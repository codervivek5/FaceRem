package com.facerem.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Scrim with an oval cut-out that tells the user exactly where to put their face, plus a scan
 * line while a frame is being validated.
 */
@Composable
fun FaceFramingOverlay(
    accent: Color,
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "scan")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scan-sweep",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val ovalWidth = size.width * 0.72f
        val ovalHeight = ovalWidth * 1.32f
        val topLeft = Offset((size.width - ovalWidth) / 2f, (size.height - ovalHeight) / 2f)
        val ovalSize = Size(ovalWidth, ovalHeight)

        drawRect(color = Color.Black.copy(alpha = 0.55f))
        drawOval(color = Color.Transparent, topLeft = topLeft, size = ovalSize, blendMode = BlendMode.Clear)
        drawOval(
            color = accent,
            topLeft = topLeft,
            size = ovalSize,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
        )

        if (isActive) {
            val y = topLeft.y + ovalHeight * sweep
            drawLine(
                color = accent.copy(alpha = 0.7f),
                start = Offset(topLeft.x + ovalWidth * 0.12f, y),
                end = Offset(topLeft.x + ovalWidth * 0.88f, y),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}
