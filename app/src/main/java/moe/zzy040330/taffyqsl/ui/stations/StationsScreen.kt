package moe.zzy040330.taffyqsl.ui.stations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import moe.zzy040330.taffyqsl.BuildConfig
import moe.zzy040330.taffyqsl.R
import moe.zzy040330.taffyqsl.data.AppPreferences
import moe.zzy040330.taffyqsl.data.config.StateValue
import moe.zzy040330.taffyqsl.domain.model.CertInfo
import moe.zzy040330.taffyqsl.domain.model.StationLocation

private val GRID_REGEX = Regex("""^[A-Ra-r]{2}[0-9]{2}([A-Xa-x]{2})?$""")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationsScreen(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: StationViewModel = viewModel()
) {
    val stations by viewModel.stations.collectAsState()
    val certs by viewModel.certs.collectAsState()
    var showDeleteDialog by remember { mutableStateOf<StationLocation?>(null) }
    var showEditDialog by remember { mutableStateOf<StationLocation?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val debugMode =
        if (BuildConfig.DEBUG) AppPreferences.getInstance(context).isDebugMode else false

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.nav_stations)) })
        },
        floatingActionButton = {
            Box(modifier = Modifier.padding(contentPadding)) {
                FloatingActionButton(onClick = {
                    viewModel.refreshCerts()
                    showAddDialog = true
                }) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_station)
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        if (stations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(contentPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_stations),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp + contentPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    end = 16.dp + contentPadding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    top = 16.dp + padding.calculateTopPadding(),
                    bottom = 16.dp + padding.calculateBottomPadding() + contentPadding.calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(stations, key = { it.id }) { station ->
                    StationCard(
                        station = station,
                        onClick = { showEditDialog = station },
                        onDelete = { showDeleteDialog = station }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        StationEditDialog(
            station = null,
            certs = certs,
            viewModel = viewModel,
            debugMode = debugMode,
            onDismiss = { showAddDialog = false },
            onSave = { station ->
                viewModel.saveStation(station)
                showAddDialog = false
            }
        )
    }

    showEditDialog?.let { station ->
        StationEditDialog(
            station = station,
            certs = certs,
            viewModel = viewModel,
            debugMode = debugMode,
            onDismiss = { showEditDialog = null },
            onSave = { updatedStation ->
                viewModel.saveStation(updatedStation)
                showEditDialog = null
            }
        )
    }

    showDeleteDialog?.let { station ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.delete_station)) },
            text = { Text(stringResource(R.string.station_delete_message, station.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteStation(station.id)
                    showDeleteDialog = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun StationCard(
    station: StationLocation,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
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
                Text(text = station.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${station.callSign} \u2013 ${station.dxccName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (station.grid.isNotEmpty()) {
                    Text(
                        text = "Grid: ${station.grid}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationEditDialog(
    station: StationLocation?,
    certs: List<CertInfo>,
    viewModel: StationViewModel,
    debugMode: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (StationLocation) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    // Form state
    var name by remember { mutableStateOf(station?.name ?: "") }

    // Callsign: for new station, empty; for edit, use existing
    var selectedCallSign by remember { mutableStateOf(station?.callSign ?: "") }
    var dxcc by remember { mutableIntStateOf(station?.dxcc ?: 0) }
    var dxccName by remember { mutableStateOf(station?.dxccName ?: "") }
    var stateFieldId by remember { mutableStateOf("") }
    var stateValues by remember { mutableStateOf<List<StateValue>>(emptyList()) }

    var grid by remember { mutableStateOf(station?.grid ?: "") }
    var selectedState by remember { mutableStateOf(station?.state ?: "") }

    // Secondary subdivisions (county / city and park)
    var countyValues by remember { mutableStateOf<List<StateValue>>(emptyList()) }
    var countyFieldId by remember { mutableStateOf(station?.countyFieldName ?: "") }
    var countyLabel by remember { mutableStateOf("County") }
    var selectedCounty by remember { mutableStateOf(station?.county ?: "") }

    var parkValues by remember { mutableStateOf<List<StateValue>>(emptyList()) }
    var parkFieldId by remember { mutableStateOf(station?.parkFieldName ?: "") }
    var parkLabel by remember { mutableStateOf("Park") }
    var selectedPark by remember { mutableStateOf(station?.park ?: "") }

    var cqZone by remember { mutableStateOf(station?.cqZone ?: "") }
    var ituZone by remember { mutableStateOf(station?.ituZone ?: "") }
    var iota by remember { mutableStateOf(station?.iota ?: "") }

    var callsignExpanded by remember { mutableStateOf(false) }
    var stateExpanded by remember { mutableStateOf(false) }
    var countyExpanded by remember { mutableStateOf(false) }
    var parkExpanded by remember { mutableStateOf(false) }

    // Debug-only: free DXCC selector
    // TODO: build type debug
    var dxccExpanded by remember { mutableStateOf(false) }
    var dxccQuery by remember { mutableStateOf("") }

    // whether user has attempted to save (to defer showing required-field errors)
    var submitAttempted by remember { mutableStateOf(false) }

    // When callsign changes, update DXCC and load state values
    LaunchedEffect(selectedCallSign) {
        val cert = certs.firstOrNull { it.callSign == selectedCallSign }
        if (cert != null) {
            dxcc = cert.dxccEntity
            dxccName = cert.dxccEntityName
            val fieldId = viewModel.stateFieldId(cert.dxccEntity)
            stateFieldId = fieldId
            stateValues = if (fieldId.isNotEmpty()) viewModel.loadStateValues(
                fieldId,
                cert.dxccEntity
            ) else
                emptyList()

            // Reset state and secondary selections if callsign changed
            if (station?.callSign != selectedCallSign) {
                selectedState = ""
                selectedCounty = ""
                selectedPark = ""
                countyValues = emptyList()
                parkValues = emptyList()
            }
        }
    }

    // For edit: load initial state values when dxcc is already set
    LaunchedEffect(Unit) {
        if (dxcc != 0) {
            val fieldId = viewModel.stateFieldId(dxcc)
            stateFieldId = fieldId
            stateValues =
                if (fieldId.isNotEmpty()) viewModel.loadStateValues(fieldId, dxcc) else emptyList()
        }

        // Load initial secondary values for existing station
        if (stateFieldId.isNotEmpty() && selectedState.isNotEmpty()) {
            val secondary = viewModel.secondaryFields(stateFieldId)
            for (field in secondary) {
                val values = viewModel.loadSubValues(field.fieldId, selectedState)
                if (field.fieldId.contains("PARK")) {
                    parkValues = values
                    parkFieldId = field.fieldId
                    parkLabel = field.label
                } else {
                    countyValues = values
                    countyFieldId = field.fieldId
                    countyLabel = field.label
                }
            }
        }
    }

    // Load secondary subdivision values when state selection changes
    LaunchedEffect(selectedState, stateFieldId) {
        if (stateFieldId.isNotEmpty() && selectedState.isNotEmpty()) {
            val secondary = viewModel.secondaryFields(stateFieldId)
            countyValues = emptyList()
            parkValues = emptyList()
            for (field in secondary) {
                val values = viewModel.loadSubValues(field.fieldId, selectedState)
                if (field.fieldId.contains("PARK")) {
                    parkValues = values
                    parkFieldId = field.fieldId
                    parkLabel = field.label
                } else {
                    countyValues = values
                    countyFieldId = field.fieldId
                    countyLabel = field.label
                }
            }
        } else {
            countyValues = emptyList()
            parkValues = emptyList()
        }
    }

    val gridValid = grid.isEmpty() || grid.matches(GRID_REGEX)
    val canSave =
        name.isNotBlank() && selectedCallSign.isNotEmpty() && grid.isNotEmpty() && gridValid

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.93f)
                .heightIn(max = 620.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Title
                Text(
                    text = if (station == null) stringResource(R.string.new_station)
                    else stringResource(R.string.edit_station),
                    style = MaterialTheme.typography.titleLarge
                )

                // Station name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.station_name)) },
                    singleLine = true,
                    isError = submitAttempted && name.isBlank(),
                    modifier = Modifier.fillMaxWidth()
                )

                // Callsign dropdown
                if (certs.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.no_certs_import_first),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    ExposedDropdownMenuBox(
                        expanded = callsignExpanded,
                        onExpandedChange = { callsignExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedCallSign,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.callsign)) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(
                                    callsignExpanded
                                )
                            },
                            isError = submitAttempted && selectedCallSign.isEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = callsignExpanded,
                            onDismissRequest = { callsignExpanded = false }
                        ) {
                            certs.forEach { cert ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                cert.callSign,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                cert.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedCallSign = cert.callSign
                                        callsignExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // DXCC Entity: debug mode allows free selection; for normal mode is read-only
                if (BuildConfig.DEBUG && debugMode) {
                    val allDxcc = viewModel.dxccEntities
                    val filteredDxcc = remember(dxccQuery) {
                        if (dxccQuery.isEmpty()) emptyList()
                        else allDxcc.filter { entity ->
                            entity.name.contains(dxccQuery, ignoreCase = true) ||
                                entity.arrlId.toString().startsWith(dxccQuery)
                        }.take(50)
                    }
                    ExposedDropdownMenuBox(
                        expanded = dxccExpanded && filteredDxcc.isNotEmpty(),
                        onExpandedChange = { dxccExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = dxccQuery,
                            onValueChange = { dxccQuery = it; dxccExpanded = true },
                            label = { Text(stringResource(R.string.dxcc_entity_debug)) },
                            placeholder = {
                                Text(
                                    if (dxcc != 0) "$dxcc \u2013 $dxccName"
                                    else stringResource(R.string.dxcc_search_hint),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dxccExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = dxccExpanded && filteredDxcc.isNotEmpty(),
                            onDismissRequest = { dxccExpanded = false }
                        ) {
                            filteredDxcc.forEach { entity ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "${entity.arrlId} \u2013 ${entity.name}" +
                                                if (entity.deleted) " (deleted)" else ""
                                        )
                                    },
                                    onClick = {
                                        dxcc = entity.arrlId
                                        dxccName = entity.name
                                        dxccQuery = ""
                                        dxccExpanded = false
                                        // Reset subdivision selections and reload for new DXCC
                                        selectedState = ""
                                        selectedCounty = ""
                                        selectedPark = ""
                                        countyValues = emptyList()
                                        parkValues = emptyList()
                                        coroutineScope.launch {
                                            val fieldId = viewModel.stateFieldId(entity.arrlId)
                                            stateFieldId = fieldId
                                            stateValues = if (fieldId.isNotEmpty())
                                                viewModel.loadStateValues(fieldId, entity.arrlId)
                                            else emptyList()
                                        }
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // Normal mode: read-only, auto-filled from selected certificate
                    OutlinedTextField(
                        value = if (dxcc != 0) "$dxcc – $dxccName" else "", // en dash
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.dxcc_entity)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        enabled = false
                    )
                }

                // Grid Square
                OutlinedTextField(
                    value = grid,
                    onValueChange = { grid = it.uppercase() },
                    label = { Text(stringResource(R.string.grid_square)) },
                    singleLine = true,
                    isError = (submitAttempted && grid.isEmpty()) || (grid.isNotEmpty() && !gridValid),
                    supportingText = if (grid.isNotEmpty() && !gridValid) {
                        { Text(stringResource(R.string.grid_format_error)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )

                // Province / State (conditional)
                if (stateValues.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = stateExpanded,
                        onExpandedChange = { stateExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = if (selectedState.isEmpty()) "" else {
                                stateValues.firstOrNull { it.abbrev == selectedState }
                                    ?.let { "${it.abbrev} \u2013 ${it.name}" } ?: selectedState
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.state_province)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(stateExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = stateExpanded,
                            onDismissRequest = { stateExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.none)) },
                                onClick = {
                                    selectedState = ""
                                    stateExpanded = false
                                }
                            )
                            stateValues.forEach { sv ->
                                DropdownMenuItem(
                                    text = { Text("${sv.abbrev} \u2013 ${sv.name}") },
                                    onClick = {
                                        selectedState = sv.abbrev
                                        // Reset secondary selections when state changes
                                        selectedCounty = ""
                                        selectedPark = ""
                                        // Auto-fill CQ and ITU zones from zonemap
                                        if (sv.cqZone.isNotEmpty()) cqZone =
                                            sv.cqZone.split(",").first()
                                        if (sv.ituZone.isNotEmpty()) ituZone =
                                            sv.ituZone.split(",").first()
                                        stateExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // County / City (dropdown, conditional on state selection)
                if (countyValues.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = countyExpanded,
                        onExpandedChange = { countyExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = if (selectedCounty.isEmpty()) "" else {
                                countyValues.firstOrNull { it.abbrev == selectedCounty }
                                    ?.let { "${it.abbrev} \u2013 ${it.name}" } ?: selectedCounty
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(countyLabel) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(countyExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = countyExpanded,
                            onDismissRequest = { countyExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.none)) },
                                onClick = { selectedCounty = ""; countyExpanded = false }
                            )
                            countyValues.forEach { sv ->
                                DropdownMenuItem(
                                    text = { Text("${sv.abbrev} \u2013 ${sv.name}") },
                                    onClick = {
                                        selectedCounty = sv.abbrev
                                        countyExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Park (dropdown, conditional on state selection)
                if (parkValues.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = parkExpanded,
                        onExpandedChange = { parkExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = if (selectedPark.isEmpty()) "" else {
                                parkValues.firstOrNull { it.abbrev == selectedPark }
                                    ?.let { "${it.abbrev} \u2013 ${it.name}" } ?: selectedPark
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(parkLabel) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(parkExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = parkExpanded,
                            onDismissRequest = { parkExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.none)) },
                                onClick = { selectedPark = ""; parkExpanded = false }
                            )
                            parkValues.forEach { sv ->
                                DropdownMenuItem(
                                    text = { Text("${sv.abbrev} \u2013 ${sv.name}") },
                                    onClick = {
                                        selectedPark = sv.abbrev
                                        parkExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // CQ Zone
                OutlinedTextField(
                    value = cqZone,
                    onValueChange = { v ->
                        val n = v.filter { it.isDigit() }
                        if (n.isEmpty() || (n.toIntOrNull() ?: 0) <= 40) cqZone = n
                    },
                    label = { Text(stringResource(R.string.cq_zone)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                // ITU Zone
                OutlinedTextField(
                    value = ituZone,
                    onValueChange = { v ->
                        val n = v.filter { it.isDigit() }
                        if (n.isEmpty() || (n.toIntOrNull() ?: 0) <= 90) ituZone = n
                    },
                    label = { Text(stringResource(R.string.itu_zone)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                // IOTA ID
                OutlinedTextField(
                    value = iota,
                    onValueChange = { iota = it.uppercase() },
                    label = { Text(stringResource(R.string.iota_id)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.cancel)) }

                    Button(
                        onClick = {
                            submitAttempted = true
                            if (canSave) {
                                onSave(
                                    StationLocation(
                                        id = station?.id ?: 0L,
                                        name = name.trim(),
                                        callSign = selectedCallSign,
                                        dxcc = dxcc,
                                        dxccName = dxccName,
                                        grid = grid.trim(),
                                        state = selectedState,
                                        stateFieldName = stateFieldId,
                                        county = selectedCounty,
                                        countyFieldName = countyFieldId,
                                        park = selectedPark,
                                        parkFieldName = parkFieldId,
                                        cqZone = cqZone,
                                        ituZone = ituZone,
                                        iota = iota.trim()
                                    )
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.save)) }
                }
            }
        }
    }
}
