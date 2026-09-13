package com.facerem.core.camera

import android.graphics.Rect
import com.google.mlkit.vision.face.Face
import kotlin.math.abs

/**
 * Frame quality verdicts surfaced to the user during enrollment and scanning. Only [VALID]
 * frames are eligible for upload; everything else is coaching, not an error.
 */
enum class FaceQualityStatus(val guidance: String) {
    VALID("Hold still"),
    NO_FACE("Position your face inside the frame"),
    MULTIPLE_FACES("Only one person at a time"),
    TOO_FAR("Move a little closer"),
    TOO_CLOSE("Move a little further away"),
    OFF_CENTER("Centre your face in the oval"),
    LOOK_STRAIGHT("Look straight at the camera"),
    TOO_DARK("Find better lighting"),
    EYES_CLOSED("Keep your eyes open"),
    ;

    val isValid: Boolean get() = this == VALID
}

/**
 * Evaluates a detected frame against face size, position, pose, brightness and face count.
 * Thresholds are deliberately conservative: a rejected frame costs nothing, a bad enrollment
 * frame degrades every future match.
 */
object FaceQualityEvaluator {

    private const val MIN_FACE_WIDTH_RATIO = 0.28f
    private const val MAX_FACE_WIDTH_RATIO = 0.82f
    private const val MAX_CENTER_OFFSET_RATIO = 0.18f
    private const val MAX_HEAD_ANGLE_DEGREES = 16f
    private const val MIN_LUMINANCE = 55
    private const val MIN_EYE_OPEN_PROBABILITY = 0.35f

    fun evaluate(
        faces: List<Face>,
        imageWidth: Int,
        imageHeight: Int,
        averageLuminance: Int,
        requirePose: HeadPose = HeadPose.FRONT,
    ): FaceQualityStatus {
        if (faces.isEmpty()) return FaceQualityStatus.NO_FACE
        if (faces.size > 1) return FaceQualityStatus.MULTIPLE_FACES
        if (averageLuminance in 0 until MIN_LUMINANCE) return FaceQualityStatus.TOO_DARK

        val face = faces.first()
        val bounds: Rect = face.boundingBox
        if (imageWidth <= 0 || imageHeight <= 0) return FaceQualityStatus.NO_FACE

        val widthRatio = bounds.width().toFloat() / imageWidth
        if (widthRatio < MIN_FACE_WIDTH_RATIO) return FaceQualityStatus.TOO_FAR
        if (widthRatio > MAX_FACE_WIDTH_RATIO) return FaceQualityStatus.TOO_CLOSE

        val centerOffsetX = abs(bounds.exactCenterX() - imageWidth / 2f) / imageWidth
        val centerOffsetY = abs(bounds.exactCenterY() - imageHeight / 2f) / imageHeight
        if (centerOffsetX > MAX_CENTER_OFFSET_RATIO || centerOffsetY > MAX_CENTER_OFFSET_RATIO) {
            return FaceQualityStatus.OFF_CENTER
        }

        val poseStatus = requirePose.validate(face.headEulerAngleY, face.headEulerAngleX)
        if (poseStatus != null) return poseStatus

        val leftEye = face.leftEyeOpenProbability
        val rightEye = face.rightEyeOpenProbability
        if (leftEye != null && rightEye != null &&
            leftEye < MIN_EYE_OPEN_PROBABILITY && rightEye < MIN_EYE_OPEN_PROBABILITY
        ) {
            return FaceQualityStatus.EYES_CLOSED
        }

        return FaceQualityStatus.VALID
    }

    /** Enrollment asks for three views so the backend can build a more robust embedding set. */
    enum class HeadPose(val instruction: String) {
        FRONT("Look straight ahead"),
        SLIGHT_LEFT("Turn slightly to your left"),
        SLIGHT_RIGHT("Turn slightly to your right"),
        ;

        fun validate(yawDegrees: Float, pitchDegrees: Float): FaceQualityStatus? {
            if (abs(pitchDegrees) > MAX_HEAD_ANGLE_DEGREES) return FaceQualityStatus.LOOK_STRAIGHT
            return when (this) {
                FRONT -> if (abs(yawDegrees) > MAX_HEAD_ANGLE_DEGREES) FaceQualityStatus.LOOK_STRAIGHT else null
                SLIGHT_LEFT -> if (yawDegrees < 12f) FaceQualityStatus.LOOK_STRAIGHT else null
                SLIGHT_RIGHT -> if (yawDegrees > -12f) FaceQualityStatus.LOOK_STRAIGHT else null
            }
        }
    }
}
