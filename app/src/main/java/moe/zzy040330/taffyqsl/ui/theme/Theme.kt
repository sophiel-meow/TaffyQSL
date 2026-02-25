package moe.zzy040330.taffyqsl.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Light mode
private val LightColorScheme = lightColorScheme(
    primary = LatteMauve,
    onPrimary = LatteBase,
    primaryContainer = LattePrimaryContainer,
    onPrimaryContainer = LatteOnPrimaryContainer,

    secondary = LatteSapphire,
    onSecondary = LatteBase,
    secondaryContainer = LatteSecondaryContainer,
    onSecondaryContainer = LatteOnSecondaryContainer,

    tertiary = LattePink,
    onTertiary = LatteOnTertiaryContainer,
    tertiaryContainer = LatteTertiaryContainer,
    onTertiaryContainer = LatteOnTertiaryContainer,

    error = LatteRed,
    onError = LatteBase,
    errorContainer = LatteErrorContainer,
    onErrorContainer = LatteOnErrorContainer,

    background = LatteBase,
    onBackground = LatteText,

    surface = LatteBase,
    onSurface = LatteText,
    surfaceVariant = LatteSurface0,
    onSurfaceVariant = LatteSubtext0,
    surfaceTint = LatteMauve,

    surfaceBright = LatteBase,
    surfaceDim = LatteCrust,
    surfaceContainerLowest = LatteBase,
    surfaceContainerLow = LatteMantle,
    surfaceContainer = LatteMantle,
    surfaceContainerHigh = LatteCrust,
    surfaceContainerHighest = LatteSurface0,

    outline = LatteSubtext0,
    outlineVariant = LatteOverlay2,
    scrim = Color.Black,

    inverseSurface = LatteText,
    inverseOnSurface = LatteBase,
    inversePrimary = LatteLavender,
)

// Dark mode
private val DarkColorScheme = darkColorScheme(
    primary = MochaMauve,
    onPrimary = MochaBase,
    primaryContainer = MochaPrimaryContainer,
    onPrimaryContainer = MochaOnPrimaryContainer,

    secondary = MochaBlue,
    onSecondary = MochaBase,
    secondaryContainer = MochaSecondaryContainer,
    onSecondaryContainer = MochaOnSecondaryContainer,

    tertiary = MochaPink,
    onTertiary = MochaBase,
    tertiaryContainer = MochaTertiaryContainer,
    onTertiaryContainer = MochaOnTertiaryContainer,

    error = MochaRed,
    onError = MochaBase,
    errorContainer = MochaErrorContainer,
    onErrorContainer = MochaOnErrorContainer,

    background = MochaBase,
    onBackground = MochaText,

    surface = MochaBase,
    onSurface = MochaText,
    surfaceVariant = MochaSurface0,
    onSurfaceVariant = MochaSubtext1,
    surfaceTint = MochaMauve,

    surfaceBright = MochaSurface0,
    surfaceDim = MochaMantle,
    surfaceContainerLowest = MochaCrust,
    surfaceContainerLow = MochaSurface0,
    surfaceContainer = MochaSurface0,
    surfaceContainerHigh = MochaSurface1,
    surfaceContainerHighest = MochaSurface2,

    outline = MochaOverlay2,
    outlineVariant = MochaOverlay1,
    scrim = Color.Black,

    inverseSurface = MochaText,
    inverseOnSurface = MochaBase,
    inversePrimary = LatteMauve,
)

@Composable
fun TaffyQslTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
