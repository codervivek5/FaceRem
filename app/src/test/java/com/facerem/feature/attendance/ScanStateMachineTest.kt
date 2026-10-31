package com.facerem.feature.attendance

import com.facerem.core.camera.FaceQualityStatus
import com.facerem.core.model.AttendanceEventType
import com.facerem.core.model.AttendanceStatus
import com.facerem.core.model.RecognitionOutcome
import com.facerem.feature.attendance.presentation.ScanPhase
import com.facerem.feature.attendance.presentation.ScanTiming
import com.facerem.feature.attendance.presentation.ScanUiState
import com.facerem.feature.attendance.presentation.armedForNextScan
import com.facerem.feature.attendance.presentation.onQualityFrame
import com.facerem.feature.attendance.presentation.resultHoldMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The gate-kiosk loop: hands-free, and safe when the same person lingers in front of it. */
class ScanStateMachineTest {

    private val required = ScanTiming.REQUIRED_STABLE_FRAMES

    private fun ScanUiState.feed(status: FaceQualityStatus, times: Int): ScanUiState {
        var state = this
        repeat(times) { state = state.onQualityFrame(status, required).state }
        return state
    }

    @Test
    fun `a held face triggers recognition without any tap`() {
        var state = ScanUiState()
        repeat(required - 1) {
            val step = state.onQualityFrame(FaceQualityStatus.VALID, required)
            assertFalse("must not fire before the streak completes", step.shouldRecognize)
            state = step.state
        }

        val final = state.onQualityFrame(FaceQualityStatus.VALID, required)
        assertTrue(final.shouldRecognize)
        assertEquals(ScanPhase.VERIFYING, final.state.phase)
    }

    @Test
    fun `an interrupted streak restarts`() {
        val state = ScanUiState()
            .feed(FaceQualityStatus.VALID, required - 1)
            .feed(FaceQualityStatus.TOO_FAR, 1)
        assertEquals(0, state.stableFrames)
    }

    @Test
    fun `verdicts are ignored while the server is deciding`() {
        val verifying = ScanUiState(phase = ScanPhase.VERIFYING)
        val step = verifying.onQualityFrame(FaceQualityStatus.VALID, required)
        assertFalse(step.shouldRecognize)
        assertEquals(verifying, step.state)
    }

    @Test
    fun `someone still standing there is not scanned again`() {
        // This is the loop the gate would otherwise fall into all day.
        val armed = ScanUiState().armedForNextScan()
        assertTrue(armed.awaitingFaceClear)

        val stillThere = armed.feed(FaceQualityStatus.VALID, required * 3)
        assertTrue("the gate must stay disarmed", stillThere.awaitingFaceClear)
        assertEquals(ScanPhase.SCANNING, stillThere.phase)
        assertEquals(0, stillThere.stableFrames)
    }

    @Test
    fun `the next person can scan once the frame empties`() {
        val armed = ScanUiState().armedForNextScan()
        val cleared = armed.feed(FaceQualityStatus.NO_FACE, 1)
        assertFalse(cleared.awaitingFaceClear)

        val nextPerson = cleared.feed(FaceQualityStatus.VALID, required - 1)
        val fires = nextPerson.onQualityFrame(FaceQualityStatus.VALID, required)
        assertTrue(fires.shouldRecognize)
    }

    @Test
    fun `arming clears the previous person's result`() {
        val showing = ScanUiState(
            phase = ScanPhase.RESULT,
            outcome = recognized(AttendanceEventType.CHECK_IN),
            errorMessage = "stale",
            stableFrames = 4,
        )
        val armed = showing.armedForNextScan()

        assertEquals(ScanPhase.SCANNING, armed.phase)
        assertEquals(null, armed.outcome)
        assertEquals(null, armed.errorMessage)
        assertEquals(0, armed.stableFrames)
    }

    @Test
    fun `a repeat visit is held on screen more briefly than a failure`() {
        val checkIn = resultHoldMillis(recognized(AttendanceEventType.CHECK_IN), null)
        val repeat = resultHoldMillis(recognized(AttendanceEventType.ALREADY_PRESENT), null)
        val unknown = resultHoldMillis(RecognitionOutcome.NotRecognized("no"), null)
        val failure = resultHoldMillis(null, "network down")

        assertTrue("a repeat is the least interesting outcome", repeat < checkIn)
        assertTrue(unknown > checkIn)
        assertEquals(ScanTiming.FAILURE_HOLD_MILLIS, failure)
    }

    @Test
    fun `manual mode keeps its buttons and kiosk mode hides them`() {
        assertTrue(ScanUiState(kioskMode = false).showsManualControls)
        assertFalse(ScanUiState(kioskMode = true).showsManualControls)
    }

    @Test
    fun `the camera is never torn down between people`() {
        ScanPhase.entries.forEach { phase ->
            assertTrue("$phase must keep the preview bound", ScanUiState(phase = phase).isCameraActive)
        }
    }

    @Test
    fun `guidance asks the next person to step up while disarmed`() {
        val armed = ScanUiState().armedForNextScan()
        assertEquals("Next person, please step up", armed.guidance)
    }

    private fun recognized(event: AttendanceEventType) = RecognitionOutcome.Recognized(
        employeeId = "emp-1",
        employeeName = "Aarav Sharma",
        employeeCode = "NW-1001",
        department = "Engineering",
        eventType = event,
        timeLabel = "09:05 AM",
        confidence = 0.94f,
        status = AttendanceStatus.PRESENT,
        lateMinutes = 0,
        checkInLabel = "09:05 AM",
    )
}
