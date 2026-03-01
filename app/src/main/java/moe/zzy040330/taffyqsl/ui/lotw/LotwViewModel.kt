package moe.zzy040330.taffyqsl.ui.lotw

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.zzy040330.taffyqsl.data.config.Band
import moe.zzy040330.taffyqsl.data.config.ConfigRepository
import moe.zzy040330.taffyqsl.data.config.Mode
import moe.zzy040330.taffyqsl.data.lotw.LotwCredentialManager
import moe.zzy040330.taffyqsl.data.lotw.LotwException
import moe.zzy040330.taffyqsl.data.lotw.LotwQueryParams
import moe.zzy040330.taffyqsl.data.lotw.LotwService
import java.time.LocalDate

class LotwViewModel(application: Application) : AndroidViewModel(application) {

    private val credentialManager = LotwCredentialManager(application)
    private val service = LotwService()
    private val config = ConfigRepository.getInstance(application)

    private val _hasCredentials = MutableStateFlow(credentialManager.hasCredentials())
    val hasCredentials: StateFlow<Boolean> = _hasCredentials.asStateFlow()

    val bands: List<Band> = config.bands
    val modes: List<Mode> = config.modes

    val qsoQsl = MutableStateFlow("yes")
    val band = MutableStateFlow("")
    val mode = MutableStateFlow("")
    val sinceDate = MutableStateFlow<LocalDate?>(null)
    val ownCall = MutableStateFlow("")
    val workedCall = MutableStateFlow("")
    val endDate = MutableStateFlow<LocalDate?>(null)
    val showDxccDetail = MutableStateFlow(false)

    val isLoading = MutableStateFlow(false)
    val hasQueried = MutableStateFlow(false)
    val results = MutableStateFlow<List<Map<String, String>>>(emptyList())
    val error = MutableStateFlow<Throwable?>(null)

    fun reloadHasCredentials() {
        _hasCredentials.value = credentialManager.hasCredentials()
    }

    fun query() {
        viewModelScope.launch {
            val creds = credentialManager.loadCredentials() ?: run {
                error.value = LotwException.NoCredentials()
                return@launch
            }
            isLoading.value = true
            error.value = null

            val effectiveQsoQsl = if (showDxccDetail.value) "yes" else qsoQsl.value
            val params = LotwQueryParams(
                qsoQsl = effectiveQsoQsl,
                qsoQslSince = if (effectiveQsoQsl == "yes") sinceDate.value?.toString() else null,
                qsoQsoRxSince = if (effectiveQsoQsl == "no") sinceDate.value?.toString() else null,
                qsoOwnCall = ownCall.value.takeIf { it.isNotBlank() },
                qsoCallsign = workedCall.value.takeIf { it.isNotBlank() },
                qsoMode = mode.value.takeIf { it.isNotBlank() },
                qsoBand = band.value.takeIf { it.isNotBlank() },
                qsoEndDate = endDate.value?.toString(),
                qsoQslDetail = showDxccDetail.value,
            )

            service.query(creds.first, creds.second, params)
                .onSuccess { list ->
                    results.value = list
                    hasQueried.value = true
                }
                .onFailure { e ->
                    error.value = e
                    hasQueried.value = true
                }

            isLoading.value = false
        }
    }
}
