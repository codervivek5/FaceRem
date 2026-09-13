package com.facerem.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.facerem.core.model.AttendanceStatus
import com.facerem.core.model.FaceEnrollmentStatus
import com.facerem.ui.theme.AppTheme

data class StatusColors(val content: Color, val container: Color)

@Composable
fun AttendanceStatus.colors(): StatusColors {
    val semantic = AppTheme.colors
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        AttendanceStatus.PRESENT -> StatusColors(semantic.onSuccessContainer, semantic.successContainer)
        AttendanceStatus.LATE -> StatusColors(semantic.onWarningContainer, semantic.warningContainer)
        AttendanceStatus.ABSENT -> StatusColors(scheme.onErrorContainer, scheme.errorContainer)
        AttendanceStatus.ON_LEAVE -> StatusColors(semantic.onInfoContainer, semantic.infoContainer)
        AttendanceStatus.HALF_DAY -> StatusColors(scheme.onSecondaryContainer, scheme.secondaryContainer)
    }
}

@Composable
fun FaceEnrollmentStatus.colors(): StatusColors {
    val semantic = AppTheme.colors
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        FaceEnrollmentStatus.ENROLLED -> StatusColors(semantic.onSuccessContainer, semantic.successContainer)
        FaceEnrollmentStatus.PENDING -> StatusColors(semantic.onWarningContainer, semantic.warningContainer)
        FaceEnrollmentStatus.EXPIRED -> StatusColors(scheme.onErrorContainer, scheme.errorContainer)
    }
}

/** Small, high-contrast label used in dense lists where a Material chip would be too heavy. */
@Composable
fun StatusPill(
    text: String,
    colors: StatusColors,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(colors.container)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = colors.content)
    }
}

@Composable
fun AttendanceStatusPill(status: AttendanceStatus, modifier: Modifier = Modifier) {
    StatusPill(status.displayName, status.colors(), modifier)
}

@Composable
fun EnrollmentStatusPill(status: FaceEnrollmentStatus, modifier: Modifier = Modifier) {
    StatusPill(status.displayName, status.colors(), modifier)
}
