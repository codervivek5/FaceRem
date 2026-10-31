package com.facerem.feature.attendance.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.facerem.core.camera.FaceCameraPreview
import com.facerem.core.camera.FaceQualityEvaluator
import com.facerem.core.camera.rememberCameraPermissionState
import com.facerem.core.camera.rememberFaceCaptureController
import com.facerem.core.camera.rememberFaceDetector
import com.facerem.core.designsystem.component.AppTopBar
import com.facerem.core.designsystem.component.AttendanceStatusPill
import com.facerem.core.designsystem.component.FaceFramingOverlay
import com.facerem.core.designsystem.component.GuestModeBanner
import com.facerem.core.designsystem.component.PrimaryButton
import com.facerem.core.designsystem.component.SecondaryButton
import com.facerem.core.designsystem.component.UserAvatar
import com.facerem.core.model.AttendanceEventType
import com.facerem.core.model.RecognitionOutcome
import com.facerem.ui.theme.AppTheme
import com.facerem.ui.theme.Spacing
import kotlin.math.roundToInt

@Composable
fun ScanRoute(
    onNavigateUp: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permission by rememberCameraPermissionState()
    val detector = rememberFaceDetector()
    val captureController = rememberFaceCaptureController()

    // A gate tablet must not sleep between visitors.
    val view = LocalView.current
    DisposableEffect(uiState.kioskMode) {
        view.keepScreenOn = uiState.kioskMode
        onDispose { view.keepScreenOn = false }
    }

    // Back would drop an unattended kiosk back into the app. Leaving is a deliberate long press.
    BackHandler(enabled = uiState.kioskMode) { /* swallowed on purpose */ }

    if (!permission.isGranted) {
        Scaffold(topBar = { AppTopBar(title = "Attendance scan", onNavigateUp = onNavigateUp) }) { padding ->
            ScanPermissionPrompt(
                hasBeenRequested = permission.hasBeenRequested,
                onRequest = permission.request,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Spacing.screenHorizontal),
            )
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Camera live feed
        FaceCameraPreview(
            detector = detector,
            pose = FaceQualityEvaluator.HeadPose.FRONT,
            onQuality = { status -> viewModel.onQuality(status, captureController::capture) },
            modifier = Modifier.fillMaxSize(),
            captureController = captureController,
        )

        // Framer Motion spring-animated camera reticle overlay
        FaceFramingOverlay(
            accent = when {
                uiState.phase != ScanPhase.SCANNING -> AppTheme.colors.info
                uiState.quality.isValid -> AppTheme.colors.success
                else -> AppTheme.colors.warning
            },
            isActive = uiState.phase == ScanPhase.SCANNING,
        )

        // Glassmorphism HUD Header
        KioskHeader(
            uiState = uiState,
            onExitKiosk = { viewModel.setKioskMode(false) },
            onEnterKiosk = { viewModel.setKioskMode(true) },
            onNavigateUp = onNavigateUp,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // Glassmorphism HUD Footer
        ScanFooter(
            uiState = uiState,
            onScanAgain = viewModel::scanAgain,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // Bouncy spring result card overlay
        AnimatedVisibility(
            visible = uiState.phase == ScanPhase.RESULT,
            enter = fadeIn(tween(250)) +
                scaleIn(
                    initialScale = 0.85f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) +
                slideInVertically(
                    initialOffsetY = { it / 6 },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ),
            exit = fadeOut(tween(200)),
        ) {
            ResultOverlay(
                outcome = uiState.outcome,
                errorMessage = uiState.errorMessage,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Animated radar wave verifying overlay
        AnimatedVisibility(
            visible = uiState.phase == ScanPhase.VERIFYING,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
        ) {
            VerifyingOverlay()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KioskHeader(
    uiState: ScanUiState,
    onExitKiosk: () -> Unit,
    onEnterKiosk: () -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            if (uiState.kioskMode) {
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(Color.White.copy(alpha = 0.18f))
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                            onLongClick = onExitKiosk,
                        )
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                ) {
                    Text(
                        "Kiosk mode · hold to exit",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.95f),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            } else {
                SecondaryButton("Back", onNavigateUp)
                Spacer(Modifier.width(Spacing.sm))
                SecondaryButton("Kiosk mode", onEnterKiosk)
            }

            Spacer(Modifier.weight(1f))

            if (uiState.scanCount > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f))
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                ) {
                    Text(
                        "${uiState.scanCount} scanned",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        if (uiState.isGuest) {
            Spacer(Modifier.height(Spacing.sm))
            GuestModeBanner()
        }
    }
}

@Composable
private fun ScanFooter(
    uiState: ScanUiState,
    onScanAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f), Color.Black),
                ),
            )
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(
            targetState = uiState.guidance,
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(initialScale = 0.95f)) togetherWith fadeOut(tween(180))
            },
            label = "guidance-text",
        ) { text ->
            if (text.isNotBlank()) {
                Text(
                    text,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(Spacing.xs))
        Text(
            "Recognition runs on your organization's server. Internet is required.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
        )

        if (uiState.showsManualControls) {
            Spacer(Modifier.height(Spacing.lg))
            PrimaryButton(
                text = "Scan next person",
                onClick = onScanAgain,
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.phase == ScanPhase.RESULT,
            )
        }
    }
}

