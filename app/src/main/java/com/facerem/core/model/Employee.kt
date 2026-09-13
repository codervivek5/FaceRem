package com.facerem.core.model

data class Employee(
    val id: String,
    val employeeCode: String,
    val fullName: String,
    val email: String,
    val phone: String,
    val department: String,
    val branch: String,
    val jobTitle: String,
    val shiftName: String,
    val faceEnrollment: FaceEnrollmentStatus,
    val isActive: Boolean,
) {
    val initials: String
        get() = fullName.trim().split(Regex("\\s+")).take(2).joinToString("") { it.take(1) }.uppercase()
}

enum class FaceEnrollmentStatus(val displayName: String) {
    ENROLLED("Face enrolled"),
    PENDING("Enrollment pending"),
    EXPIRED("Re-enrollment required"),
}

data class Department(val id: String, val name: String, val headCount: Int)

data class Branch(val id: String, val name: String, val city: String, val headCount: Int)

data class Shift(
    val id: String,
    val name: String,
    val startTime: String,
    val endTime: String,
    val graceMinutes: Int,
)
