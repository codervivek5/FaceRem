package com.facerem.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ValidatorsTest {

    @Test
    fun `accepts a well formed work email`() {
        assertNull(Validators.emailError("priya.nair@northwind.demo"))
    }

    @Test
    fun `rejects a blank email with a specific message`() {
        assertEquals("Enter your work email", Validators.emailError("   "))
    }

    @Test
    fun `rejects an email without a domain`() {
        assertNotNull(Validators.emailError("priya@northwind"))
    }

    @Test
    fun `rejects a short password`() {
        assertEquals("Password must be at least 8 characters", Validators.passwordError("abc123"))
    }

    @Test
    fun `accepts a password at the minimum length`() {
        assertNull(Validators.passwordError("demo1234"))
    }

    @Test
    fun `rejects a phone number with too few digits`() {
        assertNotNull(Validators.phoneError("+91 12"))
    }

    @Test
    fun `accepts a formatted phone number`() {
        assertNull(Validators.phoneError("+91 98765 43210"))
    }

    @Test
    fun `required field names itself in the error`() {
        assertEquals("Employee code is required", Validators.requiredError("", "Employee code"))
    }
}
