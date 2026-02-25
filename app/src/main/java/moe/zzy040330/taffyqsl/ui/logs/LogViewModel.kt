package moe.zzy040330.taffyqsl.ui.logs

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.zzy040330.taffyqsl.data.config.ConfigRepository
import moe.zzy040330.taffyqsl.data.crypto.CertificateManager
import moe.zzy040330.taffyqsl.data.db.AppDatabase
import moe.zzy040330.taffyqsl.data.db.DuplicateQsoEntity
import moe.zzy040330.taffyqsl.data.db.QsoFileEntity
import moe.zzy040330.taffyqsl.data.db.QsoRecordEntity
import moe.zzy040330.taffyqsl.data.parser.AdifParser
import moe.zzy040330.taffyqsl.data.upload.LotwUploader
import moe.zzy040330.taffyqsl.data.upload.UploadResult
import moe.zzy040330.taffyqsl.domain.SigningPipeline
import moe.zzy040330.taffyqsl.domain.StationRepository
import moe.zzy040330.taffyqsl.domain.model.CertInfo
import moe.zzy040330.taffyqsl.domain.model.SigningProgress
import moe.zzy040330.taffyqsl.domain.model.StationLocation
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

sealed class UploadState {
    object Idle : UploadState()
    object Uploading : UploadState()
    data class Done(val response: String) : UploadState()
    data class Error(val message: String) : UploadState()
}

sealed class ImportResult {
    data class Done(val imported: Int, val skipped: Int) : ImportResult()
    data class Error(val message: String) : ImportResult()
}

class LogViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)
    private val signingPipeline = SigningPipeline(app)
    private val stationRepo = StationRepository(app)
    private val certManager = CertificateManager(app)
    private val uploader = LotwUploader()
    private val configRepo = ConfigRepository.getInstance(app)

    // Internal ADIF files managed / created by the app.
    private val _qsoFiles = MutableStateFlow<List<QsoFileEntity>>(emptyList())
    val qsoFiles: StateFlow<List<QsoFileEntity>> = _qsoFiles.asStateFlow()

    // Station locations available for signing.
    private val _stations = MutableStateFlow<List<StationLocation>>(emptyList())
    val stations: StateFlow<List<StationLocation>> = _stations.asStateFlow()

    // Certificates for callsign lookup.
    private val _certs = MutableStateFlow<List<CertInfo>>(emptyList())
    val certs: StateFlow<List<CertInfo>> = _certs.asStateFlow()

    // Signing progress / result.
    private val _signingProgress = MutableStateFlow<SigningProgress?>(null)
    val signingProgress: StateFlow<SigningProgress?> = _signingProgress.asStateFlow()

    // Upload state for the post-signing upload.
    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    // ADIF import result
    private val _importState = MutableStateFlow<ImportResult?>(null)
    val importState: StateFlow<ImportResult?> = _importState.asStateFlow()

    init {
        loadQsoFiles()
        refreshAll()
    }

    private fun loadQsoFiles() {
        viewModelScope.launch {
            db.qsoFileDao().getAllLive().observeForever { files ->
                _qsoFiles.value = files
            }
        }
    }

    /** Reload stations & certificates (called when screen becomes visible). */
    fun refreshAll() {
        viewModelScope.launch(Dispatchers.IO) {
            _stations.value = stationRepo.getAll()
            _certs.value = certManager.listCerts()
        }
    }

    /** Find the first certificate whose callsign matches the given value (case-insensitive). */
    fun certForCallsign(callSign: String): CertInfo? =
        _certs.value.firstOrNull { it.callSign.equals(callSign, ignoreCase = true) }

    /**
     * Sign an external ADIF file.
     * The output .tq8 file is written to the app's cache directory.
     * Duplicate-tracking DB records are NOT committed — call [commitSignedQsos] when the user
     * saves or uploads the result.
     * Upload is also a separate action via [uploadTq8File].
     */
    fun signAdifFile(
        uri: Uri,
        station: StationLocation,
        certAlias: String,
        dateFrom: LocalDate?,
        dateTo: LocalDate?
    ) {
        viewModelScope.launch {
            val outputFile = File(
                getApplication<Application>().cacheDir,
                "taffyqsl_signed_${System.currentTimeMillis()}.tq8"
            )
            _signingProgress.value = SigningProgress.Processing(0, 0)
            _uploadState.value = UploadState.Idle
            try {
                val options = SigningPipeline.SigningOptions(dateFrom, dateTo)
                signingPipeline.signAdifFile(
                    adifUri = uri,
                    station = station,
                    certAlias = certAlias,
                    options = options,
                    outputFile = outputFile,
                    progressCallback = { progress ->
                        _signingProgress.value = progress
                    }
                )
            } catch (e: Exception) {
                _signingProgress.value = SigningProgress.Failed(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Commit signed QSO records to the duplicate-tracking DB.
     * Call this when the user saves or uploads the signing result.
     * *Safe to call multiple times (DB uses IGNORE strategy).
     */
    fun commitSignedQsos(entities: List<DuplicateQsoEntity>) {
        if (entities.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            db.duplicateQsoDao().insertAll(entities)
        }
    }

    /** Upload a signed .tq8 file to LoTW.
     * Updates [uploadState] with progress and result.
     */
    fun uploadTq8File(file: File) {
        viewModelScope.launch {
            _uploadState.value = UploadState.Uploading
            val tq8Data = withContext(Dispatchers.IO) { file.readBytes() }
            _uploadState.value = when (val result = uploader.upload(tq8Data)) {
                is UploadResult.Success -> UploadState.Done(result.response)
                is UploadResult.Error -> UploadState.Error(result.message)
            }
        }
    }

    fun clearSigningProgress() {
        _signingProgress.value = null
        _uploadState.value = UploadState.Idle
    }

    /** Create a new empty internal ADIF log file. */
    fun createQsoFile(displayName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val fileName = "adif_${System.currentTimeMillis()}.adi"
            File(getApplication<Application>().filesDir, fileName).writeText("")
            db.qsoFileDao().insert(QsoFileEntity(fileName, displayName))
        }
    }

    /** Delete an internal ADIF log and remove its file from storage. */
    fun deleteQsoFile(fileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.qsoFileDao().deleteByName(fileName)
            File(getApplication<Application>().filesDir, fileName).delete()
        }
    }

    /** Rename an internal ADIF log's display name. */
    fun renameQsoFile(fileName: String, newDisplayName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.qsoFileDao().updateDisplayName(fileName, newDisplayName)
        }
    }

    /**
     * Import an ADIF file from a URI. Valid records are stored in the DB.
     * A record is valid if it has CALL, QSO_DATE, MODE, and either BAND or FREQ.
     * Updates [importState] with the result.
     */
    fun importAdif(uri: Uri, displayName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val qsoMaps = getApplication<Application>().contentResolver
                    .openInputStream(uri)?.use { stream ->
                        AdifParser(stream).readAllQsos()
                    } ?: run {
                    _importState.value = ImportResult.Error("Cannot open file")
                    return@launch
                }

                val fileName = "adif_${System.currentTimeMillis()}.adi"
                val validRecords = mutableListOf<QsoRecordEntity>()
                var skipped = 0

                for (fields in qsoMaps) {
                    val callsign = fields["CALL"]?.trim()?.uppercase()
                    val dateStr = fields["QSO_DATE"]?.trim()
                    val adifMode = fields["MODE"]?.trim()?.uppercase()
                    val adifSubmode = fields["SUBMODE"]?.trim()?.uppercase() ?: ""
                    val adifBand = fields["BAND"]?.trim()?.uppercase() ?: ""
                    val freq = fields["FREQ"]?.trim() ?: ""

                    if (callsign.isNullOrBlank() || dateStr.isNullOrBlank() ||
                        adifMode.isNullOrBlank() || (adifBand.isEmpty() && freq.isEmpty())
                    ) {
                        skipped++
                        continue
                    }

                    // Validate date format
                    if (runCatching {
                            LocalDate.parse(dateStr, DateTimeFormatter.BASIC_ISO_DATE)
                        }.isFailure) {
                        skipped++
                        continue
                    }

                    val resolvedBand = if (adifBand.isNotEmpty()) {
                        adifBand
                    } else {
                        configRepo.bandForFreq(freq.toDoubleOrNull() ?: 0.0) ?: run {
                            skipped++
                            continue
                        }
                    }

                    val rawTime = fields["TIME_ON"]?.trim() ?: "0000"
                    val digits = rawTime.filter { it.isDigit() }.padEnd(6, '0').take(6)

                    validRecords.add(
                        QsoRecordEntity(
                            fileId = fileName,
                            callsign = callsign,
                            bandName = resolvedBand,
                            modeName = configRepo.resolveTqslMode(adifMode, adifSubmode),
                            submode = adifSubmode,
                            dateStr = dateStr,
                            timeStr = digits,
                            freq = freq,
                            rxFreq = fields["FREQ_RX"]?.trim() ?: "",
                            rxBandName = fields["BAND_RX"]?.trim()?.uppercase() ?: "",
                            propMode = fields["PROP_MODE"]?.trim()?.uppercase() ?: "",
                            satName = fields["SAT_NAME"]?.trim()?.uppercase() ?: ""
                        )
                    )
                }

                db.qsoFileDao().insert(
                    QsoFileEntity(
                        fileName = fileName,
                        displayName = displayName,
                        qsoCount = validRecords.size
                    )
                )
                if (validRecords.isNotEmpty()) {
                    db.qsoRecordDao().insertAll(validRecords)
                }

                _importState.value = ImportResult.Done(validRecords.size, skipped)
            } catch (e: Exception) {
                _importState.value = ImportResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun clearImportState() {
        _importState.value = null
    }
}
