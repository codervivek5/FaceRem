package com.facerem.core.model

data class AttendanceRecord(
    val id: String,
    val employeeId: String,
    val employeeName: String,
    val employeeCode: String,
    val department: String,
    /** ISO-8601 local date, e.g. 2026-09-13. */
    val date: String,
    val checkInTime: String?,
    val checkOutTime: String?,
    val status: AttendanceStatus,
    val lateMinutes: Int = 0,
    val overtimeMinutes: Int = 0,
    val workedMinutes: Int = 0,
    val capturedBy: String = "Face scan",
)

enum class AttendanceStatus(val displayName: String) {
    PRESENT("Present"),
    LATE("Late"),
    ABSENT("Absent"),
    ON_LEAVE("On leave"),
    HALF_DAY("Half day"),
}

enum class AttendanceEventType(val displayName: String) {
    CHECK_IN("Check in"),
    CHECK_OUT("Check out"),
}

/** Outcome of a server-side recognition attempt. */
sealed interface RecognitionOutcome {
    data class Recognized(
        val employeeId: String,
        val employeeName: String,
        val employeeCode: String,
        val department: String,
        val eventType: AttendanceEventType,
        val timeLabel: String,
        val confidence: Float,
        val status: AttendanceStatus,
        val lateMinutes: Int,
    ) : RecognitionOutcome

    /** Never expose near matches for an unrecognised face. */
    data class NotRecognized(val message: String) : RecognitionOutcome

    data class Rejected(val code: String, val message: String) : RecognitionOutcome
}
