package com.facerem.core.model

data class User(
    val id: String,
    val fullName: String,
    val email: String,
    val role: Role,
    val organizationId: String,
    val organizationName: String,
    val jobTitle: String? = null,
    val avatarUrl: String? = null,
)

data class Organization(
    val id: String,
    val name: String,
    val plan: SubscriptionPlan,
    val employeeCount: Int,
    val branchCount: Int,
    val timeZone: String,
)

enum class SubscriptionPlan(val displayName: String, val seatLimit: Int) {
    TRIAL("Trial", 25),
    STARTER("Starter", 100),
    GROWTH("Growth", 500),
    ENTERPRISE("Enterprise", Int.MAX_VALUE),
}
