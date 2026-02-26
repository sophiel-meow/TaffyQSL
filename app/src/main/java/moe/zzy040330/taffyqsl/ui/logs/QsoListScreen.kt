package moe.zzy040330.taffyqsl.ui.logs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import moe.zzy040330.taffyqsl.R
import moe.zzy040330.taffyqsl.data.db.QsoRecordEntity
import moe.zzy040330.taffyqsl.data.AppPreferences
import moe.zzy040330.taffyqsl.domain.model.CertInfo
import moe.zzy040330.taffyqsl.domain.model.SigningProgress
import moe.zzy040330.taffyqsl.domain.model.StationLocation
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QsoListScreen(
    navController: NavController,
    viewModel: QsoListViewModel = viewModel()
) {
    val context = LocalContext.current
    val fileInfo by viewModel.fileInfo.collectAsState()
    val qsos by viewModel.qsos.collectAsState()
    val stations by viewModel.stations.collectAsState()
    val signingProgress by viewModel.signingProgress.collectAsState()
    val uploadState by viewModel.uploadState.collectAsState()

    var deleteQsoId by remember { mutableStateOf<Long?>(null) }
    var showSignDialog by remember { mutableStateOf(false) }
    var pendingOutputFile by remember { mutableStateOf<File?>(null) }

    // ******** Launchers ********
    val saveTq8Launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let { destUri ->
            val src = pendingOutputFile ?: return@let
            context.contentResolver.openOutputStream(destUri)?.use { out ->
                src.inputStream().use { inp -> inp.copyTo(out) }
            }
        }
        pendingOutputFile = null
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let { viewModel.exportAdif(it) }
    }

    // ******** Dialogs ********

    // Delete QSO confirmation dialog
    deleteQsoId?.let { id ->
        val qso = qsos.firstOrNull { it.id == id }
        AlertDialog(
            onDismissRequest = { deleteQsoId = null },
            title = { Text(stringResource(R.string.delete_qso_title)) },
            text = { Text(stringResource(R.string.delete_qso_message, qso?.callsign ?: "")) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteQso(id)
                    deleteQsoId = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteQsoId = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Sign options dialog
    if (showSignDialog) {
        SignOptionsDialog(
            stations = stations,
            certForCallsign = { viewModel.certForCallsign(it) },
            onSign = { station, dateFrom, dateTo ->
                val cert = viewModel.certForCallsign(station.callSign) ?: return@SignOptionsDialog
                viewModel.sign(station, cert.alias, dateFrom, dateTo)
                showSignDialog = false
            },
            onDismiss = { showSignDialog = false }
        )
    }

    // Signing progress / result dialogs
    when (val p = signingProgress) {
        is SigningProgress.Processing -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.signing_in_progress)) },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (p.total > 0) {
                            LinearProgressIndicator(
                                progress = { p.current.toFloat() / p.total },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("${p.current} / ${p.total} QSOs")
                        } else {
                            CircularProgressIndicator()
                        }
                    }
                },
                confirmButton = {}
            )
        }

        is SigningProgress.Completed -> {
            val result = p.result
            var committed by remember(p) { mutableStateOf(false) }

            fun commitOnce() {
                if (!committed) {
                    committed = true
                    viewModel.commitSignedQsos(result.pendingDupeEntities)
                }
            }

            Dialog(onDismissRequest = { viewModel.clearSigningProgress() }) {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            stringResource(R.string.signing_complete),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(Modifier.height(8.dp))

                        Text(stringResource(R.string.signing_result_total, result.totalQsos))
                        Text(stringResource(R.string.signing_result_signed, result.signedQsos))
                        if (result.duplicateQsos > 0)
                            Text(
                                stringResource(
                                    R.string.signing_result_duplicates,
                                    result.duplicateQsos
                                )
                            )
                        if (result.dateFilteredQsos > 0)
                            Text(
                                stringResource(
                                    R.string.signing_result_date_filtered,
                                    result.dateFilteredQsos
                                )
                            )
                        if (result.invalidQsos > 0)
                            Text(
                                stringResource(
                                    R.string.signing_result_invalid,
                                    result.invalidQsos
                                )
                            )

                        when (val us = uploadState) {
                            is UploadState.Uploading -> {
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text(
                                    stringResource(R.string.uploading_to_lotw),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            is UploadState.Done -> {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                Text(
                                    stringResource(R.string.upload_response, us.response),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            is UploadState.Error -> {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                Text(
                                    stringResource(R.string.upload_error, us.message),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            else -> {}
                        }

                        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

                        result.outputFile?.takeIf { it.exists() }?.let { file ->
                            TextButton(
                                onClick = {
                                    commitOnce()
                                    pendingOutputFile = file
                                    saveTq8Launcher.launch("signed_log.tq8")
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    stringResource(R.string.save_tq8),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.End
                                )
                            }
                            TextButton(
                                onClick = {
                                    commitOnce()
                                    viewModel.uploadTq8File(file)
                                },
                                enabled = uploadState !is UploadState.Uploading,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    stringResource(R.string.btn_upload_to_lotw),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.End
                                )
                            }
                        }

                        TextButton(
                            onClick = { viewModel.clearSigningProgress() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                stringResource(R.string.close),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }
        }

        is SigningProgress.Failed -> {
            AlertDialog(
                onDismissRequest = { viewModel.clearSigningProgress() },
                title = { Text(stringResource(R.string.signing_failed)) },
                text = { Text(p.error) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearSigningProgress() }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            )
        }

        null -> {}
    }

    // ******** Main screen ********

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fileInfo?.displayName ?: viewModel.fileName) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val name = fileInfo?.displayName ?: "log"
                            exportLauncher.launch("$name.adi")
                        }
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = stringResource(R.string.export_adif)
                        )
                    }
                    IconButton(onClick = { showSignDialog = true }) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = stringResource(R.string.sign_log)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("qso_edit/${viewModel.fileName}/0") }
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_qso))
            }
        }
    ) { padding ->
        if (qsos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_qso_records),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = 88.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(qsos, key = { it.id }) { qso ->
                    QsoRecordCard(
                        qso = qso,
                        onDelete = { deleteQsoId = qso.id },
                        onClick = { navController.navigate("qso_edit/${viewModel.fileName}/${qso.id}") }
                    )
                }
            }
        }
    }
}

