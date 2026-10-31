package com.facerem.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * High-tech Framer Motion style face framing camera overlay.
 *
 * Features:
 * - Smooth spring-animated corner target brackets that scale/lock on active state.
 * - Multi-layer glowing aura ring around the face framing oval.
 * - Futuristic laser beam sweep with trailing vertical gradient glow.
 * - Subtle crosshair reticle indicators at key alignment points.
 * - Smooth color state transitions using spring animation physics.
 */
@Composable
fun FaceFramingOverlay(
    accent: Color,
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
) {
    // Smoothly animate accent color changes across scanner phases
    val animatedAccent by animateColorAsState(
        targetValue = accent,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "overlay-color",
    )

    // Bouncy spring animation for corner bracket target expansion/lock-in
    val bracketScale by animateFloatAsState(
        targetValue = if (isActive) 1f else 1.15f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "bracket-scale",
    )

    // Continuous laser scan sweep animation
    val infiniteTransition = rememberInfiniteTransition(label = "scan-loop")
    val sweepProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "laser-sweep",
    )

    // Subtle breathing pulse for corner reticles and reticle glow
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "reticle-pulse",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val ovalWidth = size.width * 0.72f
        val ovalHeight = ovalWidth * 1.32f
        val topLeft = Offset((size.width - ovalWidth) / 2f, (size.height - ovalHeight) / 2f)
        val ovalSize = Size(ovalWidth, ovalHeight)
        val center = Offset(size.width / 2f, size.height / 2f)

        // 1. Dark background scrim with oval cutout
        val scrimPath = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(Offset.Zero, size))
            addOval(Rect(topLeft, ovalSize))
        }
        drawPath(path = scrimPath, color = Color.Black.copy(alpha = 0.62f))

        // 2. Multi-layer ambient outer aura glow
        drawOuterGlow(
            topLeft = topLeft,
            ovalSize = ovalSize,
            color = animatedAccent,
        )

        // 3. Core oval border framing path
        drawOval(
            color = animatedAccent,
            topLeft = topLeft,
            size = ovalSize,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )

        // 4. Cardinal alignment ticks / crosshairs
        drawCardinalTicks(
            center = center,
            ovalWidth = ovalWidth,
            ovalHeight = ovalHeight,
            color = animatedAccent.copy(alpha = pulseAlpha),
        )

        // 5. Spring-animated futuristic corner target brackets
        drawCornerBrackets(
            topLeft = topLeft,
            ovalSize = ovalSize,
            bracketScale = bracketScale,
            color = animatedAccent,
        )

        // 6. Glowing laser scan beam with gradient trail
        if (isActive) {
            drawLaserBeam(
                topLeft = topLeft,
                ovalWidth = ovalWidth,
                ovalHeight = ovalHeight,
                sweep = sweepProgress,
                color = animatedAccent,
            )
        }
    }
}