@Composable
private fun VerifyingOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "verifying-radar")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "radar-pulse",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.68f)),
        contentAlignment = Alignment.Center,
    ) {
        // Radar pulse wave
        Canvas(modifier = Modifier.size(180.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.width / 2f
            val currentRadius = maxRadius * pulseProgress
            val alpha = (1f - pulseProgress).coerceIn(0f, 1f)

            drawCircle(
                color = Color(0xFF00E5FF).copy(alpha = alpha * 0.45f),
                radius = currentRadius,
                center = center,
                style = Stroke(width = 3.dp.toPx()),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = Color(0xFF00E5FF),
                    strokeWidth = 3.5.dp,
                    modifier = Modifier.size(64.dp),
                )
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.height(Spacing.lg))
            Text(
                "Matching face profile…",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ResultOverlay(
    outcome: RecognitionOutcome?,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.xl)
                .border(1.dp, Color.White.copy(alpha = 0.2f), MaterialTheme.shapes.extraLarge),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
        ) {
            when {
                outcome is RecognitionOutcome.Recognized -> RecognizedCard(outcome)
                outcome is RecognitionOutcome.NotRecognized -> MessageCard(
                    icon = Icons.Default.ErrorOutline,
                    tint = MaterialTheme.colorScheme.error,
                    title = "Face not recognized",
                    message = outcome.message,
                )

                outcome is RecognitionOutcome.Rejected -> MessageCard(
                    icon = Icons.Default.ErrorOutline,
                    tint = MaterialTheme.colorScheme.error,
                    title = "Scan rejected",
                    message = outcome.message,
                )

                else -> MessageCard(
                    icon = Icons.Default.ErrorOutline,
                    tint = MaterialTheme.colorScheme.error,
                    title = "Scan failed",
                    message = errorMessage ?: "Please try again.",
                )
            }
        }
    }
}

@Composable
private fun RecognizedCard(outcome: RecognitionOutcome.Recognized) {
    val semantic = AppTheme.colors
    val isRepeat = outcome.eventType == AttendanceEventType.ALREADY_PRESENT
    val accent = if (isRepeat) semantic.info else semantic.success

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f))
                .border(2.dp, accent.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isRepeat) Icons.Default.Schedule else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(48.dp),
            )
        }

        Spacer(Modifier.height(Spacing.lg))
        UserAvatar(name = outcome.employeeName, size = 64.dp)
        Spacer(Modifier.height(Spacing.md))

        Text(
            outcome.employeeName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            "${outcome.employeeCode} · ${outcome.department}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Spacing.lg))
        Text(
            outcome.eventType.displayName,
            style = MaterialTheme.typography.headlineSmall,
            color = accent,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = if (isRepeat) repeatDetail(outcome) else "at ${outcome.timeLabel}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(Spacing.md))
        AttendanceStatusPill(outcome.status)

        if (outcome.lateMinutes > 0 && !isRepeat) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "${outcome.lateMinutes} minutes after shift start",
                style = MaterialTheme.typography.bodySmall,
                color = semantic.warning,
            )
        }

        Spacer(Modifier.height(Spacing.md))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        ) {
            Text(
                "Match confidence ${(outcome.confidence * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** "In since 09:05" reads better at a gate than a bare timestamp. */
private fun repeatDetail(outcome: RecognitionOutcome.Recognized): String {
    val checkIn = outcome.checkInLabel
    val checkOut = outcome.checkOutLabel
    return when {
        checkOut != null && checkIn != null -> "In $checkIn · out $checkOut"
        checkIn != null -> "In since $checkIn"
        else -> "Already recorded for today"
    }
}

@Composable
private fun MessageCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    message: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(Spacing.lg))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.sm))
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ScanPermissionPrompt(
    hasBeenRequested: Boolean,
    onRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.CameraAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(Spacing.lg))
        Text("Camera access needed", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.sm))
        Text(
            if (hasBeenRequested) {
                "Camera permission was declined. Enable it in system settings to scan attendance."
            } else {
                "The scanner needs the front camera to detect a face before verification."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.lg))
        PrimaryButton("Allow camera", onRequest, modifier = Modifier.fillMaxWidth())
    }
}
