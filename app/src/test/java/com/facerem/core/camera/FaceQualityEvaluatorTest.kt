package com.facerem.core.camera

import com.facerem.core.camera.FaceQualityEvaluator.HeadPose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FaceQualityEvaluatorTest {

    @Test
    fun `front pose accepts a face looking straight ahead`() {
        assertNull(HeadPose.FRONT.validate(yawDegrees = 3f, pitchDegrees = -2f))
    }

    @Test
    fun `front pose rejects a turned head`() {
        assertEquals(
            FaceQualityStatus.LOOK_STRAIGHT,
            HeadPose.FRONT.validate(yawDegrees = 28f, pitchDegrees = 0f),
        )
    }

    @Test
    fun `any pose rejects an extreme pitch`() {
        assertEquals(
            FaceQualityStatus.LOOK_STRAIGHT,
            HeadPose.SLIGHT_LEFT.validate(yawDegrees = 20f, pitchDegrees = 30f),
        )
    }

    @Test
    fun `slight left requires a positive yaw`() {
        assertNull(HeadPose.SLIGHT_LEFT.validate(yawDegrees = 20f, pitchDegrees = 0f))
        assertEquals(
            FaceQualityStatus.LOOK_STRAIGHT,
            HeadPose.SLIGHT_LEFT.validate(yawDegrees = 2f, pitchDegrees = 0f),
        )
    }

    @Test
    fun `slight right requires a negative yaw`() {
        assertNull(HeadPose.SLIGHT_RIGHT.validate(yawDegrees = -20f, pitchDegrees = 0f))
        assertEquals(
            FaceQualityStatus.LOOK_STRAIGHT,
            HeadPose.SLIGHT_RIGHT.validate(yawDegrees = -2f, pitchDegrees = 0f),
        )
    }

    @Test
    fun `only VALID is treated as capturable`() {
        assertEquals(true, FaceQualityStatus.VALID.isValid)
        FaceQualityStatus.entries.filter { it != FaceQualityStatus.VALID }.forEach { status ->
            assertEquals("$status must not be capturable", false, status.isValid)
        }
    }
}
