package moe.zzy040330.taffyqsl.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import moe.zzy040330.taffyqsl.BuildConfig
import moe.zzy040330.taffyqsl.R
import moe.zzy040330.taffyqsl.data.AppPreferences

@Composable
fun SettingsScreen(innerPadding: PaddingValues, navController: NavController) {
    var offlineMode by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var debugMode by remember {
        mutableStateOf(if (BuildConfig.DEBUG) AppPreferences.getInstance(context).isDebugMode else false)
    }

    // TODO: dummy items here
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
                icon = Icons.Default.CloudOff,
                title = stringResource(R.string.offline_mode),
                subtitle = stringResource(R.string.offline_mode_desc),
                checked = offlineMode,
                onCheckedChange = { offlineMode = it }
            )
        }
        item {
            SettingsItem(
                icon = Icons.Default.CalendarToday,
                title = stringResource(R.string.date_format),
                subtitle = stringResource(R.string.date_format_desc),
                onClick = { /* TODO: Open date format dialog */ }
            )
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
                subtitle = stringResource(R.string.lotw_credentials_desc),
                onClick = { /* TODO: Open LoTW credentials dialog */ }
            )
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        item {
            SettingsSectionHeader(stringResource(R.string.settings_section_storage))
        }
        item {
            SettingsItem(
                icon = Icons.Default.Delete,
                title = stringResource(R.string.clear_cache),
                subtitle = stringResource(R.string.clear_cache_desc),
                onClick = { /* TODO: Clear cache */ }
            )
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

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
            item {
                SettingsItemWithSwitch(
                    icon = Icons.Default.BugReport,
                    title = stringResource(R.string.debug_mode),
                    subtitle = stringResource(R.string.debug_mode_desc),
                    checked = debugMode,
                    onCheckedChange = { enabled ->
                        debugMode = enabled
                        AppPreferences.getInstance(context).isDebugMode = enabled
                    }
                )
            }
        }
    }
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
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
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
