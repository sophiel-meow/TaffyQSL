package moe.zzy040330.taffyqsl.ui.settings

import android.annotation.SuppressLint
import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import moe.zzy040330.taffyqsl.BuildConfig
import moe.zzy040330.taffyqsl.R
import moe.zzy040330.taffyqsl.data.AppLanguage
import moe.zzy040330.taffyqsl.data.AppPreferences
import moe.zzy040330.taffyqsl.data.DateFormatOption
import moe.zzy040330.taffyqsl.data.lotw.LotwCredentialManager

@Composable
fun SettingsScreen(innerPadding: PaddingValues, navController: NavController) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences.getInstance(context) }
    val credentialManager = remember { LotwCredentialManager(context) }

    var debugMode by remember {
        mutableStateOf(if (BuildConfig.DEBUG) prefs.isDebugMode else false)
    }
    var dateFormat by remember { mutableStateOf(prefs.dateFormat) }
    var dateFormatExpanded by remember { mutableStateOf(false) }
    val supportsPerAppLanguage = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    var languageExpanded by remember { mutableStateOf(false) }
    var currentLanguage by remember { mutableStateOf(readCurrentAppLanguage(context)) }
    var useLocalTime by remember { mutableStateOf(prefs.useLocalTime) }
    var credUsername by remember {
        mutableStateOf(credentialManager.loadCredentials()?.first ?: "")
    }
    var showLotwDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            SettingsSectionHeader(stringResource(R.string.settings_section_general))
        }

        item {
            SettingsItemWithSwitch(
                icon = Icons.Default.Schedule,
                title = stringResource(R.string.use_local_time),
                subtitle = stringResource(R.string.use_local_time_desc),
                checked = useLocalTime,
                onCheckedChange = { enabled ->
                    useLocalTime = enabled
                    prefs.useLocalTime = enabled
                }
            )
        }

        item {
            Box {
                SettingsItem(
                    icon = Icons.Default.CalendarToday,
                    title = stringResource(R.string.date_format),
                    subtitle = dateFormat.preview(),
                    onClick = { dateFormatExpanded = true }
                )
                DropdownMenu(
                    expanded = dateFormatExpanded,
                    onDismissRequest = { dateFormatExpanded = false }
                ) {
                    DateFormatOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text("${option.longPattern}  (${option.preview()})") },
                            onClick = {
                                dateFormat = option
                                prefs.dateFormat = option
                                dateFormatExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // TODO: dark & light theme

        item {
            val languageNames = mapOf(
                AppLanguage.SYSTEM to stringResource(R.string.language_follow_system),
                AppLanguage.ENGLISH to "English",
                AppLanguage.CHINESE_SIMPLIFIED to "中文 (简体)"
            )
            Box {
                SettingsItem(
                    icon = Icons.Default.Language,
                    title = stringResource(R.string.settings_language),
                    subtitle = if (!supportsPerAppLanguage) {
                        stringResource(R.string.language_requires_android_13)
                    } else {
                        languageNames[currentLanguage] ?: ""
                    },
                    enabled = supportsPerAppLanguage,
                    onClick = { languageExpanded = true }
                )
                if (supportsPerAppLanguage) {
                    DropdownMenu(
                        expanded = languageExpanded,
                        onDismissRequest = { languageExpanded = false }
                    ) {
                        AppLanguage.entries.forEach { lang ->
                            DropdownMenuItem(
                                text = { Text(languageNames[lang] ?: "") },
                                onClick = {
                                    currentLanguage = lang
                                    languageExpanded = false
                                    applyAppLanguage(context, lang)
                                }
                            )
                        }
                    }
                }
            }
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        item {
            SettingsSectionHeader(stringResource(R.string.settings_section_lotw))
        }
        item {
            SettingsItem(
                icon = Icons.Default.VpnKey,
                title = stringResource(R.string.lotw_credentials),
                subtitle = if (credUsername.isNotEmpty()) {
                    stringResource(R.string.lotw_logged_in_as, credUsername)
                } else {
                    stringResource(R.string.lotw_credentials_desc)
                },
                onClick = { showLotwDialog = true }
            )
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }


        // TODO: backup & restore
        // TODO: pin & biometric


        item {
            SettingsSectionHeader(stringResource(R.string.settings_section_about))
        }
        item {
            SettingsItem(
                icon = Icons.Default.Info,
                title = stringResource(R.string.about),
                subtitle = stringResource(R.string.about_subtitle),
                onClick = { navController.navigate("about") }
            )
        }
        item {
            SettingsItem(
                icon = Icons.Default.Description,
                title = stringResource(R.string.open_source_licenses),
                subtitle = stringResource(R.string.licenses_subtitle),
                onClick = { navController.navigate("licenses") }
            )
        }

        if (BuildConfig.DEBUG) {
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_developer))
            }

            // TODO: encryption / key managements

            item {
                SettingsItemWithSwitch(
                    icon = Icons.Default.BugReport,
                    title = stringResource(R.string.debug_mode),
                    subtitle = stringResource(R.string.debug_mode_desc),
                    checked = debugMode,
                    onCheckedChange = { enabled ->
                        debugMode = enabled
                        prefs.isDebugMode = enabled
                    }
                )
            }
        }
    }

    if (showLotwDialog) {
        LotwCredentialsDialog(
            credentialManager = credentialManager,
            hasCredentials = credUsername.isNotEmpty(),
            onDismiss = { showLotwDialog = false },
            onSaved = { username ->
                credUsername = username
                showLotwDialog = false
            },
            onCleared = {
                credUsername = ""
                showLotwDialog = false
            }
        )
    }
}

@Composable
private fun LotwCredentialsDialog(
    credentialManager: LotwCredentialManager,
    hasCredentials: Boolean,
    onDismiss: () -> Unit,
    onSaved: (username: String) -> Unit,
    onCleared: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var usernameError by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lotw_credentials_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; usernameError = false },
                    label = { Text(stringResource(R.string.lotw_username)) },
                    isError = usernameError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; passwordError = false },
                    label = { Text(stringResource(R.string.password)) },
                    isError = passwordError,
                    visualTransformation = if (showPassword) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (username.isBlank()) { usernameError = true; return@TextButton }
                if (password.isBlank()) { passwordError = true; return@TextButton }
                credentialManager.saveCredentials(username, password)
                onSaved(username)
            }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row {
                if (hasCredentials) {
                    TextButton(onClick = {
                        credentialManager.clearCredentials()
                        onCleared()
                    }) {
                        Text(stringResource(R.string.lotw_credentials_clear))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    ListItem(
        modifier = Modifier
            .then(if (!enabled) Modifier.alpha(0.38f) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}

/** Returns the currently active per-app language, or SYSTEM if not set / API < 33. */
private fun readCurrentAppLanguage(context: Context): AppLanguage {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return AppLanguage.SYSTEM
    val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
    if (locales.isEmpty) return AppLanguage.SYSTEM
    val tag = locales[0].toLanguageTag()
    return AppLanguage.entries.find { it.tag == tag } ?: AppLanguage.SYSTEM
}

/** Applies the selected language via LocaleManager (API 33+). No-op on older APIs. */
private fun applyAppLanguage(context: Context, lang: AppLanguage) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    context.getSystemService(LocaleManager::class.java).applicationLocales =
        if (lang.tag.isEmpty()) LocaleList.getEmptyLocaleList()
        else LocaleList.forLanguageTags(lang.tag)
}

@Composable
private fun SettingsItemWithSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        modifier = Modifier.clickable { onCheckedChange(!checked) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    )
}
