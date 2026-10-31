package com.facerem.ui.theme

import androidx.compose.ui.graphics.Color

// Brand ramp. Keep product colour decisions here so branding stays configurable.
val BrandBlue = Color(0xFF155EEF)
val BrandBlueDark = Color(0xFF84ADFF)
val SurfaceLight = Color(0xFFF8F9FC)
val SurfaceDark = Color(0xFF101828)
val OnSurfaceLight = Color(0xFF101828)
val OnSurfaceDark = Color(0xFFF9FAFB)

// Light scheme
val LightPrimary = BrandBlue
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFD1E0FF)
val LightOnPrimaryContainer = Color(0xFF00214D)
val LightSecondary = Color(0xFF475467)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFE4E7EC)
val LightOnSecondaryContainer = Color(0xFF1D2939)
val LightTertiary = Color(0xFF6941C6)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFEBE4FF)
val LightOnTertiaryContainer = Color(0xFF2A1364)
val LightError = Color(0xFFD92D20)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFEE4E2)
val LightOnErrorContainer = Color(0xFF7A271A)
val LightSurfaceVariant = Color(0xFFE9EDF5)
val LightOnSurfaceVariant = Color(0xFF475467)
val LightOutline = Color(0xFFD0D5DD)
val LightOutlineVariant = Color(0xFFE4E7EC)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFFCFCFD)
val LightSurfaceContainer = Color(0xFFFFFFFF)
val LightSurfaceContainerHigh = Color(0xFFF2F4F7)
val LightSurfaceContainerHighest = Color(0xFFEAEEF5)
val LightSurfaceDim = Color(0xFFE4E7EC)
val LightSurfaceBright = Color(0xFFFFFFFF)
val LightInverseSurface = Color(0xFF1D2939)
val LightInverseOnSurface = Color(0xFFF2F4F7)
val LightInversePrimary = Color(0xFF84ADFF)
val LightScrim = Color(0xFF000000)

// Dark scheme
val DarkPrimary = BrandBlueDark
val DarkOnPrimary = Color(0xFF002A6B)
val DarkPrimaryContainer = Color(0xFF1849A9)
val DarkOnPrimaryContainer = Color(0xFFD1E0FF)
val DarkSecondary = Color(0xFFB3BAC7)
val DarkOnSecondary = Color(0xFF1D2939)
val DarkSecondaryContainer = Color(0xFF344054)
val DarkOnSecondaryContainer = Color(0xFFE4E7EC)
val DarkTertiary = Color(0xFFCEBDFF)
val DarkOnTertiary = Color(0xFF3A2079)
val DarkTertiaryContainer = Color(0xFF53389E)
val DarkOnTertiaryContainer = Color(0xFFEBE4FF)
val DarkError = Color(0xFFFDA29B)
val DarkOnError = Color(0xFF601410)
val DarkErrorContainer = Color(0xFF912018)
val DarkOnErrorContainer = Color(0xFFFEE4E2)
val DarkSurfaceVariant = Color(0xFF344054)
val DarkOnSurfaceVariant = Color(0xFFB3BAC7)
val DarkOutline = Color(0xFF475467)
val DarkOutlineVariant = Color(0xFF344054)
val DarkSurfaceContainerLowest = Color(0xFF0B111E)
val DarkSurfaceContainerLow = Color(0xFF151C2B)
val DarkSurfaceContainer = Color(0xFF1A2233)
val DarkSurfaceContainerHigh = Color(0xFF222C40)
val DarkSurfaceContainerHighest = Color(0xFF2B364D)
val DarkSurfaceDim = Color(0xFF0B111E)
val DarkSurfaceBright = Color(0xFF2B364D)
val DarkInverseSurface = Color(0xFFF2F4F7)
val DarkInverseOnSurface = Color(0xFF1D2939)
val DarkInversePrimary = Color(0xFF155EEF)
val DarkScrim = Color(0xFF000000)

/** Semantic colours that Material 3 does not model, exposed through [LocalAppColors]. */
data class AppSemanticColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val gradientStart: Color,
    val gradientEnd: Color,
)

val LightSemanticColors = AppSemanticColors(
    success = Color(0xFF079455),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFDCFAE6),
    onSuccessContainer = Color(0xFF074D31),
    warning = Color(0xFFDC6803),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFEF0C7),
    onWarningContainer = Color(0xFF7A2E0E),
    info = Color(0xFF0BA5EC),
    infoContainer = Color(0xFFE0F2FE),
    onInfoContainer = Color(0xFF065986),
    gradientStart = Color(0xFF155EEF),
    gradientEnd = Color(0xFF6941C6),
)

val DarkSemanticColors = AppSemanticColors(
    success = Color(0xFF47CD89),
    onSuccess = Color(0xFF06331F),
    successContainer = Color(0xFF074D31),
    onSuccessContainer = Color(0xFFDCFAE6),
    warning = Color(0xFFFDB022),
    onWarning = Color(0xFF4E1D09),
    warningContainer = Color(0xFF7A2E0E),
    onWarningContainer = Color(0xFFFEF0C7),
    info = Color(0xFF36BFFA),
    infoContainer = Color(0xFF065986),
    onInfoContainer = Color(0xFFE0F2FE),
    gradientStart = Color(0xFF1849A9),
    gradientEnd = Color(0xFF53389E),
)


// ---------------------------------------------------------------------------
// Liquid glass
// ---------------------------------------------------------------------------
// A near-white base with three wide colour blobs. Kept pale on purpose: panels sit on it
// at ~72% opacity, so a saturated backdrop would bleed through and hurt text contrast.
val GlassBase = Color(0xFFF2F5FC)
val GlassBlobBlue = Color(0xFF9CC0FF)
val GlassBlobViolet = Color(0xFFCDB8FF)
val GlassBlobTeal = Color(0xFF9FE3E0)
