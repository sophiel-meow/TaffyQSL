package moe.zzy040330.taffyqsl.ui.logs

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.zzy040330.taffyqsl.data.crypto.CertificateManager
import moe.zzy040330.taffyqsl.data.db.AppDatabase
import moe.zzy040330.taffyqsl.data.db.DuplicateQsoEntity
import moe.zzy040330.taffyqsl.data.db.QsoFileEntity
import moe.zzy040330.taffyqsl.data.db.QsoRecordEntity
import moe.zzy040330.taffyqsl.data.db.toQsoRecord
import moe.zzy040330.taffyqsl.data.parser.AdifWriter
import moe.zzy040330.taffyqsl.data.upload.LotwUploader
import moe.zzy040330.taffyqsl.data.upload.UploadResult
import moe.zzy040330.taffyqsl.domain.SigningPipeline
import moe.zzy040330.taffyqsl.domain.StationRepository
import moe.zzy040330.taffyqsl.domain.model.CertInfo
import moe.zzy040330.taffyqsl.domain.model.SigningProgress
import moe.zzy040330.taffyqsl.domain.model.StationLocation
import java.io.File
import java.time.LocalDate

class QsoListViewModel(
    app: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(app) {

    val fileName: String = checkNotNull(savedStateHandle["fileName"])

    private val db = AppDatabase.getInstance(app)
    private val signingPipeline = SigningPipeline(app)
    private val stationRepo = StationRepository(app)
    private val certManager = CertificateManager(app)
    private val uploader = LotwUploader()

    /** File metadata */
    val fileInfo: StateFlow<QsoFileEntity?> = db.qsoFileDao()
        .getByNameFlow(fileName)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** QSO list for this file */
    val qsos: StateFlow<List<QsoRecordEntity>> = db.qsoRecordDao()
        .getByFileFlow(fileName)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _stations = MutableStateFlow<List<StationLocation>>(emptyList())
    val stations: StateFlow<List<StationLocation>> = _stations.asStateFlow()

    private val _certs = MutableStateFlow<List<CertInfo>>(emptyList())

    private val _signingProgress = MutableStateFlow<SigningProgress?>(null)
    val signingProgress: StateFlow<SigningProgress?> = _signingProgress.asStateFlow()

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _stations.value = stationRepo.getAll()
            _certs.value = certManager.listCerts()
        }
    }

    fun certForCallsign(callSign: String): CertInfo? =
        _certs.value.firstOrNull { it.callSign.equals(callSign, ignoreCase = true) }

    /** Delete a single QSO record and update the file's QSO count. */
    fun deleteQso(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.qsoRecordDao().deleteById(id)
            val count = db.qsoRecordDao().countByFile(fileName)
            db.qsoFileDao().updateQsoCount(fileName, count)
        }
    }

    /** Export all QSOs for this file to an ADIF file at the given URI. */
    fun exportAdif(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val records = db.qsoRecordDao().getByFile(fileName)
            val qsoRecords = records.map { it.toQsoRecord() }
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                AdifWriter(out).write(qsoRecords)
            }
        }
    }

    /**
     * Sign all QSO records in this file and write a .tq8 to the app's cache dir.
     * Calls [progressCallback] on the main dispatcher via SigningPipeline.
     */
    fun sign(
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
                val qsoRecords = withContext(Dispatchers.IO) {
                    db.qsoRecordDao().getByFile(fileName).map { it.toQsoRecord() }
                }
                val options = SigningPipeline.SigningOptions(dateFrom, dateTo)
                signingPipeline.signQsoList(
                    qsos = qsoRecords,
                    station = station,
                    certAlias = certAlias,
                    options = options,
                    outputFile = outputFile,
                    progressCallback = { progress -> _signingProgress.value = progress }
                )
            } catch (e: Exception) {
                _signingProgress.value = SigningProgress.Failed(e.message ?: "Unknown error")
            }
        }
    }

    fun commitSignedQsos(entities: List<DuplicateQsoEntity>) {
        if (entities.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            db.duplicateQsoDao().insertAll(entities)
        }
    }

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
}
