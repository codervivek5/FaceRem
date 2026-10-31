package com.facerem.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Tokens for the liquid-glass look.
 *
 * Android has no backdrop blur that works below API 31, so the frosting is achieved the
 * other way round: the backdrop itself is made of very soft colour blobs, and panels sit on
 * it at partial opacity with a bright edge. What shows through is already diffuse, so the
 * panel reads as frosted without blurring anything.
 */
data class GlassTokens(
    val enabled: Boolean,
    /** Panel fill. Opaque enough to keep text legible over a coloured backdrop. */
    val panel: Color,
    /** For surfaces that sit on top of another panel and need to separate from it. */
    val panelRaised: Color,
    /** The lit edge that makes a pane look like it has thickness. */
    val edge: Color,
    /**
     * For panes the page scrolls *behind*, such as the floating nav bar. Much denser than
     * [panel]: at card opacity the text underneath shows through and collides with the
     * labels on top.
     */
    val overlay: Color,
    /** Reads as a gap rather than a fill, so a button can separate from the bar below it. */
    val cutout: Color,
)

val OpaqueGlass = GlassTokens(
    enabled = false,
    panel = Color.Unspecified,
    panelRaised = Color.Unspecified,
    edge = Color.Unspecified,
    overlay = Color.Unspecified,
    cutout = Color.Unspecified,
)

val LiquidGlass = GlassTokens(
    enabled = true,
    panel = Color.White.copy(alpha = 0.72f),
    panelRaised = Color.White.copy(alpha = 0.86f),
    edge = Color.White.copy(alpha = 0.65f),
    overlay = Color.White.copy(alpha = 0.93f),
    cutout = Color.White.copy(alpha = 0.30f),
)

val LocalGlass = staticCompositionLocalOf { OpaqueGlass }

object Glass {
    val tokens: GlassTokens
        @Composable @ReadOnlyComposable get() = LocalGlass.current

    val isActive: Boolean
        @Composable @ReadOnlyComposable get() = LocalGlass.current.enabled
}

/**
 * The colour wash the whole app sits on.
 *
 * Drawn once behind everything rather than per screen, so panels at partial opacity always
 * have the same thing showing through them.
 */
@Composable
fun GlassBackdrop(content: @Composable () -> Unit) {
    if (!Glass.isActive) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            content()
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GlassBase)
            .drawBehind {
                // Wide, low-opacity radial blobs. Their softness is what the panels frost.
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(GlassBlobBlue.copy(alpha = 0.55f), Color.Transparent),
                        center = Offset(size.width * 0.12f, size.height * 0.08f),
                        radius = size.minDimension * 1.05f,
                    ),
                    radius = size.minDimension * 1.05f,
                    center = Offset(size.width * 0.12f, size.height * 0.08f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(GlassBlobViolet.copy(alpha = 0.5f), Color.Transparent),
                        center = Offset(size.width * 0.95f, size.height * 0.3f),
                        radius = size.minDimension * 0.95f,
                    ),
                    radius = size.minDimension * 0.95f,
                    center = Offset(size.width * 0.95f, size.height * 0.3f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(GlassBlobTeal.copy(alpha = 0.42f), Color.Transparent),
                        center = Offset(size.width * 0.2f, size.height * 0.92f),
                        radius = size.minDimension * 0.9f,
                    ),
                    radius = size.minDimension * 0.9f,
                    center = Offset(size.width * 0.2f, size.height * 0.92f),
                )
            },
    ) {
        content()
    }
}

/** Panel fill plus its lit edge. A no-op when glass is off, so call sites stay unconditional. */
@Composable
fun Modifier.glassEdge(radius: androidx.compose.ui.unit.Dp = 20.dp): Modifier {
    val tokens = Glass.tokens
    return if (!tokens.enabled) this
    else this.then(
        Modifier.background(
            brush = Brush.verticalGradient(
                listOf(tokens.edge.copy(alpha = 0.35f), Color.Transparent),
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(radius),
        ),
    )
}
