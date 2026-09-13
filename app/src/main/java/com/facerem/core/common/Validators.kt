package com.facerem.core.common

/** Client-side input checks. The server re-validates everything; these only improve UX. */
object Validators {
    private val emailPattern = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun emailError(value: String): String? = when {
        value.isBlank() -> "Enter your work email"
        !emailPattern.matches(value.trim()) -> "Enter a valid email address"
        else -> null
    }

    fun passwordError(value: String): String? = when {
        value.isBlank() -> "Enter your password"
        value.length < 8 -> "Password must be at least 8 characters"
        else -> null
    }

    fun requiredError(value: String, fieldLabel: String): String? =
        if (value.isBlank()) "$fieldLabel is required" else null

    fun phoneError(value: String): String? {
        val digits = value.filter(Char::isDigit)
        return when {
            value.isBlank() -> "Phone number is required"
            digits.length < 8 -> "Enter a valid phone number"
            else -> null
        }
    }
}
