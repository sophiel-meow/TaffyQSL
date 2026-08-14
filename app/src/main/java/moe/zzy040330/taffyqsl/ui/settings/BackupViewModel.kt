package moe.zzy040330.taffyqsl.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.zzy040330.taffyqsl.data.backup.TbkBackup
import moe.zzy040330.taffyqsl.data.crypto.CertificateManager
import moe.zzy040330.taffyqsl.data.db.AppDatabase
import moe.zzy040330.taffyqsl.domain.StationRepository
import moe.zzy040330.taffyqsl.domain.model.CertInfo
import moe.zzy040330.taffyqsl.domain.model.StationLocation

/**
 * Handles import/export of TrustedQSL-compatible .tbk backup files.
 * A .tbk contains certificates (with private keys), station locations and
 * duplicate-QSO tracking records.
 */
class BackupViewModel(app: Application) : AndroidViewModel(app) {

    private val app = app.applicationContext
    private val certManager = CertificateManager(app)
    private val stationRepo = StationRepository(app)
    private val db = AppDatabase.getInstance(app)

    data class ImportSummary(
        val certsImported: Int,
        val certsSkipped: Int,
        val stationsImported: Int,
        val dupesImported: Int
    )

    private val _exportResult = MutableStateFlow<Result<Int>?>(null)
    val exportResult: StateFlow<Result<Int>?> = _exportResult.asStateFlow()

    private val _importResult = MutableStateFlow<Result<ImportSummary>?>(null)
    val importResult: StateFlow<Result<ImportSummary>?> = _importResult.asStateFlow()

    fun exportTbk(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val content = buildContent()
                val bytes = TbkBackup.serialize(content)
                app.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: throw Exception("Cannot open file")
                content.userCerts.size + content.locations.size + content.dupes.size
            }
            _exportResult.value = result
        }
    }

    fun importTbk(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw Exception("Cannot open file")
                val content = TbkBackup.parse(bytes)

                var certsImported = 0
                var certsSkipped = 0
                for (cert in content.userCerts) {
                    if (cert.signedCertPem.isNotEmpty()) {
                        val keyRecord = TbkBackup.decodeKeyRecord(cert.privateKeyAdifB64)
                        val res = certManager.importCertKeyPair(
                            cert.signedCertPem,
                            keyRecord.privateKeyPem
                        )
                        if (res.isSuccess) certsImported++ else certsSkipped++
                    } else {
                        certsSkipped++
                    }
                }

                var stationsImported = 0
                for (loc in content.locations) {
                    val station = TbkBackup.toStation(loc)
                    if (station.name.isNotBlank() && station.callSign.isNotBlank()) {
                        if (!stationRepo.nameExists(station.name)) {
                            stationRepo.save(station)
                            stationsImported++
                        }
                    }
                }

                val dupes = content.dupes.map { TbkBackup.toDupeEntity(it) }
                if (dupes.isNotEmpty()) db.duplicateQsoDao().insertAll(dupes)

                ImportSummary(certsImported, certsSkipped, stationsImported, dupes.size)
            }
            _importResult.value = result
        }
    }

    fun clearExportResult() {
        _exportResult.value = null
    }

    fun clearImportResult() {
        _importResult.value = null
    }

    private suspend fun buildContent(): TbkBackup.TbkContent {
        val certs = certManager.listCerts().mapNotNull { alias ->
            buildTbkCert(alias)
        }
        val stations = stationRepo.getAll().map { TbkBackup.fromStation(it) }
        val dupes = db.duplicateQsoDao().getAll().map { TbkBackup.fromDupe(it) }
        return TbkBackup.TbkContent(userCerts = certs, locations = stations, dupes = dupes)
    }

    private fun buildTbkCert(alias: CertInfo): TbkBackup.TbkUserCert? {
        val cert = certManager.getCertificate(alias.alias) ?: return null
        val certPem = certManager.getCertPem(alias.alias) ?: return null
        val privateKeyPem = certManager.getPrivateKeyPem(alias.alias)
        val publicKeyPem = certManager.getPublicKeyPem(alias.alias)

        val keyRecord =
            if (privateKeyPem != null && publicKeyPem != null) {
                TbkBackup.encodeKeyRecord(alias.callSign, privateKeyPem, publicKeyPem, alias.dxccEntity)
            } else {
                ""
            }

        return TbkBackup.TbkUserCert(
            callSign = alias.callSign,
            dxcc = alias.dxccEntity,
            serial = runCatching { cert.serialNumber.toLong() }.getOrDefault(0L),
            signedCertPem = certPem,
            privateKeyAdifB64 = keyRecord
        )
    }
}
