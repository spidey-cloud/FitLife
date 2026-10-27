package com.fitlife.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val KineticLightScheme = lightColorScheme(
    primary = KineticClarity.Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = KineticClarity.Secondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = Color(0xFF164E63),
    tertiary = KineticClarity.Tertiary,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF064E3B),
    background = KineticClarity.Background,
    onBackground = KineticClarity.Ink,
    surface = KineticClarity.Surface,
    onSurface = KineticClarity.Ink,
    surfaceVariant = KineticClarity.SurfaceLow,
    onSurfaceVariant = KineticClarity.InkMuted,
    surfaceTint = KineticClarity.Primary,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = KineticClarity.SurfaceLow,
    surfaceContainerHigh = Color(0xFFEDF1F7),
    surfaceContainerHighest = KineticClarity.SurfaceHigh,
    inverseSurface = KineticClarity.Ink,
    inverseOnSurface = Color.White,
    inversePrimary = Color(0xFF93C5FD),
    outline = KineticClarity.OutlineStrong,
    outlineVariant = KineticClarity.Outline,
    error = KineticClarity.Error,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    scrim = Color(0x990F172A),
)

private val NightRideDarkScheme = darkColorScheme(
    primary = NightRide.Lime,
    onPrimary = Color(0xFF0A0A0A),
    primaryContainer = Color(0xFF2A3300),
    onPrimaryContainer = Color(0xFFE6FF80),
    secondary = NightRide.Cyan,
    onSecondary = Color(0xFF001A1C),
    secondaryContainer = Color(0xFF00393C),
    onSecondaryContainer = Color(0xFFB3FCFF),
    tertiary = NightRide.Orange,
    onTertiary = Color(0xFF1A0E00),
    tertiaryContainer = Color(0xFF3D2600),
    onTertiaryContainer = Color(0xFFFFD9A8),
    background = NightRide.Background,
    onBackground = NightRide.Ink,
    surface = NightRide.Surface,
    onSurface = NightRide.Ink,
    surfaceVariant = NightRide.SurfaceHigh,
    onSurfaceVariant = NightRide.InkMuted,
    surfaceTint = NightRide.Lime,
    surfaceContainerLowest = NightRide.Background,
    surfaceContainerLow = NightRide.SurfaceLow,
    surfaceContainer = NightRide.Surface,
    surfaceContainerHigh = Color(0xFF1F1F23),
    surfaceContainerHighest = NightRide.SurfaceHigh,
    inverseSurface = NightRide.Ink,
    inverseOnSurface = NightRide.Background,
    inversePrimary = Color(0xFF5C7300),
    outline = NightRide.OutlineStrong,
    outlineVariant = NightRide.Outline,
    error = NightRide.Error,
    onError = Color(0xFF1A0000),
    errorContainer = Color(0xFF3B0D0D),
    onErrorContainer = Color(0xFFFFB4AB),
    scrim = Color(0xCC000000),
)

/** Rounded, friendly shape scale used by both systems. */
val FitShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * FitLife theme.
 *
 * @param themeMode "system" | "light" | "dark" (stored in the profile; editable in Profile).
 * @param forceNightRide always use the dark Night Ride system (used by the ride HUD for
 *        outdoor contrast regardless of the user's theme).
 *
 * Dynamic (wallpaper) colour is intentionally not used — the brand palettes carry meaning
 * (each metric keeps a fixed colour) and were being replaced by wallpaper colours before v2.
 */
@Composable
fun FitLifeTheme(
    themeMode: String = "system",
    forceNightRide: Boolean = false,
    content: @Composable () -> Unit
) {
    val dark = forceNightRide || when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val colorScheme = if (dark) NightRideDarkScheme else KineticLightScheme
    val fitColors = if (dark) NightRideFitColors else KineticFitColors
    val typography = if (dark) NightRideTypography else KineticTypography

    val view = LocalView.current
    if (!view.isInEditMode && !forceNightRide) {
        // The activity is edge-to-edge (enforced on Android 15), so only the icon colour of the
        // system bars needs to follow the in-app theme choice.
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(LocalFitColors provides fitColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = FitShapes,
            content = content
        )
    }
}

/** Convenience accessor: `FitTheme.colors.steps`. */
object FitTheme {
    val colors: FitColors
        @Composable get() = LocalFitColors.current
}
