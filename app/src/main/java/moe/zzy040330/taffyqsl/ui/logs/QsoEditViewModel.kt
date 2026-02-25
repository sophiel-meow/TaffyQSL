package moe.zzy040330.taffyqsl.ui.logs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.zzy040330.taffyqsl.data.config.Band
import moe.zzy040330.taffyqsl.data.config.ConfigRepository
import moe.zzy040330.taffyqsl.data.config.Mode
import moe.zzy040330.taffyqsl.data.config.PropMode
import moe.zzy040330.taffyqsl.data.config.Satellite
import moe.zzy040330.taffyqsl.data.db.AppDatabase
import moe.zzy040330.taffyqsl.data.db.QsoRecordEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class QsoEditViewModel(
    app: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(app) {

    val fileName: String = checkNotNull(savedStateHandle["fileName"])
    val qsoId: Long = savedStateHandle.get<Long>("qsoId") ?: 0L
    val isNew: Boolean = qsoId == 0L

    private val db = AppDatabase.getInstance(app)
    private val configRepo = ConfigRepository.getInstance(app)

    private val _bands = MutableStateFlow<List<Band>>(emptyList())
    val bands: StateFlow<List<Band>> = _bands.asStateFlow()

    private val _modes = MutableStateFlow<List<Mode>>(emptyList())
    val modes: StateFlow<List<Mode>> = _modes.asStateFlow()

    private val _propModes = MutableStateFlow<List<PropMode>>(emptyList())
    val propModes: StateFlow<List<PropMode>> = _propModes.asStateFlow()

    private val _satellites = MutableStateFlow<List<Satellite>>(emptyList())
    val satellites: StateFlow<List<Satellite>> = _satellites.asStateFlow()

    // ******** Form fields ********

    val callsign = MutableStateFlow("")
    val date = MutableStateFlow<LocalDate?>(null)
    val time = MutableStateFlow<LocalTime?>(null)

    val selectedMode = MutableStateFlow("")
    val submode = MutableStateFlow("")
    val selectedBand = MutableStateFlow("")
    val freq = MutableStateFlow("")
    val rxBand = MutableStateFlow("")
    val rxFreq = MutableStateFlow("")
    val propMode = MutableStateFlow("")
    val satName = MutableStateFlow("")

    // If band was auto-inferred from freq, holds the inferred band name.
    private val _bandAutoInferred = MutableStateFlow<String?>(null)
    val bandAutoInferred: StateFlow<String?> = _bandAutoInferred

    // If RX band was auto-inferred from RX freq, holds the inferred band name.
    private val _rxBandAutoInferred = MutableStateFlow<String?>(null)
    val rxBandAutoInferred: StateFlow<String?> = _rxBandAutoInferred

    /**
     * True when both freq and selectedBand are non-empty but the entered frequency
     * falls outside the selected band's range.
     */
    val freqOutOfBand: StateFlow<Boolean> = combine(freq, selectedBand, _bands) { f, b, bandList ->
        if (f.isBlank() || b.isBlank()) return@combine false
        val freqMhz = f.toDoubleOrNull() ?: return@combine false
        val band = bandList.firstOrNull { it.name == b } ?: return@combine false
        freqMhz < band.freqLow || freqMhz > band.freqHigh
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * True when both rxFreq and rxBand are non-empty but the entered RX frequency
     * falls outside the selected RX band's range.
     */
    val rxFreqOutOfBand: StateFlow<Boolean> = combine(rxFreq, rxBand, _bands) { f, b, bandList ->
        if (f.isBlank() || b.isBlank()) return@combine false
        val freqMhz = f.toDoubleOrNull() ?: return@combine false
        val band = bandList.firstOrNull { it.name == b } ?: return@combine false
        freqMhz < band.freqLow || freqMhz > band.freqHigh
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** True when callsign matches the allowed format. */
    val callsignValid: StateFlow<Boolean> = callsign
        .map { cs -> cs.isBlank() || CALLSIGN_REGEX.matches(cs) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** True when all required fields are valid. */
    val isValid: StateFlow<Boolean> = combine(
        callsign, date, time, selectedBand, freq
    ) { cs, d, t, b, f ->
        cs.isNotBlank() && d != null && t != null && (b.isNotBlank() || f.isNotBlank()) && f.isNotBlank()
    }.combine(callsignValid) { fieldsOk, csOk -> fieldsOk && csOk }
        .combine(selectedMode) { prev, m -> prev && m.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        // Pre-load config on IO so dropdown lists are ready before the user taps them
        viewModelScope.launch(Dispatchers.IO) {
            _bands.value = configRepo.bands
            _modes.value = configRepo.modes
            _propModes.value = configRepo.propModes
            _satellites.value = configRepo.satellites
        }

        if (!isNew) {
            viewModelScope.launch(Dispatchers.IO) {
                db.qsoRecordDao().getById(qsoId)?.let { entity ->
                    withContext(Dispatchers.Main) {
                        callsign.value = entity.callsign
                        selectedBand.value = entity.bandName
                        selectedMode.value = entity.modeName
                        submode.value = entity.submode
                        freq.value = entity.freq
                        rxFreq.value = entity.rxFreq
                        rxBand.value = entity.rxBandName
                        propMode.value = entity.propMode
                        satName.value = entity.satName
                        date.value = runCatching {
                            LocalDate.parse(
                                entity.dateStr,
                                DateTimeFormatter.BASIC_ISO_DATE
                            )
                        }.getOrNull()
                        time.value = parseTimeInput(entity.timeStr)
                    }
                }
            }
        }
    }

    /** Called when the user changes the frequency field.
     * Auto-infers TX band if not manually set.*/
    fun onFreqChanged(newFreq: String) {
        freq.value = newFreq
        if (newFreq.isBlank()) {
            // Clear auto-inferred band if frequency is cleared
            if (_bandAutoInferred.value != null) {
                selectedBand.value = ""
                _bandAutoInferred.value = null
            }
            return
        }
        val freqMhz = newFreq.toDoubleOrNull() ?: return
        val inferred = configRepo.bandForFreq(freqMhz)

        // Only auto-update if band was previously auto-inferred or empty
        if (_bandAutoInferred.value != null || selectedBand.value.isEmpty()) {
            if (inferred != null) {
                selectedBand.value = inferred
                _bandAutoInferred.value = inferred
            } else {
                // Frequency doesn't match any band
                if (_bandAutoInferred.value != null) {
                    selectedBand.value = ""
                }
                _bandAutoInferred.value = null
            }
        }
    }

    /** Called when the user manually selects a band — clears auto-inferred state. */
    fun onBandSelected(band: String) {
        selectedBand.value = band
        _bandAutoInferred.value = null
    }

    /** Called when the user changes the RX frequency field.
     * Auto-infers RX band if not manually set. */
    fun onRxFreqChanged(newFreq: String) {
        rxFreq.value = newFreq
        if (newFreq.isBlank()) {
            // Clear auto-inferred band if frequency is cleared
            if (_rxBandAutoInferred.value != null) {
                rxBand.value = ""
                _rxBandAutoInferred.value = null
            }
            return
        }
        val freqMhz = newFreq.toDoubleOrNull() ?: return
        val inferred = configRepo.bandForFreq(freqMhz)

        // Only auto-update if band was previously auto-inferred or empty
        if (_rxBandAutoInferred.value != null || rxBand.value.isEmpty()) {
            if (inferred != null) {
                rxBand.value = inferred
                _rxBandAutoInferred.value = inferred
            } else {
                // Frequency doesn't match any band
                if (_rxBandAutoInferred.value != null) {
                    rxBand.value = ""
                }
                _rxBandAutoInferred.value = null
            }
        }
    }

    /** Called when the user manually selects an RX band, clears auto-inferred state. */
    fun onRxBandSelected(band: String) {
        rxBand.value = band
        _rxBandAutoInferred.value = null
    }

    /** Save the QSO record (insert or update), then invoke [onSuccess] on the main thread. */
    fun save(onSuccess: () -> Unit) {
        val d = date.value ?: return
        val t = time.value ?: return
        val band = selectedBand.value.ifBlank {
            configRepo.bandForFreq(freq.value.toDoubleOrNull() ?: 0.0) ?: return
        }

        val entity = QsoRecordEntity(
            id = if (isNew) 0L else qsoId,
            fileId = fileName,
            callsign = callsign.value.uppercase().trim(),
            bandName = band,
            modeName = selectedMode.value,
            submode = submode.value,
            dateStr = d.format(DateTimeFormatter.BASIC_ISO_DATE),
            timeStr = t.format(DateTimeFormatter.ofPattern("HHmmss")),
            freq = freq.value,
            rxFreq = rxFreq.value,
            rxBandName = rxBand.value,
            propMode = propMode.value,
            satName = satName.value
        )

        viewModelScope.launch(Dispatchers.IO) {
            if (isNew) {
                db.qsoRecordDao().insert(entity)
            } else {
                db.qsoRecordDao().update(entity)
            }
            val count = db.qsoRecordDao().countByFile(fileName)
            db.qsoFileDao().updateQsoCount(fileName, count)
            withContext(Dispatchers.Main) { onSuccess() }
        }
    }

    /** Reset form fields for batch entry (only for new QSO mode). */
    fun resetForm() {
        if (!isNew) return
        callsign.value = ""
        date.value = LocalDate.now()
        time.value = LocalTime.now()
        // Keep mode, band, freq for convenience
        submode.value = ""
        rxBand.value = ""
        rxFreq.value = ""
        propMode.value = ""
        satName.value = ""
        _bandAutoInferred.value = null
        _rxBandAutoInferred.value = null
    }

    companion object {
        // FIXME: a robust callsign regex
        val CALLSIGN_REGEX =
            Regex("""^([A-Z0-9]{1,4}/)?[A-Z0-9]{1,3}[0-9][A-Z]{1,3}(/[A-Z0-9]+)?$""")

        fun parseTimeInput(input: String): LocalTime? {
            val digits = input.filter { it.isDigit() }
            return when (digits.length) {
                4 -> runCatching {
                    LocalTime.of(
                        digits.substring(0, 2).toInt(),
                        digits.substring(2, 4).toInt(),
                        0
                    )
                }.getOrNull()

                in 5..6 -> {
                    val padded = digits.padEnd(6, '0').take(6)
                    runCatching {
                        LocalTime.of(
                            padded.substring(0, 2).toInt(),
                            padded.substring(2, 4).toInt(),
                            padded.substring(4, 6).toInt()
                        )
                    }.getOrNull()
                }

                else -> null
            }
        }
    }
}
