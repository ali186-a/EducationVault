package com.educationvault.app

import com.educationvault.app.InputValidator.Problem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InputValidatorTest {
    @Test fun emailEmptyAndInvalid() {
        assertEquals(Problem.EMAIL_EMPTY, InputValidator.validateEmail(""))
        assertEquals(Problem.EMAIL_EMPTY, InputValidator.validateEmail("   "))
        assertEquals(Problem.EMAIL_INVALID, InputValidator.validateEmail("abc"))
        assertEquals(Problem.EMAIL_INVALID, InputValidator.validateEmail("a@b"))
        assertEquals(Problem.EMAIL_INVALID, InputValidator.validateEmail("a b@example.com"))
        assertEquals(Problem.EMAIL_INVALID, InputValidator.validateEmail("@example.com"))
        assertEquals(Problem.EMAIL_INVALID, InputValidator.validateEmail("a".repeat(250) + "@example.com"))
    }

    @Test fun validEmailsAreAcceptedWithSurroundingSpaces() {
        assertNull(InputValidator.validateEmail("user@example.com"))
        assertNull(InputValidator.validateEmail("  User.Name+tag@Example.co.uk "))
    }

    @Test fun passwordRules() {
        assertEquals(Problem.PASSWORD_EMPTY, InputValidator.validatePassword(""))
        assertEquals(Problem.PASSWORD_TOO_SHORT, InputValidator.validatePassword("1234567"))
        assertNull(InputValidator.validatePassword("12345678"))
    }

    @Test fun loginOnlyNeedsNonEmptyPassword() {
        assertEquals(Problem.EMAIL_INVALID, InputValidator.validateLogin("nope", "x"))
        assertEquals(Problem.PASSWORD_EMPTY, InputValidator.validateLogin("a@b.com", ""))
        assertNull(InputValidator.validateLogin("a@b.com", "x"))
    }

    @Test fun registrationChecksEmailThenPasswordThenConfirmation() {
        assertEquals(Problem.EMAIL_INVALID, InputValidator.validateRegistration("x", "12345678", "12345678"))
        assertEquals(Problem.PASSWORD_TOO_SHORT, InputValidator.validateRegistration("a@b.com", "123", "123"))
        assertEquals(Problem.PASSWORD_MISMATCH, InputValidator.validateRegistration("a@b.com", "12345678", "12345679"))
        assertNull(InputValidator.validateRegistration("a@b.com", "12345678", "12345678"))
    }
}
