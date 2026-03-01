package moe.zzy040330.taffyqsl.ui.lotw

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import moe.zzy040330.taffyqsl.R
import moe.zzy040330.taffyqsl.data.lotw.LotwException
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LotwScreen(innerPadding: PaddingValues, onNavigateToSettings: () -> Unit) {
    val vm: LotwViewModel = viewModel()

    LaunchedEffect(Unit) {
        vm.reloadHasCredentials()
    }

    val hasCredentials by vm.hasCredentials.collectAsState()

    if (!hasCredentials) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.lotw_no_credentials),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.lotw_no_credentials_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onNavigateToSettings) {
                    Text(stringResource(R.string.lotw_go_to_settings))
                }
            }
        }
        return
    }

    val qsoQsl by vm.qsoQsl.collectAsState()
    val band by vm.band.collectAsState()
    val mode by vm.mode.collectAsState()
    val sinceDate by vm.sinceDate.collectAsState()
    val ownCall by vm.ownCall.collectAsState()
    val workedCall by vm.workedCall.collectAsState()
    val endDate by vm.endDate.collectAsState()
    val showDxccDetail by vm.showDxccDetail.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val hasQueried by vm.hasQueried.collectAsState()
    val results by vm.results.collectAsState()
    val error by vm.error.collectAsState()

    var moreFiltersExpanded by remember { mutableStateOf(false) }
    var bandExpanded by remember { mutableStateOf(false) }
    var modeExpanded by remember { mutableStateOf(false) }
    var showSinceDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    if (showSinceDatePicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = sinceDate?.toEpochDay()?.times(86400000L)
        )
        DatePickerDialog(
            onDismissRequest = { showSinceDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { millis ->
                        vm.sinceDate.value = LocalDate.ofEpochDay(millis / 86400000L)
                    }
                    showSinceDatePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showSinceDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) { DatePicker(state = dpState) }
    }

    if (showEndDatePicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = endDate?.toEpochDay()?.times(86400000L)
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { millis ->
                        vm.endDate.value = LocalDate.ofEpochDay(millis / 86400000L)
                    }
                    showEndDatePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) { DatePicker(state = dpState) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        // Filter panel
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {

            // row 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {


                SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        onClick = { if (!showDxccDetail) vm.qsoQsl.value = "yes" },
                        selected = qsoQsl == "yes" || showDxccDetail
                    ) { Text(stringResource(R.string.lotw_filter_confirmed)) }
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        onClick = { if (!showDxccDetail) vm.qsoQsl.value = "no" },
                        selected = qsoQsl == "no" && !showDxccDetail
                    ) { Text(stringResource(R.string.lotw_filter_uploaded)) }
                }

                OutlinedButton(
                    onClick = { showSinceDatePicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = sinceDate?.let {
                            stringResource(R.string.lotw_since_date, it.toString())
                        } ?: stringResource(R.string.lotw_since_any),
                        maxLines = 1
                    )
                }
            }


            Spacer(Modifier.height(8.dp))

            // row 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                ExposedDropdownMenuBox(
                    expanded = modeExpanded,
                    onExpandedChange = { modeExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = mode.ifEmpty { stringResource(R.string.lotw_filter_any) },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.mode)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(modeExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = modeExpanded,
                        onDismissRequest = { modeExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.lotw_filter_any)) },
                            onClick = { vm.mode.value = ""; modeExpanded = false }
                        )
                        vm.modes.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m.name) },
                                onClick = { vm.mode.value = m.name; modeExpanded = false }
                            )
                        }
                    }
                }


                ExposedDropdownMenuBox(
                    expanded = bandExpanded,
                    onExpandedChange = { bandExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = band.ifEmpty { stringResource(R.string.lotw_filter_any) },
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
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.lotw_filter_any)) },
                            onClick = { vm.band.value = ""; bandExpanded = false }
                        )
                        vm.bands.forEach { b ->
                            DropdownMenuItem(
                                text = { Text(b.name) },
                                onClick = { vm.band.value = b.name; bandExpanded = false }
                            )
                        }
                    }
                }
            }

            // More filters toggle
            TextButton(
                onClick = { moreFiltersExpanded = !moreFiltersExpanded },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    if (moreFiltersExpanded) stringResource(R.string.lotw_less_filters)
                    else stringResource(R.string.lotw_more_filters)
                )
            }

            AnimatedVisibility(visible = moreFiltersExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ownCall,
                        onValueChange = { vm.ownCall.value = it },
                        label = { Text(stringResource(R.string.lotw_filter_own_call)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = workedCall,
                        onValueChange = { vm.workedCall.value = it },
                        label = { Text(stringResource(R.string.lotw_filter_worked_call)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = { showEndDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = endDate?.let {
                                stringResource(R.string.lotw_until_date, it.toString())
                            } ?: stringResource(R.string.lotw_until_any)
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = showDxccDetail,
                            onCheckedChange = {
                                vm.showDxccDetail.value = it
                                if (it) vm.qsoQsl.value = "yes"
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.lotw_show_dxcc_detail),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(Modifier.height(0.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { vm.query() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Text(stringResource(R.string.lotw_query))
            }
        }

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        HorizontalDivider()

        when {
            error != null -> {
                val errorMsg = when (val e = error) {
                    is LotwException.NoCredentials -> stringResource(R.string.lotw_error_no_credentials)
                    is LotwException.AuthFailed -> stringResource(R.string.lotw_error_auth_failed)
                    is LotwException.ServerError -> stringResource(R.string.lotw_error_server, e.httpCode)
                    else -> stringResource(R.string.lotw_error_network)
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = errorMsg,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            !hasQueried -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.lotw_query_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            results.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.lotw_no_results),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(results) { record ->
                        LotwQsoCard(record)
                    }
                }
            }
        }
    }
}

@Composable
private fun LotwQsoCard(record: Map<String, String>) {
    val call = record["CALL"] ?: "?"
    val band = record["BAND"] ?: ""
    val mode = record["MODE"] ?: ""
    val freq = record["FREQ"]
    val date = record["QSO_DATE"] ?: ""
    val time = record["TIME_ON"] ?: ""
    val confirmed = record["QSL_RCVD"]?.uppercase() == "Y"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = call,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                val freqPart = if (!freq.isNullOrBlank()) "$freq MHz · " else ""
                Text(
                    text = "$freqPart$mode · $band",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "$date · ${time}Z",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (confirmed) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = if (confirmed) "✓ Confirmed" else "Uploaded",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (confirmed) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
