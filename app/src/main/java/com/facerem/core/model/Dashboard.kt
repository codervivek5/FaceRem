package com.facerem.core.model

data class DashboardSummary(
    val organizationName: String,
    val dateLabel: String,
    val totalEmployees: Int,
    val presentCount: Int,
    val lateCount: Int,
    val absentCount: Int,
    val onLeaveCount: Int,
    val pendingEnrollments: Int,
    val weeklyAttendanceRate: List<DailyRate>,
    val recentActivity: List<AttendanceRecord>,
) {
    val attendanceRate: Float
        get() = if (totalEmployees == 0) 0f else (presentCount + lateCount).toFloat() / totalEmployees
}

data class DailyRate(val dayLabel: String, val rate: Float)

data class ReportSummary(
    val periodLabel: String,
    val averageAttendanceRate: Float,
    val totalWorkedHours: Int,
    val totalOvertimeHours: Int,
    val lateArrivals: Int,
    val absences: Int,
    val departmentBreakdown: List<DepartmentRate>,
)

data class DepartmentRate(val department: String, val rate: Float, val headCount: Int)
