package com.facerem.feature.attendance.presentation

import com.facerem.core.camera.FaceQualityStatus
import com.facerem.core.model.AttendanceEventType
import com.facerem.core.model.RecognitionOutcome

/** How long a result stays on screen before the kiosk arms itself for the next person. */
object ScanTiming {
    const val REQUIRED_STABLE_FRAMES = 6
    const val SUCCESS_HOLD_MILLIS = 2_600L
    const val REPEAT_HOLD_MILLIS = 2_000L
    const val FAILURE_HOLD_MILLIS = 3_600L
}

/** The outcome of feeding one analyser verdict into the scanner. */
data class QualityTransition(val state: ScanUiState, val shouldRecognize: Boolean)

/**
 * The kiosk loop, as a pure transition so its rules can be tested without a camera or DI.
 *
 * The subtle rule is [ScanUiState.awaitingFaceClear]: after someone is greeted they are usually
 * still standing in front of the tablet. Without waiting for an empty frame the scanner would
 * recognise them again immediately and spend the day in a loop.
 */
fun ScanUiState.onQualityFrame(
    status: FaceQualityStatus,
    requiredStableFrames: Int = ScanTiming.REQUIRED_STABLE_FRAMES,
): QualityTransition {
    // Verdicts arriving while the server is deciding, or while a result is up, are ignored.
    if (phase != ScanPhase.SCANNING) return QualityTransition(this, shouldRecognize = false)

    if (awaitingFaceClear) {
        val cleared = status == FaceQualityStatus.NO_FACE
        return QualityTransition(
            state = if (cleared) copy(awaitingFaceClear = false, quality = status) else this,
            shouldRecognize = false,
        )
    }

    if (!status.isValid) {
        return QualityTransition(copy(quality = status, stableFrames = 0), shouldRecognize = false)
    }

    val frames = stableFrames + 1
    if (frames < requiredStableFrames) {
        return QualityTransition(copy(quality = status, stableFrames = frames), shouldRecognize = false)
    }

    return QualityTransition(
        state = copy(quality = status, stableFrames = 0, phase = ScanPhase.VERIFYING),
        shouldRecognize = true,
    )
}

/** Clears the current result and requires an empty frame before the next scan can start. */
fun ScanUiState.armedForNextScan(): ScanUiState = copy(
    phase = ScanPhase.SCANNING,
    outcome = null,
    errorMessage = null,
    stableFrames = 0,
    awaitingFaceClear = true,
)

/** A greeting can be brief; something that needs attention stays up longer. */
fun resultHoldMillis(outcome: RecognitionOutcome?, errorMessage: String?): Long = when {
    errorMessage != null -> ScanTiming.FAILURE_HOLD_MILLIS
    outcome is RecognitionOutcome.NotRecognized -> ScanTiming.FAILURE_HOLD_MILLIS
    outcome is RecognitionOutcome.Rejected -> ScanTiming.FAILURE_HOLD_MILLIS
    (outcome as? RecognitionOutcome.Recognized)?.eventType == AttendanceEventType.ALREADY_PRESENT ->
        ScanTiming.REPEAT_HOLD_MILLIS

    else -> ScanTiming.SUCCESS_HOLD_MILLIS
}
