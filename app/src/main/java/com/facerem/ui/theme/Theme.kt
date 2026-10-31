package com.facerem.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    surfaceDim = DarkSurfaceDim,
    surfaceBright = DarkSurfaceBright,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    inversePrimary = DarkInversePrimary,
    scrim = DarkScrim,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    surfaceDim = LightSurfaceDim,
    surfaceBright = LightSurfaceBright,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    inversePrimary = LightInversePrimary,
    scrim = LightScrim,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
)

val LocalAppColors = staticCompositionLocalOf { LightSemanticColors }

/** Semantic colours (success/warning/info/brand gradient) that sit alongside [MaterialTheme]. */
object AppTheme {
    val colors: AppSemanticColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current
}

/**
 * Light scheme with translucent surfaces.
 *
 * Making the surface *roles* translucent rather than restyling each card means every
 * existing `Card` and `Surface` becomes glass without touching a single screen — they all
 * draw from `surface` / `surfaceContainer` already.
 */
private val GlassColorScheme = LightColorScheme.copy(
    // Transparent, so GlassBackdrop shows through instead of being painted over.
    background = Color.Transparent,
    surface = Color.White.copy(alpha = 0.72f),
    surfaceContainerLowest = Color.White.copy(alpha = 0.60f),
    surfaceContainerLow = Color.White.copy(alpha = 0.66f),
    surfaceContainer = Color.White.copy(alpha = 0.72f),
    surfaceContainerHigh = Color.White.copy(alpha = 0.80f),
    surfaceContainerHighest = Color.White.copy(alpha = 0.86f),
    surfaceVariant = Color.White.copy(alpha = 0.55f),
    outline = Color.White.copy(alpha = 0.70f),
    outlineVariant = Color.White.copy(alpha = 0.50f),
)

@Composable
fun FaceAttendTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    glass: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        glass -> GlassColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val semanticColors = if (darkTheme && !glass) DarkSemanticColors else LightSemanticColors

    CompositionLocalProvider(
        LocalAppColors provides semanticColors,
        LocalGlass provides if (glass) LiquidGlass else OpaqueGlass,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content,
        )
    }
}
