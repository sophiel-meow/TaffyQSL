package moe.zzy040330.taffyqsl.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import moe.zzy040330.taffyqsl.R

data class LibraryLicense(
    val name: String,
    val license: String,
    val url: String
)

private val libraries = listOf(
    LibraryLicense(
        name = "Kotlin",
        license = "Apache License 2.0",
        url = "https://kotlinlang.org"
    ),
    LibraryLicense(
        name = "Jetpack Compose",
        license = "Apache License 2.0",
        url = "https://developer.android.com/jetpack/compose"
    ),
    LibraryLicense(
        name = "Material Design 3",
        license = "Apache License 2.0",
        url = "https://m3.material.io"
    ),
    LibraryLicense(
        name = "Room Database",
        license = "Apache License 2.0",
        url = "https://developer.android.com/training/data-storage/room"
    ),
    LibraryLicense(
        name = "Kotlin Coroutines",
        license = "Apache License 2.0",
        url = "https://kotlinlang.org/docs/coroutines-overview.html"
    ),
    LibraryLicense(
        name = "OkHttp",
        license = "Apache License 2.0",
        url = "https://square.github.io/okhttp/"
    ),
    LibraryLicense(
        name = "AndroidX Navigation",
        license = "Apache License 2.0",
        url = "https://developer.android.com/guide/navigation"
    ),
    LibraryLicense(
        name = "AndroidX Lifecycle",
        license = "Apache License 2.0",
        url = "https://developer.android.com/topic/libraries/architecture/lifecycle"
    ),
    LibraryLicense(
        name = "AndroidX Core KTX",
        license = "Apache License 2.0",
        url = "https://developer.android.com/kotlin/ktx"
    ),
    LibraryLicense(
        name = "Catppuccin Color Palette",
        license = "MIT License",
        url = "https://github.com/catppuccin/catppuccin"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.licenses_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.licenses_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            items(libraries) { library ->
                LicenseCard(library)
            }
        }
    }
}

@Composable
private fun LicenseCard(library: LibraryLicense) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = library.name,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = library.license,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = library.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
