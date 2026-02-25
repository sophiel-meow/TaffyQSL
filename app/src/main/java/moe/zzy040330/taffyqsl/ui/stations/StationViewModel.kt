package moe.zzy040330.taffyqsl.ui.stations

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.zzy040330.taffyqsl.data.config.ConfigRepository
import moe.zzy040330.taffyqsl.data.config.DxccEntity
import moe.zzy040330.taffyqsl.data.config.StateValue
import moe.zzy040330.taffyqsl.data.config.SubdivisionField
import moe.zzy040330.taffyqsl.data.crypto.CertificateManager
import moe.zzy040330.taffyqsl.domain.StationRepository
import moe.zzy040330.taffyqsl.domain.model.CertInfo
import moe.zzy040330.taffyqsl.domain.model.StationLocation

class StationViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = StationRepository(app)
    private val certManager = CertificateManager(app)
    private val configRepo = ConfigRepository.getInstance(app)

    private val _stations = MutableStateFlow<List<StationLocation>>(emptyList())
    val stations: StateFlow<List<StationLocation>> = _stations.asStateFlow()

    private val _certs = MutableStateFlow<List<CertInfo>>(emptyList())
    val certs: StateFlow<List<CertInfo>> = _certs.asStateFlow()

    init {
        loadStations()
        loadCerts()
    }

    private fun loadStations() {
        viewModelScope.launch {
            repo.allStations.observeForever { stationList ->
                _stations.value = stationList
            }
        }
    }

    private fun loadCerts() {
        viewModelScope.launch(Dispatchers.IO) {
            val certList = certManager.listCerts().map { cert ->
                val dxccName = configRepo.dxccById(cert.dxccEntity)?.name ?: ""
                cert.copy(dxccEntityName = dxccName)
            }
            _certs.value = certList
        }
    }

    fun refreshCerts() {
        loadCerts()
    }

    fun saveStation(station: StationLocation) {
        viewModelScope.launch {
            repo.save(station)
        }
    }

    fun deleteStation(id: Long) {
        viewModelScope.launch {
            repo.getById(id)?.let { station ->
                repo.delete(station)
            }
        }
    }

    suspend fun loadStateValues(fieldId: String, dxccId: Int = 0): List<StateValue> {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            configRepo.loadStateValues(fieldId, dxccId)
        }
    }

    suspend fun loadSubValues(fieldId: String, parentValue: String): List<StateValue> {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            configRepo.loadSubValues(fieldId, parentValue)
        }
    }

    fun secondaryFields(primaryFieldId: String): List<SubdivisionField> =
        configRepo.secondaryFields(primaryFieldId)

    fun dxccEntityName(dxccId: Int): String = configRepo.dxccById(dxccId)?.name ?: ""
    fun stateFieldId(dxccId: Int): String = configRepo.dxccById(dxccId)?.stateFieldId ?: ""

    // DEBUG: Exposed for debug DXCC override
    val dxccEntities: List<DxccEntity> get() = configRepo.dxccEntities
}
