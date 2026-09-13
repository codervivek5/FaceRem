package com.facerem.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.facerem.ui.theme.AppTheme

/** The brand gradient used by the splash, auth screens and hero surfaces. */
@Composable
fun brandBrush(): Brush = Brush.linearGradient(
    listOf(AppTheme.colors.gradientStart, AppTheme.colors.gradientEnd),
)

/**
 * Product mark: a face-scan reticle. Drawn rather than shipped as a raster so it stays crisp and
 * so branding can be re-themed without new assets.
 */
@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    contentColor: Color = Color.White,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(brandBrush()),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size * 0.52f)) {
            val stroke = Stroke(width = size.toPx() * 0.045f, cap = StrokeCap.Round)
            val w = this.size.width
            val h = this.size.height
            val corner = w * 0.3f

            // Four reticle corners.
            listOf(
                listOf(0f to corner, 0f to 0f, corner to 0f),
                listOf(w - corner to 0f, w to 0f, w to corner),
                listOf(w to h - corner, w to h, w - corner to h),
                listOf(corner to h, 0f to h, 0f to h - corner),
            ).forEach { points ->
                points.zipWithNext { start, end ->
                    drawLine(
                        color = contentColor,
                        start = androidx.compose.ui.geometry.Offset(start.first, start.second),
                        end = androidx.compose.ui.geometry.Offset(end.first, end.second),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round,
                    )
                }
            }

            // Simplified face inside the reticle.
            drawCircle(
                color = contentColor,
                radius = w * 0.055f,
                center = androidx.compose.ui.geometry.Offset(w * 0.36f, h * 0.42f),
            )
            drawCircle(
                color = contentColor,
                radius = w * 0.055f,
                center = androidx.compose.ui.geometry.Offset(w * 0.64f, h * 0.42f),
            )
            drawArc(
                color = contentColor,
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(w * 0.3f, h * 0.46f),
                size = androidx.compose.ui.geometry.Size(w * 0.4f, h * 0.3f),
                style = stroke,
            )
        }
    }
}
