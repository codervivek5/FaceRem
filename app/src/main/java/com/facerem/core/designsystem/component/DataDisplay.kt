package com.facerem.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.facerem.core.model.DailyRate
import com.facerem.ui.theme.AppTheme
import com.facerem.ui.theme.Spacing
import kotlin.math.roundToInt

/** Lightweight column chart. Values are ratios in `0f..1f`. */
@Composable
fun WeeklyRateChart(
    rates: List<DailyRate>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.Bottom,
    ) {
        rates.forEach { rate ->
            val animated by animateFloatAsState(
                targetValue = rate.rate.coerceIn(0f, 1f),
                animationSpec = tween(durationMillis = 600),
                label = "bar-${rate.dayLabel}",
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    "${(rate.rate * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.xs))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(animated)
                            .clip(MaterialTheme.shapes.small)
                            .background(barColor),
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    rate.dayLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Horizontal ratio bar with a leading label and trailing percentage. */
@Composable
fun RateBar(
    label: String,
    rate: Float,
    modifier: Modifier = Modifier,
    caption: String? = null,
    barColor: Color = MaterialTheme.colorScheme.primary,
) {
    val animated by animateFloatAsState(
        targetValue = rate.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "rate-$label",
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("${(rate * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
        }
        if (caption != null) {
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .height(8.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(barColor),
            )
        }
    }
}

/**
 * Persistent, unmissable marker that the session is a local demo. Guest sessions never reach the
 * backend, so the UI must say so rather than implying the data is real.
 */
@Composable
fun GuestModeBanner(
    modifier: Modifier = Modifier,
    onExit: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(AppTheme.colors.warningContainer)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Info,
            contentDescription = null,
            tint = AppTheme.colors.onWarningContainer,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = "Guest mode — sample data stored only on this device",
            style = MaterialTheme.typography.bodySmall,
            color = AppTheme.colors.onWarningContainer,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f),
        )
        if (onExit != null) {
            TextButton(onClick = onExit) {
                Text("Exit", style = MaterialTheme.typography.labelMedium, color = AppTheme.colors.onWarningContainer)
            }
        }
    }
}
