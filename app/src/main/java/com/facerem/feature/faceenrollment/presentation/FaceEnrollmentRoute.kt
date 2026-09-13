package com.facerem.feature.faceenrollment.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.facerem.core.camera.FaceCameraPreview
import com.facerem.core.camera.rememberCameraPermissionState
import com.facerem.core.camera.rememberFaceDetector
import com.facerem.core.designsystem.component.AppTopBar
import com.facerem.core.designsystem.component.FaceFramingOverlay
import com.facerem.core.designsystem.component.InlineMessage
import com.facerem.core.designsystem.component.PrimaryButton
import com.facerem.core.designsystem.component.SecondaryButton
import com.facerem.ui.theme.AppTheme
import com.facerem.ui.theme.Spacing
import com.google.mlkit.vision.face.FaceDetector

@Composable
fun FaceEnrollmentRoute(
    onNavigateUp: () -> Unit,
    viewModel: FaceEnrollmentViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permission by rememberCameraPermissionState()
    val detector = rememberFaceDetector()

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Face enrollment",
                subtitle = uiState.employee?.fullName,
                onNavigateUp = onNavigateUp,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenHorizontal),
        ) {
            when {
                uiState.isComplete -> EnrollmentComplete(
                    employeeName = uiState.employee?.fullName.orEmpty(),
                    onDone = onNavigateUp,
                )

                !uiState.consentGiven -> ConsentStep(
                    employeeName = uiState.employee?.fullName.orEmpty(),
                    onConsentChange = viewModel::onConsentChange,
                )

                !permission.isGranted -> CameraPermissionStep(
                    hasBeenRequested = permission.hasBeenRequested,
                    onRequest = permission.request,
                )

                else -> CaptureStep(
                    uiState = uiState,
                    detector = detector,
                    onQuality = viewModel::onQuality,
                    onSubmit = viewModel::submit,
                    onRetake = viewModel::retake,
                )
            }
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}

@Composable
private fun ConsentStep(employeeName: String, onConsentChange: (Boolean) -> Unit) {
    Column {
        Spacer(Modifier.height(Spacing.lg))
        Text("Biometric consent", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Spacing.md))
        Text(
            "Enrolling ${employeeName.ifBlank { "this employee" }} creates a face profile used only " +
                "to verify attendance inside your organization.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.lg))
        listOf(
            "Captured images are sent over HTTPS and are not stored as pictures.",
            "The server stores a mathematical face embedding, scoped to this organization only.",
            "The profile can be removed at any time; attendance history is kept.",
            "Consent and its timestamp are recorded in the audit log.",
        ).forEach { line ->
            Row(modifier = Modifier.padding(vertical = Spacing.xs)) {
                Text("•  ", style = MaterialTheme.typography.bodyMedium)
                Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        var acknowledged by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it })
            Text(
                "The employee has given informed consent to face enrollment.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(Spacing.lg))
        PrimaryButton(
            text = "Continue to capture",
            onClick = { onConsentChange(true) },
            modifier = Modifier.fillMaxWidth(),
            enabled = acknowledged,
        )
    }
}

@Composable
private fun CameraPermissionStep(hasBeenRequested: Boolean, onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
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
                "Camera permission was declined. Enable it in system settings to continue enrollment."
            } else {
                "FaceAttend uses the front camera to capture enrollment frames. Nothing is recorded."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.lg))
        PrimaryButton("Allow camera", onRequest, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun CaptureStep(
    uiState: FaceEnrollmentUiState,
    detector: FaceDetector,
    onQuality: (com.facerem.core.camera.FaceQualityStatus) -> Unit,
    onSubmit: () -> Unit,
    onRetake: () -> Unit,
) {
    val semantic = AppTheme.colors
    val accent = when {
        uiState.allPosesCaptured -> semantic.success
        uiState.quality.isValid -> semantic.success
        else -> semantic.warning
    }

    Column {
        Spacer(Modifier.height(Spacing.md))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.78f)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            if (!uiState.allPosesCaptured) {
                FaceCameraPreview(
                    detector = detector,
                    pose = uiState.currentPose,
                    onQuality = onQuality,
                    modifier = Modifier.fillMaxSize(),
                )
                FaceFramingOverlay(accent = accent, isActive = !uiState.isSubmitting)
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = semantic.success,
                        modifier = Modifier.size(72.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.lg))
        Text(
            if (uiState.allPosesCaptured) "All angles captured" else uiState.currentPose.instruction,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            if (uiState.allPosesCaptured) "Submit to create the face profile." else uiState.quality.guidance,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Spacing.lg))
        LinearProgressIndicator(
            progress = { uiState.progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(MaterialTheme.shapes.extraSmall),
        )
        Spacer(Modifier.height(Spacing.md))

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            ENROLLMENT_POSES.forEachIndexed { index, pose ->
                val captured = index < uiState.capturedPoses
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        if (captured) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (captured) semantic.success else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        pose.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        InlineMessage(text = uiState.errorMessage)

        Spacer(Modifier.height(Spacing.lg))
        PrimaryButton(
            text = "Submit enrollment",
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.allPosesCaptured,
            loading = uiState.isSubmitting,
        )
        Spacer(Modifier.height(Spacing.sm))
        SecondaryButton(
            text = "Start over",
            onClick = onRetake,
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.capturedPoses > 0 && !uiState.isSubmitting,
        )
    }
}

@Composable
private fun EnrollmentComplete(employeeName: String, onDone: () -> Unit) {
    val semantic = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = semantic.success,
            modifier = Modifier.size(84.dp),
        )
        Spacer(Modifier.height(Spacing.lg))
        Text("Face profile created", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "${employeeName.ifBlank { "This employee" }} can now check in with the attendance scanner.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xl))
        PrimaryButton("Done", onDone, modifier = Modifier.fillMaxWidth())
    }
}
