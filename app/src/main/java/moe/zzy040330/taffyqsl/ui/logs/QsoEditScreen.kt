package moe.zzy040330.taffyqsl.ui.logs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import moe.zzy040330.taffyqsl.R
import moe.zzy040330.taffyqsl.data.AppPreferences
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QsoEditScreen(
    navController: NavController,
    viewModel: QsoEditViewModel = viewModel()
) {
    val callsign by viewModel.callsign.collectAsState()
    val context = LocalContext.current
    val prefs = remember { AppPreferences.getInstance(context) }
    val dateFormat = prefs.dateFormat
    val useLocalTime = prefs.useLocalTime
    val date by viewModel.date.collectAsState()
    val time by viewModel.time.collectAsState()
    val selectedMode by viewModel.selectedMode.collectAsState()
    val submode by viewModel.submode.collectAsState()
    val selectedBand by viewModel.selectedBand.collectAsState()
    val freq by viewModel.freq.collectAsState()
    val rxBand by viewModel.rxBand.collectAsState()
    val rxFreq by viewModel.rxFreq.collectAsState()
    val propMode by viewModel.propMode.collectAsState()
    val satName by viewModel.satName.collectAsState()
    val bandAutoInferred by viewModel.bandAutoInferred.collectAsState()
    val rxBandAutoInferred by viewModel.rxBandAutoInferred.collectAsState()
    val freqOutOfBand by viewModel.freqOutOfBand.collectAsState()
    val rxFreqOutOfBand by viewModel.rxFreqOutOfBand.collectAsState()
    val callsignValid by viewModel.callsignValid.collectAsState()
    val isValid by viewModel.isValid.collectAsState()
    val bands by viewModel.bands.collectAsState()
    val modes by viewModel.modes.collectAsState()
    val propModes by viewModel.propModes.collectAsState()
    val satellites by viewModel.satellites.collectAsState()

    // Track whether user has attempted to save (to defer showing required-field errors)
    var submitAttempted by remember { mutableStateOf(false) }

    // Dropdown expansion states
    var modeExpanded by remember { mutableStateOf(false) }
    var bandExpanded by remember { mutableStateOf(false) }
    var rxBandExpanded by remember { mutableStateOf(false) }
    var propModeExpanded by remember { mutableStateOf(false) }
    var satExpanded by remember { mutableStateOf(false) }

    var showDatePicker by remember { mutableStateOf(false) }

    var showTimePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date?.toEpochDay()?.times(86400000L)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        viewModel.date.value = LocalDate.ofEpochDay(millis / 86400000)
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) { DatePicker(state = state) }
    }

    if (showTimePicker) {
        // When useLocalTime, show the picker in local time (convert UTC→local using QSO date)
        val effectiveDate = date ?: LocalDate.now(ZoneOffset.UTC)
        val pickerTime = if (useLocalTime && time != null) {
            time!!.atDate(effectiveDate)
                .atZone(ZoneOffset.UTC)
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalTime()
        } else time
        val state = rememberTimePickerState(
            initialHour = pickerTime?.hour ?: 0,
            initialMinute = pickerTime?.minute ?: 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val picked = LocalTime.of(state.hour, state.minute, 0)
                    // Convert local→UTC before storing; ViewModel always holds UTC
                    viewModel.time.value = if (useLocalTime) {
                        picked.atDate(effectiveDate)
                            .atZone(ZoneId.systemDefault())
                            .withZoneSameInstant(ZoneOffset.UTC)
                            .toLocalTime()
                    } else picked
                    showTimePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            text = { TimePicker(state = state) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (viewModel.isNew) stringResource(R.string.new_qso)
                        else stringResource(R.string.edit_qso)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            submitAttempted = true
                            if (isValid) {
                                viewModel.save {
                                    if (viewModel.isNew) {
                                        // For new QSO, reset form for batch entry
                                        viewModel.resetForm()
                                        submitAttempted = false
                                    } else {
                                        // For edit, go back
                                        navController.popBackStack()
                                    }
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // callsign
            val callsignError = callsign.isNotBlank() && !callsignValid
            OutlinedTextField(
                value = callsign,
                onValueChange = { viewModel.callsign.value = it.uppercase() },
                label = { Text(stringResource(R.string.callsign)) },
                isError = (submitAttempted && callsign.isBlank()) || callsignError,
                supportingText = if (callsignError) {
                    { Text(stringResource(R.string.invalid_callsign_format)) }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth()
            )

            // date & time
            Text(
                text = if (useLocalTime) {
                    stringResource(R.string.qso_local_date_time)
                } else {
                    stringResource(R.string.qso_utc_date_time)
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(date?.let { dateFormat.formatDate(it) }
                        ?: if (useLocalTime) {
                            stringResource(R.string.qso_date_local)
                        } else {
                            stringResource(R.string.qso_date_utc)
                        }
                    )
                }

                // TODO: change here to support seconds.
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    val displayTime = if (useLocalTime && time != null) {
                        val d = date ?: LocalDate.now(ZoneOffset.UTC)
                        time!!.atDate(d).atZone(ZoneOffset.UTC)
                            .withZoneSameInstant(ZoneId.systemDefault())
                            .toLocalTime()
                    } else time
                    Text(
                        displayTime?.toString() ?: if (useLocalTime) {
                            stringResource(R.string.qso_time_local)
                        } else {
                            stringResource(R.string.qso_time_utc)
                        }
                    )
                }
            }

            if (submitAttempted && (date == null || time == null)) {
                Text(
                    text = stringResource(R.string.required_field),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // mode
            ExposedDropdownMenuBox(
                expanded = modeExpanded,
                onExpandedChange = { modeExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedMode,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.mode)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(modeExpanded) },
                    isError = submitAttempted && selectedMode.isBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = modeExpanded,
                    onDismissRequest = { modeExpanded = false }
                ) {
                    modes.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.name) },
                            onClick = {
                                viewModel.selectedMode.value = mode.name
                                modeExpanded = false
                            }
                        )
                    }
                }
            }
            if (submitAttempted && selectedMode.isBlank()) {
                Text(
                    text = stringResource(R.string.required_field),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // submode
            OutlinedTextField(
                value = submode,
                onValueChange = { viewModel.submode.value = it },
                label = { Text(stringResource(R.string.submode)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // band
            ExposedDropdownMenuBox(
                expanded = bandExpanded,
                onExpandedChange = { bandExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedBand,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.band)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(bandExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = bandExpanded,
                    onDismissRequest = { bandExpanded = false }
                ) {
                    bands.forEach { band ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(band.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        band.freqRangeDisplay(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                viewModel.onBandSelected(band.name)
                                bandExpanded = false
                            }
                        )
                    }
                }
            }
            if (bandAutoInferred != null) {
                Text(
                    text = stringResource(R.string.band_inferred, bandAutoInferred!!),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (submitAttempted && selectedBand.isBlank() && freq.isBlank()) {
                Text(
                    text = stringResource(R.string.band_or_freq_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // freq
            OutlinedTextField(
                value = freq,
                onValueChange = { viewModel.onFreqChanged(it) },
                label = { Text(stringResource(R.string.frequency_mhz)) },
                isError = (submitAttempted && freq.isBlank()) || freqOutOfBand,
                supportingText = if (freqOutOfBand) {
                    { Text(stringResource(R.string.freq_out_of_band)) }
                } else if (submitAttempted && freq.isBlank()) {
                    { Text(stringResource(R.string.required_field)) }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()

            // rx band
            ExposedDropdownMenuBox(
                expanded = rxBandExpanded,
                onExpandedChange = { rxBandExpanded = it }
            ) {
                OutlinedTextField(
                    value = rxBand.ifEmpty { stringResource(R.string.none) },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.rx_band)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(rxBandExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = rxBandExpanded,
                    onDismissRequest = { rxBandExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.none)) },
                        onClick = {
                            viewModel.onRxBandSelected("")
                            rxBandExpanded = false
                        }
                    )
                    bands.forEach { band ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(band.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        band.freqRangeDisplay(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                viewModel.onRxBandSelected(band.name)
                                rxBandExpanded = false
                            }
                        )
                    }
                }
            }
            if (rxBandAutoInferred != null) {
                Text(
                    text = stringResource(R.string.band_inferred, rxBandAutoInferred!!),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // rx freq
            OutlinedTextField(
                value = rxFreq,
                onValueChange = { viewModel.onRxFreqChanged(it) },
                isError = rxFreqOutOfBand,
                label = { Text(stringResource(R.string.rx_frequency_mhz)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            if (rxFreqOutOfBand) {
                Text(
                    text = stringResource(R.string.freq_out_of_band),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // propagation mode
            ExposedDropdownMenuBox(
                expanded = propModeExpanded,
                onExpandedChange = { propModeExpanded = it }
            ) {
                OutlinedTextField(
                    value = propMode.ifEmpty { stringResource(R.string.none) },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.propagation_mode)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(propModeExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = propModeExpanded,
                    onDismissRequest = { propModeExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.none)) },
                        onClick = {
                            viewModel.propMode.value = ""
                            propModeExpanded = false
                        }
                    )
                    propModes.forEach { pm ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(pm.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        pm.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                viewModel.propMode.value = pm.name
                                propModeExpanded = false
                            }
                        )
                    }
                }
            }

            // satellite
            ExposedDropdownMenuBox(
                expanded = satExpanded,
                onExpandedChange = { satExpanded = it }
            ) {
                OutlinedTextField(
                    value = satName.ifEmpty { stringResource(R.string.none) },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.satellite)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(satExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = satExpanded,
                    onDismissRequest = { satExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.none)) },
                        onClick = {
                            viewModel.satName.value = ""
                            satExpanded = false
                        }
                    )
                    satellites.forEach { sat ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(sat.name, style = MaterialTheme.typography.bodyMedium)
                                    if (sat.description.isNotEmpty()) {
                                        Text(
                                            sat.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            onClick = {
                                viewModel.satName.value = sat.name
                                satExpanded = false
                            }
                        )
                    }
                }
            }

            // Bottom spacer so last field isn't hidden by soft keyboard
            Spacer(Modifier.height(32.dp))
        }
    }
}
