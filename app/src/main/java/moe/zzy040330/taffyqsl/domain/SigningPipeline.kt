package moe.zzy040330.taffyqsl.domain

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.zzy040330.taffyqsl.data.config.ConfigRepository
import moe.zzy040330.taffyqsl.data.crypto.CertificateManager
import moe.zzy040330.taffyqsl.data.db.AppDatabase
import moe.zzy040330.taffyqsl.data.db.DuplicateQsoEntity
import moe.zzy040330.taffyqsl.data.parser.AdifParser
import moe.zzy040330.taffyqsl.data.parser.GabbiWriter
import moe.zzy040330.taffyqsl.data.parser.SignedQso
import moe.zzy040330.taffyqsl.domain.model.QsoRecord
import moe.zzy040330.taffyqsl.domain.model.SigningProgress
import moe.zzy040330.taffyqsl.domain.model.SigningResult
import moe.zzy040330.taffyqsl.domain.model.StationLocation
import java.io.File
import java.security.Signature
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class SigningPipeline(private val context: Context) {

    private val certManager = CertificateManager(context)
    private val configRepo = ConfigRepository.getInstance(context)
    private val db = AppDatabase.getInstance(context)
    private val gabbiWriter = GabbiWriter()

    data class SigningOptions(
        val dateFrom: LocalDate? = null,
        val dateTo: LocalDate? = null,
        val skipDuplicates: Boolean = true
    )

    /**
     * Sign an ADIF file from a Uri.
     * Does NOT commit duplicate-tracking records to the DB — the caller must invoke
     * [moe.zzy040330.taffyqsl.ui.logs.LogViewModel.commitSignedQsos] when the user saves or uploads.
     * Does NOT upload or save — upload is triggered separately from the UI.
     */
    suspend fun signAdifFile(
        adifUri: Uri,
        station: StationLocation,
        certAlias: String,
        options: SigningOptions,
        outputFile: File,
        progressCallback: suspend (SigningProgress) -> Unit
    ): SigningResult = withContext(Dispatchers.IO) {

        val qsoMaps = context.contentResolver.openInputStream(adifUri)?.use { stream ->
            AdifParser(stream).readAllQsos()
        } ?: error("Cannot open ADIF file")

        val total = qsoMaps.size

        val privateKey = certManager.getPrivateKey(certAlias)
            ?: error("Private key not found. Please re-import the certificate.")
        val certPemData = certManager.getCertPemData(certAlias)
            ?: error("Certificate not found.")

        val stationSigndata = gabbiWriter.buildStationSigndata(station)
        val signer = Signature.getInstance("SHA1withRSA")
        signer.initSign(privateKey)

        val signed = mutableListOf<SignedQso>()
        val dupeKeys = mutableListOf<String>()
        var dupCount = 0
        var dateFilterCount = 0
        var invalidCount = 0
        var gridMismatchCount = 0

        for ((index, rawFields) in qsoMaps.withIndex()) {
            progressCallback(SigningProgress.Processing(index, total))

            // If present in ADIF and station has a grid, they must match
            val myGrid = rawFields["MY_GRIDSQUARE"]?.trim()?.uppercase()
            if (!myGrid.isNullOrEmpty() && station.grid.isNotEmpty()) {
                val stationGrid = station.grid.uppercase()
                // compare prefix
                val compareLen = minOf(4, myGrid.length, stationGrid.length)
                if (myGrid.take(compareLen) != stationGrid.take(compareLen)) {
                    gridMismatchCount++
                    continue
                }
            }

            val qso = parseQsoMap(rawFields) ?: run { invalidCount++; continue }

            // Date filter
            if (options.dateFrom != null && qso.date.isBefore(options.dateFrom)) {
                dateFilterCount++
                continue
            }
            if (options.dateTo != null && qso.date.isAfter(options.dateTo)) {
                dateFilterCount++
                continue
            }

            // Duplicate check
            val dupeKey = buildDupeKey(qso, certAlias, station.name)
            if (options.skipDuplicates && db.duplicateQsoDao().exists(dupeKey) > 0) {
                dupCount++
                continue
            }

            // Build signdata and sign
            val signdata = gabbiWriter.buildContactSigndata(stationSigndata, qso)
            signer.update(signdata.toByteArray(Charsets.US_ASCII))
            val sigBytes = signer.sign()
            signer.initSign(privateKey)

            val sigB64 = Base64.encodeToString(sigBytes, Base64.NO_WRAP)
            signed.add(SignedQso(qso, signdata, sigB64))
            dupeKeys.add(dupeKey)
        }

        // Write GAbbi
        val gabbi = gabbiWriter.buildGabbi(certAlias, certPemData, station, signed)
        val tq8Data = gabbiWriter.compressToTq8(gabbi)
        outputFile.writeBytes(tq8Data)

        // Build pending duplicate entities — NOT committed yet
        val pendingEntities = dupeKeys.zip(signed).map { (key, sq) ->
            DuplicateQsoEntity(
                key = key,
                callsign = sq.qso.callsign,
                band = sq.qso.band,
                mode = sq.qso.mode,
                date = sq.qso.date.format(DateTimeFormatter.BASIC_ISO_DATE),
                time = sq.qso.time.format(DateTimeFormatter.ofPattern("HHmmss")),
                certAlias = certAlias,
                stationName = station.name
            )
        }

        val result = SigningResult(
            totalQsos = total,
            signedQsos = signed.size,
            duplicateQsos = dupCount,
            dateFilteredQsos = dateFilterCount,
            invalidQsos = invalidCount,
            gridMismatchQsos = gridMismatchCount,
            outputFile = outputFile,
            pendingDupeEntities = pendingEntities
        )
        progressCallback(SigningProgress.Completed(result))
        result
    }

    /**
     * Sign an in-memory list of QsoRecords (from the built-in ADIF editor).
     * Same deferred-commit semantics as [signAdifFile].
     */
    suspend fun signQsoList(
        qsos: List<QsoRecord>,
        station: StationLocation,
        certAlias: String,
        options: SigningOptions,
        outputFile: File,
        progressCallback: suspend (SigningProgress) -> Unit
    ): SigningResult = withContext(Dispatchers.IO) {

        val privateKey = certManager.getPrivateKey(certAlias)
            ?: error("Private key not found.")
        val certPemData = certManager.getCertPemData(certAlias)
            ?: error("Certificate not found.")

        val stationSigndata = gabbiWriter.buildStationSigndata(station)
        val signer = Signature.getInstance("SHA1withRSA")
        signer.initSign(privateKey)

        val signed = mutableListOf<SignedQso>()
        val dupeKeys = mutableListOf<String>()
        var dupCount = 0
        var dateFilterCount = 0
        var invalidCount = 0
        val total = qsos.size

        for ((index, qso) in qsos.withIndex()) {
            progressCallback(SigningProgress.Processing(index, total))

            if (options.dateFrom != null && qso.date.isBefore(options.dateFrom)) {
                dateFilterCount++
                continue
            }
            if (options.dateTo != null && qso.date.isAfter(options.dateTo)) {
                dateFilterCount++
                continue
            }
            if (qso.callsign.isBlank() || qso.band.isBlank() || qso.mode.isBlank()) {
                invalidCount++
                continue
            }

            val dupeKey = buildDupeKey(qso, certAlias, station.name)
            if (options.skipDuplicates && db.duplicateQsoDao().exists(dupeKey) > 0) {
                dupCount++
                continue
            }

            val signdata = gabbiWriter.buildContactSigndata(stationSigndata, qso)
            signer.update(signdata.toByteArray(Charsets.US_ASCII))
            val sigBytes = signer.sign()
            signer.initSign(privateKey)

            val sigB64 = Base64.encodeToString(sigBytes, Base64.NO_WRAP)
            signed.add(SignedQso(qso, signdata, sigB64))
            dupeKeys.add(dupeKey)
        }

        val gabbi = gabbiWriter.buildGabbi(certAlias, certPemData, station, signed)
        val tq8Data = gabbiWriter.compressToTq8(gabbi)
        outputFile.writeBytes(tq8Data)

        val pendingEntities = dupeKeys.zip(signed).map { (key, sq) ->
            DuplicateQsoEntity(
                key = key,
                callsign = sq.qso.callsign,
                band = sq.qso.band,
                mode = sq.qso.mode,
                date = sq.qso.date.format(DateTimeFormatter.BASIC_ISO_DATE),
                time = sq.qso.time.format(DateTimeFormatter.ofPattern("HHmmss")),
                certAlias = certAlias,
                stationName = station.name
            )
        }

        val result = SigningResult(
            totalQsos = total,
            signedQsos = signed.size,
            duplicateQsos = dupCount,
            dateFilteredQsos = dateFilterCount,
            invalidQsos = invalidCount,
            outputFile = outputFile,
            pendingDupeEntities = pendingEntities
        )
        progressCallback(SigningProgress.Completed(result))
        result
    }

    private fun buildDupeKey(qso: QsoRecord, certAlias: String, stationName: String): String {
        val date = qso.date.format(DateTimeFormatter.BASIC_ISO_DATE)
        val time = qso.time.format(DateTimeFormatter.ofPattern("HHmmss"))
        return "${qso.callsign.uppercase()}|${qso.band.uppercase()}|${qso.mode.uppercase()}|$date|$time|$certAlias|$stationName"
    }

    private fun parseQsoMap(fields: Map<String, String>): QsoRecord? {
        val callsign = fields["CALL"]?.trim()?.uppercase() ?: return null
        val dateStr = fields["QSO_DATE"]?.trim() ?: return null
        val timeStr = fields["TIME_ON"]?.trim() ?: ""
        val adifMode = fields["MODE"]?.trim()?.uppercase() ?: return null
        val adifSubmode = fields["SUBMODE"]?.trim()?.uppercase() ?: ""

        val date =
            runCatching {
                LocalDate.parse(dateStr, DateTimeFormatter.BASIC_ISO_DATE)
            }.getOrNull()
                ?: return null
        val time = parseTime(timeStr)

        val tqslMode = configRepo.resolveTqslMode(adifMode, adifSubmode)

        val adifBand = fields["BAND"]?.trim()?.uppercase() ?: ""
        val freq = fields["FREQ"]?.trim() ?: ""
        val band = when {
            adifBand.isNotEmpty() -> adifBand
            freq.isNotEmpty() -> configRepo.bandForFreq(freq.toDoubleOrNull() ?: 0.0)
                ?: return null

            else -> return null
        }

        return QsoRecord(
            callsign = callsign,
            band = band,
            mode = tqslMode,
            submode = adifSubmode,
            date = date,
            time = time,
            freq = freq,
            rxFreq = fields["FREQ_RX"]?.trim() ?: "",
            rxBand = fields["BAND_RX"]?.trim()?.uppercase() ?: "",
            propMode = fields["PROP_MODE"]?.trim()?.uppercase() ?: "",
            satName = fields["SAT_NAME"]?.trim()?.uppercase() ?: ""
        )
    }

    private fun parseTime(timeStr: String): LocalTime {
        val digits = timeStr.filter { it.isDigit() }.padEnd(6, '0').take(6)
        return runCatching {
            LocalTime.of(
                digits.substring(0, 2).toInt(),
                digits.substring(2, 4).toInt(),
                digits.substring(4, 6).toInt()
            )
        }.getOrDefault(LocalTime.MIDNIGHT)
    }
}