// Sign Options Dialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignOptionsDialog(
    stations: List<StationLocation>,
    certForCallsign: (String) -> CertInfo?,
    onSign: (StationLocation, LocalDate?, LocalDate?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { AppPreferences.getInstance(context).dateFormat }
    var selectedStation by remember { mutableStateOf<StationLocation?>(null) }
    var stationExpanded by remember { mutableStateOf(false) }
    var dateFrom by remember { mutableStateOf<LocalDate?>(null) }
    var dateTo by remember { mutableStateOf<LocalDate?>(null) }
    var showDateFromPicker by remember { mutableStateOf(false) }
    var showDateToPicker by remember { mutableStateOf(false) }

    val canSign = selectedStation != null &&
        certForCallsign(selectedStation?.callSign ?: "") != null

    if (showDateFromPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = dateFrom?.toEpochDay()?.times(86400000L)
        )
        DatePickerDialog(
            onDismissRequest = { showDateFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        dateFrom = LocalDate.ofEpochDay(millis / 86400000)
                    }
                    showDateFromPicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDateFromPicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) { DatePicker(state = state) }
    }

    if (showDateToPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = dateTo?.toEpochDay()?.times(86400000L)
        )
        DatePickerDialog(
            onDismissRequest = { showDateToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        dateTo = LocalDate.ofEpochDay(millis / 86400000)
                    }
                    showDateToPicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDateToPicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) { DatePicker(state = state) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_station_to_sign)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (stations.isEmpty()) {
                    Text(
                        stringResource(R.string.no_stations_available),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = stationExpanded,
                        onExpandedChange = { stationExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedStation?.let { "${it.name} – ${it.callSign}" } ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.select_station)) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(
                                    stationExpanded
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = stationExpanded,
                            onDismissRequest = { stationExpanded = false }
                        ) {
                            stations.forEach { station ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                station.name,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                "${station.callSign} – ${station.dxccName}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedStation = station
                                        stationExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    selectedStation?.let { station ->
                        if (certForCallsign(station.callSign) == null) {
                            Text(
                                stringResource(R.string.no_cert_for_station),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                // Date range
                Text(
                    stringResource(R.string.date_filter_optional),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showDateFromPicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(dateFrom?.let { dateFormat.formatDate(it) } ?: stringResource(R.string.date_from))
                    }
                    OutlinedButton(
                        onClick = { showDateToPicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(dateTo?.let { dateFormat.formatDate(it) } ?: stringResource(R.string.date_to))
                    }
                }
                if (dateFrom != null || dateTo != null) {
                    TextButton(onClick = { dateFrom = null; dateTo = null }) {
                        Text(stringResource(R.string.clear_date_filter))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedStation?.let { onSign(it, dateFrom, dateTo) }
                },
                enabled = canSign
            ) { Text(stringResource(R.string.sign)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

// QSO record card
@Composable
fun QsoRecordCard(
    qso: QsoRecordEntity,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences.getInstance(context) }
    val dateFormat = prefs.dateFormat
    val useLocalTime = prefs.useLocalTime

    val qsoDate = if (qso.dateStr.length == 8) {
        runCatching {
            LocalDate.parse(qso.dateStr, java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
        }.getOrNull()
    } else null

    val formattedDate = qsoDate?.let { dateFormat.formatDate(it) } ?: qso.dateStr

    val formattedTime = if (qso.timeStr.length >= 4) {
        val utcHour = qso.timeStr.substring(0, 2).toIntOrNull() ?: 0
        val utcMin = qso.timeStr.substring(2, 4).toIntOrNull() ?: 0
        if (useLocalTime && qsoDate != null) {
            val localTime = LocalTime.of(utcHour, utcMin)
                .atDate(qsoDate)
                .atZone(ZoneOffset.UTC)
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalTime()
            "${localTime.hour.toString().padStart(2, '0')}:${localTime.minute.toString().padStart(2, '0')}"
        } else {
            "${utcHour.toString().padStart(2, '0')}:${utcMin.toString().padStart(2, '0')}Z"
        }
    } else "${qso.timeStr}Z"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = qso.callsign,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$formattedDate $formattedTime",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${qso.bandName}  ${qso.modeName}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