private fun DrawScope.drawOuterGlow(
    topLeft: Offset,
    ovalSize: Size,
    color: Color,
) {
    // Stacked soft stroke layers simulating radial bloom around the face oval
    drawOval(
        color = color.copy(alpha = 0.08f),
        topLeft = Offset(topLeft.x - 16.dp.toPx(), topLeft.y - 16.dp.toPx()),
        size = Size(ovalSize.width + 32.dp.toPx(), ovalSize.height + 32.dp.toPx()),
        style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round),
    )
    drawOval(
        color = color.copy(alpha = 0.18f),
        topLeft = Offset(topLeft.x - 8.dp.toPx(), topLeft.y - 8.dp.toPx()),
        size = Size(ovalSize.width + 16.dp.toPx(), ovalSize.height + 16.dp.toPx()),
        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawCardinalTicks(
    center: Offset,
    ovalWidth: Float,
    ovalHeight: Float,
    color: Color,
) {
    val tickLength = 12.dp.toPx()
    val strokePx = 2.dp.toPx()

    // Top tick
    drawLine(
        color = color,
        start = Offset(center.x, center.y - ovalHeight / 2f - tickLength),
        end = Offset(center.x, center.y - ovalHeight / 2f + 4.dp.toPx()),
        strokeWidth = strokePx,
        cap = StrokeCap.Round,
    )
    // Bottom tick
    drawLine(
        color = color,
        start = Offset(center.x, center.y + ovalHeight / 2f - 4.dp.toPx()),
        end = Offset(center.x, center.y + ovalHeight / 2f + tickLength),
        strokeWidth = strokePx,
        cap = StrokeCap.Round,
    )
    // Left tick
    drawLine(
        color = color,
        start = Offset(center.x - ovalWidth / 2f - tickLength, center.y),
        end = Offset(center.x - ovalWidth / 2f + 4.dp.toPx(), center.y),
        strokeWidth = strokePx,
        cap = StrokeCap.Round,
    )
    // Right tick
    drawLine(
        color = color,
        start = Offset(center.x + ovalWidth / 2f - 4.dp.toPx(), center.y),
        end = Offset(center.x + ovalWidth / 2f + tickLength, center.y),
        strokeWidth = strokePx,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawCornerBrackets(
    topLeft: Offset,
    ovalSize: Size,
    bracketScale: Float,
    color: Color,
) {
    val armLength = 28.dp.toPx()
    val strokeWidth = 3.5f.dp.toPx()
    val gap = 12.dp.toPx() * bracketScale

    val left = topLeft.x - gap
    val right = topLeft.x + ovalSize.width + gap
    val top = topLeft.y - gap
    val bottom = topLeft.y + ovalSize.height + gap

    // Top-Left bracket
    drawLine(color, Offset(left, top), Offset(left + armLength, top), strokeWidth, StrokeCap.Round)
    drawLine(color, Offset(left, top), Offset(left, top + armLength), strokeWidth, StrokeCap.Round)

    // Top-Right bracket
    drawLine(color, Offset(right, top), Offset(right - armLength, top), strokeWidth, StrokeCap.Round)
    drawLine(color, Offset(right, top), Offset(right, top + armLength), strokeWidth, StrokeCap.Round)

    // Bottom-Left bracket
    drawLine(color, Offset(left, bottom), Offset(left + armLength, bottom), strokeWidth, StrokeCap.Round)
    drawLine(color, Offset(left, bottom), Offset(left, bottom - armLength), strokeWidth, StrokeCap.Round)

    // Bottom-Right bracket
    drawLine(color, Offset(right, bottom), Offset(right - armLength, bottom), strokeWidth, StrokeCap.Round)
    drawLine(color, Offset(right, bottom), Offset(right, bottom - armLength), strokeWidth, StrokeCap.Round)
}

private fun DrawScope.drawLaserBeam(
    topLeft: Offset,
    ovalWidth: Float,
    ovalHeight: Float,
    sweep: Float,
    color: Color,
) {
    val laserY = topLeft.y + ovalHeight * sweep
    val startX = topLeft.x + ovalWidth * 0.08f
    val endX = topLeft.x + ovalWidth * 0.92f
    val trailHeight = 40.dp.toPx()

    // Vertical trailing gradient beam tail
    val trailRect = Rect(
        left = startX,
        top = (laserY - trailHeight).coerceAtLeast(topLeft.y),
        right = endX,
        bottom = laserY,
    )
    val trailBrush = Brush.verticalGradient(
        colors = listOf(Color.Transparent, color.copy(alpha = 0.25f)),
        startY = trailRect.top,
        endY = trailRect.bottom,
    )
    drawRect(brush = trailBrush, topLeft = Offset(trailRect.left, trailRect.top), size = trailRect.size)

    // Glowing core laser beam line
    drawLine(
        color = color.copy(alpha = 0.4f),
        start = Offset(startX, laserY),
        end = Offset(endX, laserY),
        strokeWidth = 6.dp.toPx(),
        cap = StrokeCap.Round,
    )
    drawLine(
        color = Color.White,
        start = Offset(startX + 12.dp.toPx(), laserY),
        end = Offset(endX - 12.dp.toPx(), laserY),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round,
    )
}
