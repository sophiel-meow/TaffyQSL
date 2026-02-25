package moe.zzy040330.taffyqsl.ui.certificates

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.zzy040330.taffyqsl.data.config.ConfigRepository
import moe.zzy040330.taffyqsl.data.crypto.CertificateManager
import moe.zzy040330.taffyqsl.domain.model.CertInfo

class CertViewModel(app: Application) : AndroidViewModel(app) {

    private val certManager = CertificateManager(app)
    private val configRepo = ConfigRepository.getInstance(app)

    private val _certs = MutableStateFlow<List<CertInfo>>(emptyList())
    val certs: StateFlow<List<CertInfo>> = _certs.asStateFlow()

    private val _importResult = MutableStateFlow<Result<CertInfo>?>(null)
    val importResult: StateFlow<Result<CertInfo>?> = _importResult.asStateFlow()

    private val _exportResult = MutableStateFlow<Result<ByteArray>?>(null)
    val exportResult: StateFlow<Result<ByteArray>?> = _exportResult.asStateFlow()

    init {
        loadCerts()
    }

    fun loadCerts() {
        viewModelScope.launch(Dispatchers.IO) {
            val certs = certManager.listCerts().map { cert ->
                val dxccName = configRepo.dxccById(cert.dxccEntity)?.name ?: ""
                cert.copy(dxccEntityName = dxccName)
            }
            _certs.value = certs
        }
    }

    fun importP12(uri: Uri, password: String) {
        viewModelScope.launch {
            val result = certManager.importP12(uri, password)
            _importResult.value = result
            if (result.isSuccess) loadCerts()
        }
    }

    fun deleteCert(alias: String) {
        viewModelScope.launch(Dispatchers.IO) {
            certManager.deleteCert(alias)
            loadCerts()
        }
    }

    fun exportP12(alias: String, password: String) {
        viewModelScope.launch {
            val result = runCatching { certManager.exportP12(alias, password) }
            _exportResult.value = result
        }
    }

    fun clearImportResult() {
        _importResult.value = null
    }

    fun clearExportResult() {
        _exportResult.value = null
    }
}
