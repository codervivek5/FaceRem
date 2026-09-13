package com.facerem.feature.attendance.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.facerem.core.camera.FaceCameraPreview
import com.facerem.core.camera.FaceQualityEvaluator
import com.facerem.core.camera.rememberCameraPermissionState
import com.facerem.core.camera.rememberFaceDetector
import com.facerem.core.designsystem.component.AppTopBar
import com.facerem.core.designsystem.component.AttendanceStatusPill
import com.facerem.core.designsystem.component.FaceFramingOverlay
import com.facerem.core.designsystem.component.GuestModeBanner
import com.facerem.core.designsystem.component.PrimaryButton
import com.facerem.core.designsystem.component.UserAvatar
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
    val semantic = AppTheme.colors

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Attendance scan",
                subtitle = "Face verification runs on your organization's server",
                onNavigateUp = onNavigateUp,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.screenHorizontal),
        ) {
            if (uiState.isGuest) {
                GuestModeBanner()
                Spacer(Modifier.height(Spacing.md))
            }

            if (!permission.isGranted) {
                ScanPermissionPrompt(
                    hasBeenRequested = permission.hasBeenRequested,
                    onRequest = permission.request,
                )
                return@Column
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(Color.Black),
            ) {
                if (uiState.isCameraActive) {
                    FaceCameraPreview(
                        detector = detector,
                        pose = FaceQualityEvaluator.HeadPose.FRONT,
                        onQuality = viewModel::onQuality,
                        modifier = Modifier.fillMaxSize(),
                    )
                    FaceFramingOverlay(
                        accent = if (uiState.quality.isValid) semantic.success else semantic.warning,
                    )
                }

                when (uiState.phase) {
                    ScanPhase.VERIFYING -> VerifyingOverlay()
                    ScanPhase.RESULT, ScanPhase.COOLDOWN -> uiState.outcome?.let { outcome ->
                        ResultOverlay(outcome = outcome)
                    } ?: uiState.errorMessage?.let { message ->
                        MessageOverlay(title = "Scan failed", message = message, isError = true)
                    }

                    ScanPhase.SCANNING -> Unit
                }
            }

            Spacer(Modifier.height(Spacing.lg))
            Text(
                when (uiState.phase) {
                    ScanPhase.SCANNING -> uiState.quality.guidance
                    ScanPhase.VERIFYING -> "Verifying with the server…"
                    ScanPhase.RESULT -> "Attendance recorded"
                    ScanPhase.COOLDOWN -> "Ready for the next person"
                },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Internet is required. Attendance is never recorded on the device alone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.lg))
            PrimaryButton(
                text = "Scan next person",
                onClick = viewModel::scanAgain,
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.phase == ScanPhase.COOLDOWN || uiState.phase == ScanPhase.RESULT,
            )
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun VerifyingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
            Spacer(Modifier.height(Spacing.lg))
            Text("Matching face…", color = Color.White, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ResultOverlay(outcome: RecognitionOutcome) {
    when (outcome) {
        is RecognitionOutcome.Recognized -> Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.lg),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = AppTheme.colors.success,
                    modifier = Modifier.size(64.dp),
                )
                Spacer(Modifier.height(Spacing.lg))
                UserAvatar(name = outcome.employeeName, size = 56.dp)
                Spacer(Modifier.height(Spacing.md))
                Text(outcome.employeeName, style = MaterialTheme.typography.titleLarge)
                Text(
                    "${outcome.employeeCode} • ${outcome.department}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.lg))
                Text(
                    "${outcome.eventType.displayName} at ${outcome.timeLabel}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(Spacing.sm))
                AttendanceStatusPill(outcome.status)
                if (outcome.lateMinutes > 0) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        "${outcome.lateMinutes} minutes after shift start",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.warning,
                    )
                }
                Spacer(Modifier.height(Spacing.md))
                Text(
                    "Match confidence ${(outcome.confidence * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        is RecognitionOutcome.NotRecognized -> MessageOverlay(
            title = "Face not recognized",
            message = outcome.message,
            isError = true,
        )

        is RecognitionOutcome.Rejected -> MessageOverlay(
            title = "Scan rejected",
            message = outcome.message,
            isError = true,
        )
    }
}

@Composable
private fun MessageOverlay(title: String, message: String, isError: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(Spacing.lg))
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Spacing.sm))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ScanPermissionPrompt(hasBeenRequested: Boolean, onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = Spacing.xxl),
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
