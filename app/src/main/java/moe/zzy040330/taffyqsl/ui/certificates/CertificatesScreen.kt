package moe.zzy040330.taffyqsl.ui.certificates

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import moe.zzy040330.taffyqsl.R
import moe.zzy040330.taffyqsl.data.AppPreferences
import moe.zzy040330.taffyqsl.domain.model.CertInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CertificatesScreen(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: CertViewModel = viewModel()
) {
    val context = LocalContext.current
    val certs by viewModel.certs.collectAsState()
    val importResult by viewModel.importResult.collectAsState()
    val exportResult by viewModel.exportResult.collectAsState()

    var showPasswordDialog by remember { mutableStateOf(false) }
    var pendingUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showDeleteDialog by remember { mutableStateOf<CertInfo?>(null) }
    var showDetailSheet by remember { mutableStateOf<CertInfo?>(null) }
    var showExportDialog by remember { mutableStateOf<CertInfo?>(null) }
    var pendingExportBytes by remember { mutableStateOf<ByteArray?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val certExportSuccessMsg = stringResource(R.string.cert_export_success)
    val certImportSuccessTemplate = stringResource(R.string.cert_import_success)
    val certImportFailedTemplate = stringResource(R.string.cert_import_failed)

    val pickP12 = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            pendingUri = it
            showPasswordDialog = true
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-pkcs12")
    ) { uri ->
        uri?.let { saveUri ->
            pendingExportBytes?.let { bytes ->
                runCatching {
                    context.contentResolver.openOutputStream(saveUri)?.use { it.write(bytes) }
                }
                scope.launch {
                    snackbarHostState.showSnackbar(certExportSuccessMsg)
                }
            }
            pendingExportBytes = null
        }
    }

    LaunchedEffect(importResult) {
        importResult?.let { result ->
            val msg = if (result.isSuccess) {
                certImportSuccessTemplate.format(result.getOrNull()?.callSign ?: "")
            } else {
                certImportFailedTemplate.format(
                    result.exceptionOrNull()?.message ?: "Unknown error"
                )
            }
            scope.launch { snackbarHostState.showSnackbar(msg) }
            viewModel.clearImportResult()
        }
    }

    LaunchedEffect(exportResult) {
        exportResult?.let { result ->
            result.getOrNull()?.let { bytes ->
                pendingExportBytes = bytes
                saveLauncher.launch("certificate.p12")
            } ?: run {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        "Export failed: ${result.exceptionOrNull()?.message}"
                    )
                }
            }
            viewModel.clearExportResult()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (certs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_certificates),
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
                    top = 16.dp + contentPadding.calculateTopPadding(),
                    bottom = 16.dp + contentPadding.calculateBottomPadding() + 80.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(certs, key = { it.alias }) { cert ->
                    CertificateCard(
                        cert = cert,
                        onClick = { showDetailSheet = cert },
                        onDelete = { showDeleteDialog = cert }
                    )
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = contentPadding.calculateBottomPadding())
        )
        FloatingActionButton(
            onClick = {
                pickP12.launch(
                    arrayOf(
                        "application/x-pkcs12",
                        "application/octet-stream",
                        "*/*"
                    )
                )
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp + contentPadding.calculateBottomPadding())
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.cert_import)
            )
        }
    }

    if (showPasswordDialog) {
        PasswordInputDialog(
            onDismiss = {
                showPasswordDialog = false
                pendingUri = null
            },
            onConfirm = { password ->
                pendingUri?.let { uri ->
                    viewModel.importP12(uri, password)
                }
                showPasswordDialog = false
                pendingUri = null
            }
        )
    }

    showDeleteDialog?.let { cert ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.cert_delete_title)) },
            text = { Text(stringResource(R.string.cert_delete_message, cert.callSign)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCert(cert.alias)
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

    showDetailSheet?.let { cert ->
        CertDetailSheet(
            cert = cert,
            onDismiss = { showDetailSheet = null },
            onExport = {
                showExportDialog = cert
                showDetailSheet = null
            },
            onDelete = {
                showDeleteDialog = cert
                showDetailSheet = null
            }
        )
    }

    showExportDialog?.let { cert ->
        ExportPasswordDialog(
            onDismiss = { showExportDialog = null },
            onExport = { password ->
                viewModel.exportP12(cert.alias, password)
                showExportDialog = null
            }
        )
    }
}

@Composable
fun CertificateCard(
    cert: CertInfo,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { AppPreferences.getInstance(context).dateFormat }
    Card(modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cert.callSign.ifEmpty { cert.name },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = cert.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (cert.dxccEntity != 0) {
                    Text(
                        text = "${cert.dxccEntity} \u2013 ${cert.dxccEntityName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val qsoRange = when {
                    cert.qsoNotBefore != null && cert.qsoNotAfter != null ->
                        "${dateFormat.formatDate(cert.qsoNotBefore)} \u2013 ${dateFormat.formatDate(cert.qsoNotAfter)}"

                    cert.qsoNotBefore != null -> "From ${dateFormat.formatDate(cert.qsoNotBefore)}"
                    cert.qsoNotAfter != null -> "Until ${dateFormat.formatDate(cert.qsoNotAfter)}"
                    else -> null
                }
                qsoRange?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = cert.statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (cert.isExpired || cert.isQsoExpired) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CertDetailSheet(
    cert: CertInfo,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { AppPreferences.getInstance(context).dateFormat }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Text(
                text = stringResource(R.string.cert_properties),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(12.dp))

            CertDetailRow(stringResource(R.string.callsign), cert.callSign.ifEmpty { "—" })
            CertDetailRow(stringResource(R.string.holder_name), cert.name.ifEmpty { "—" })
            CertDetailRow(
                stringResource(R.string.dxcc_entity),
                if (cert.dxccEntity != 0) "${cert.dxccEntity} \u2013 ${cert.dxccEntityName}"
                else "—"
            )

            val qsoRange = when {
                cert.qsoNotBefore != null && cert.qsoNotAfter != null ->
                    "${dateFormat.formatDate(cert.qsoNotBefore)} \u2013 ${dateFormat.formatDate(cert.qsoNotAfter)}"

                cert.qsoNotBefore != null -> "From ${dateFormat.formatDate(cert.qsoNotBefore)}"
                cert.qsoNotAfter != null -> "Until ${dateFormat.formatDate(cert.qsoNotAfter)}"
                else -> "—"
            }
            CertDetailRow(stringResource(R.string.qso_date_range), qsoRange)

            val validityRange =
                "${cert.notBefore?.let { dateFormat.formatDate(it) } ?: "—"} \u2013 ${cert.notAfter?.let { dateFormat.formatDate(it) } ?: "—"}"
            CertDetailRow(stringResource(R.string.cert_validity), validityRange)
            CertDetailRow(stringResource(R.string.issuer), cert.issuerOrg.ifEmpty { "—" })
            CertDetailRow(stringResource(R.string.serial_number), cert.serialNumber)
            if (cert.email.isNotEmpty()) {
                CertDetailRow(stringResource(R.string.email), cert.email)
            }
            CertDetailRow(
                stringResource(R.string.fingerprint),
                cert.fingerprint.chunked(8).joinToString(" ")
            )
            CertDetailRow(
                stringResource(R.string.status),
                cert.statusLabel,
                valueColor = if (cert.isExpired || cert.isQsoExpired)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.delete)) }
                Button(
                    onClick = onExport,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.export)) }
            }
        }
    }
}

@Composable
fun CertDetailRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor
        )
        HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun PasswordInputDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cert_import_password_title)) },
        text = {
            Column {
                Text(stringResource(R.string.cert_import_password_message))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun ExportPasswordDialog(
    onDismiss: () -> Unit,
    onExport: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val passwordsMatch = password == confirmPassword

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cert_export_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text(stringResource(R.string.confirm_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = confirmPassword.isNotEmpty() && !passwordsMatch,
                    supportingText = if (confirmPassword.isNotEmpty() && !passwordsMatch) {
                        { Text(stringResource(R.string.passwords_donot_match)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onExport(password) },
                enabled = passwordsMatch
            ) { Text(stringResource(R.string.export)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
