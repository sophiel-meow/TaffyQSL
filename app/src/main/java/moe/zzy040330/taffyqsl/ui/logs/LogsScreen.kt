package moe.zzy040330.taffyqsl.ui.logs

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import moe.zzy040330.taffyqsl.R
import moe.zzy040330.taffyqsl.data.db.QsoFileEntity
import moe.zzy040330.taffyqsl.domain.model.SigningProgress
import moe.zzy040330.taffyqsl.domain.model.StationLocation
import java.io.File
import java.time.LocalDate

// TODO: add lotw query support: https://lotw.arrl.org/lotw-help/developer-query-qsos-qsls/
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    navController: NavController,
    viewModel: LogViewModel = viewModel()
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.tab_sign_log),
        stringResource(R.string.tab_adif_files)
    )

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.nav_logs)) })
        },
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }
            when (selectedTabIndex) {
                0 -> SignLogTab(
                    viewModel = viewModel,
                    contentPadding = contentPadding
                )

                1 -> AdifFilesTab(
                    viewModel = viewModel,
                    contentPadding = contentPadding,
                    navController = navController
                )
            }
        }
    }
}

// Sign an external ADIF file

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignLogTab(
    viewModel: LogViewModel,
    contentPadding: PaddingValues
) {
    val context = LocalContext.current
    val stations by viewModel.stations.collectAsState()
    val signingProgress by viewModel.signingProgress.collectAsState()
    val uploadState by viewModel.uploadState.collectAsState()

    // Form state
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var selectedStation by remember { mutableStateOf<StationLocation?>(null) }
    var stationExpanded by remember { mutableStateOf(false) }
    var dateFrom by remember { mutableStateOf<LocalDate?>(null) }
    var dateTo by remember { mutableStateOf<LocalDate?>(null) }
    var showDateFromPicker by remember { mutableStateOf(false) }
    var showDateToPicker by remember { mutableStateOf(false) }

    // Track whether user has attempted to sign (to defer showing required-field errors)
    var submitAttempted by remember { mutableStateOf(false) }

    // Holds output file reference while user interacts with the save dialog
    var pendingOutputFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(Unit) { viewModel.refreshAll() }


    // ******** Launchers ********
    val pickAdif = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            selectedUri = it
            selectedFileName = context.contentResolver
                .query(it, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val idx =
                            c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) c.getString(idx) else null
                    } else null
                } ?: it.lastPathSegment ?: "adif_file"
        }
    }

    val saveTq8Launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let { destUri ->
            val src = pendingOutputFile ?: return@let
            context.contentResolver.openOutputStream(destUri)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
            }
        }
        pendingOutputFile = null
    }

    // ******** Derived state ********

    val certForStation = selectedStation?.let { viewModel.certForCallsign(it.callSign) }
    val canSign = selectedUri != null && selectedStation != null && certForStation != null

    // ******** Date pickers ********

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

    // ******** Signing progress & result dialogs ********

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
            // Track whether duplicates have been committed to DB
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

                        // Signing stats
                        Text(
                            stringResource(
                                R.string.signing_result_total,
                                result.totalQsos
                            )
                        )
                        Text(
                            stringResource(
                                R.string.signing_result_signed,
                                result.signedQsos
                            )
                        )
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
                        if (result.gridMismatchQsos > 0)
                            Text(
                                stringResource(
                                    R.string.signing_result_grid_mismatch,
                                    result.gridMismatchQsos
                                )
                            )
                        if (result.invalidQsos > 0)
                            Text(
                                stringResource(
                                    R.string.signing_result_invalid,
                                    result.invalidQsos
                                )
                            )

                        // Upload progress / result
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
                                HorizontalDivider(
                                    modifier = Modifier.padding(
                                        vertical = 4.dp
                                    )
                                )
                                Text(
                                    stringResource(
                                        R.string.upload_response,
                                        us.response
                                    ),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            is UploadState.Error -> {
                                HorizontalDivider(
                                    modifier = Modifier.padding(
                                        vertical = 4.dp
                                    )
                                )
                                Text(
                                    stringResource(
                                        R.string.upload_error,
                                        us.message
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            else -> {}
                        }

                        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

                        // Action buttons — stacked to prevent text wrapping on narrow screens
                        result.outputFile?.takeIf { it.exists() }
                            ?.let { file ->
                                // Save .tq8
                                TextButton(
                                    onClick = {
                                        commitOnce()
                                        pendingOutputFile =
                                            file
                                        saveTq8Launcher.launch(
                                            "signed_log.tq8"
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        stringResource(R.string.save_tq8),
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.End
                                    )
                                }

                                // Upload to LoTW
                                TextButton(
                                    onClick = {
                                        commitOnce()
                                        viewModel.uploadTq8File(
                                            file
                                        )
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

                        // Close
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

    // ******** Main Form ********

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(
                top = 16.dp,
                bottom = 16.dp + contentPadding.calculateBottomPadding()
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // select file
        Text(
            stringResource(R.string.tab_sign_log),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            stringResource(R.string.sign_adif_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedButton(
            onClick = { pickAdif.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Default.Upload,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = selectedFileName.ifEmpty { stringResource(R.string.select_adif_file) },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        HorizontalDivider()

        // select station
        Text(
            stringResource(R.string.select_station),
            style = MaterialTheme.typography.titleMedium
        )

        if (stations.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = stringResource(R.string.no_stations_available),
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            ExposedDropdownMenuBox(
                expanded = stationExpanded,
                onExpandedChange = { stationExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedStation?.let { "${it.name} \u2013 ${it.callSign}" }
                        ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.select_station)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(
                            stationExpanded
                        )
                    },
                    isError = submitAttempted && selectedStation == null,
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
                                        "${station.callSign} \u2013 ${station.dxccName}",
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

            // Show warning when no cert is found for the selected station's callsign
            selectedStation?.let { station ->
                if (viewModel.certForCallsign(station.callSign) == null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.no_cert_for_station),
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        HorizontalDivider()

        // select data filter
        Text(
            stringResource(R.string.date_filter_optional),
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { showDateFromPicker = true },
                modifier = Modifier.weight(1f)
            ) {
                Text(dateFrom?.toString() ?: stringResource(R.string.date_from))
            }
            OutlinedButton(
                onClick = { showDateToPicker = true },
                modifier = Modifier.weight(1f)
            ) {
                Text(dateTo?.toString() ?: stringResource(R.string.date_to))
            }
        }

        if (dateFrom != null || dateTo != null) {
            TextButton(onClick = { dateFrom = null; dateTo = null }) {
                Text(stringResource(R.string.clear_date_filter))
            }
        }

        // sign button
        Button(
            onClick = {
                submitAttempted = true
                if (canSign) {
                    val station = selectedStation ?: return@Button
                    val uri = selectedUri ?: return@Button
                    val cert = viewModel.certForCallsign(station.callSign)
                        ?: return@Button
                    viewModel.signAdifFile(
                        uri = uri,
                        station = station,
                        certAlias = cert.alias,
                        dateFrom = dateFrom,
                        dateTo = dateTo
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.sign))
        }
    }
}

// ******** Internal ADIF file ********
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdifFilesTab(
    viewModel: LogViewModel,
    contentPadding: PaddingValues,
    navController: NavController
) {
    val context = LocalContext.current
    val qsoFiles by viewModel.qsoFiles.collectAsState()
    val importState by viewModel.importState.collectAsState()

    // FAB expansion state
    var fabExpanded by remember { mutableStateOf(false) }

    // New log dialog state
    var showNewLogDialog by remember { mutableStateOf(false) }
    var newLogName by remember { mutableStateOf("") }

    // Import name dialog state
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var importDisplayName by remember { mutableStateOf("") }

    // Delete confirmation state
    var deleteFile by remember { mutableStateOf<QsoFileEntity?>(null) }

    // Rename dialog state
    var renameFile by remember { mutableStateOf<QsoFileEntity?>(null) }
    var renameText by remember { mutableStateOf("") }

    // ******** ADIF import launcher ********
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            // Pre-fill display name from file name
            val name = context.contentResolver
                .query(it, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val idx =
                            c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) c.getString(idx) else null
                    } else null
                } ?: it.lastPathSegment ?: "Imported Log"
            pendingImportUri = it
            importDisplayName = name.substringBeforeLast('.').ifBlank { name }
        }
        fabExpanded = false
    }

    // ******** Import name dialog ********
    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = {
                pendingImportUri = null
                importDisplayName = ""
            },
            title = { Text(stringResource(R.string.import_adif_name_title)) },
            text = {
                OutlinedTextField(
                    value = importDisplayName,
                    onValueChange = { importDisplayName = it },
                    label = { Text(stringResource(R.string.import_adif_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (importDisplayName.isNotBlank()) {
                            viewModel.importAdif(
                                uri,
                                importDisplayName.trim()
                            )
                            pendingImportUri = null
                            importDisplayName = ""
                        }
                    },
                    enabled = importDisplayName.isNotBlank()
                ) { Text(stringResource(R.string.import_adif)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingImportUri = null
                    importDisplayName = ""
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // ******** Import result dialog ********
    when (val result = importState) {
        is ImportResult.Done -> {
            AlertDialog(
                onDismissRequest = { viewModel.clearImportState() },
                title = { Text(stringResource(R.string.import_result_title)) },
                text = {
                    Text(
                        pluralStringResource(
                            R.plurals.import_result_body,
                            result.skipped,
                            result.imported,
                            result.skipped
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearImportState() }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            )
        }

        is ImportResult.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.clearImportState() },
                title = { Text(stringResource(R.string.import_result_title)) },
                text = { Text(result.message) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearImportState() }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            )
        }

        null -> {}
    }

    // ******** New log name dialog ********
    if (showNewLogDialog) {
        AlertDialog(
            onDismissRequest = {
                showNewLogDialog = false
                newLogName = ""
            },
            title = { Text(stringResource(R.string.new_adif_title)) },
            text = {
                OutlinedTextField(
                    value = newLogName,
                    onValueChange = { newLogName = it },
                    label = { Text(stringResource(R.string.log_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newLogName.isNotBlank()) {
                            viewModel.createQsoFile(newLogName.trim())
                            showNewLogDialog = false
                            newLogName = ""
                        }
                    },
                    enabled = newLogName.isNotBlank()
                ) { Text(stringResource(R.string.create)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showNewLogDialog = false
                    newLogName = ""
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // ******** Delete confirmation dialog ********
    deleteFile?.let { file ->
        AlertDialog(
            onDismissRequest = { deleteFile = null },
            title = { Text(stringResource(R.string.delete_file_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.delete_file_message,
                        file.displayName
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteQsoFile(file.fileName)
                    deleteFile = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteFile = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // ******** Rename dialog ********
    renameFile?.let { file ->
        AlertDialog(
            onDismissRequest = {
                renameFile = null
                renameText = ""
            },
            title = { Text(stringResource(R.string.rename_file_title)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text(stringResource(R.string.log_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            viewModel.renameQsoFile(
                                file.fileName,
                                renameText.trim()
                            )
                            renameFile = null
                            renameText = ""
                        }
                    },
                    enabled = renameText.isNotBlank()
                ) { Text(stringResource(R.string.rename)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    renameFile = null
                    renameText = ""
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // ******** Main content ********
    Box(modifier = Modifier.fillMaxSize()) {
        if (qsoFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_adif_files),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 80.dp + contentPadding.calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(qsoFiles, key = { it.fileName }) { file ->
                    AdifFileCard(
                        file = file,
                        onDelete = { deleteFile = file },
                        onRename = {
                            renameFile = file
                            renameText = file.displayName
                        },
                        onClick = { navController.navigate("qso_list/${file.fileName}") }
                    )
                }
            }
        }

        // ******** Expandable FAB ********
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 16.dp,
                    bottom = 16.dp + contentPadding.calculateBottomPadding()
                ),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Show sub FABs when expanded
            AnimatedVisibility(
                visible = fabExpanded,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Import ADIF
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = 2.dp
                        ) {
                            Text(
                                text = stringResource(R.string.import_adif),
                                modifier = Modifier.padding(
                                    horizontal = 12.dp,
                                    vertical = 6.dp
                                ),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        SmallFloatingActionButton(
                            onClick = {
                                importLauncher.launch(arrayOf("*/*"))
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Icon(
                                Icons.Default.Upload,
                                contentDescription = stringResource(
                                    R.string.import_adif
                                )
                            )
                        }
                    }

                    // New log
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = 2.dp
                        ) {
                            Text(
                                text = stringResource(R.string.new_adif_log),
                                modifier = Modifier.padding(
                                    horizontal = 12.dp,
                                    vertical = 6.dp
                                ),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        SmallFloatingActionButton(
                            onClick = {
                                showNewLogDialog = true
                                fabExpanded = false
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(
                                    R.string.new_adif_log
                                )
                            )
                        }
                    }
                }
            }

            // Main FAB
            FloatingActionButton(
                onClick = { fabExpanded = !fabExpanded }
            ) {
                Icon(
                    if (fabExpanded) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = if (fabExpanded) stringResource(R.string.close) else stringResource(
                        R.string.new_adif_log
                    )
                )
            }
        }
    }
}

// ******** Shared card ********

@Composable
fun AdifFileCard(
    file: QsoFileEntity,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (file.qsoCount > 0)
                        "${file.qsoCount} QSOs \u2022 ${file.fileName}"
                    else
                        file.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Menu"
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rename)) },
                        onClick = {
                            showMenu = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
