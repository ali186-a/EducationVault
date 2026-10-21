package com.educationvault.app

import com.educationvault.app.auth.NameValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NameValidatorTest {
    @Test fun cleansSpaces() {
        assertEquals("Ali Raza", NameValidator.clean("  Ali    Raza  "))
        assertEquals("علی رضا", NameValidator.clean("علی  رضا"))
    }

    @Test fun rejectsEmptyTooLongAndControlCharacters() {
        assertNull(NameValidator.clean("   "))
        assertNull(NameValidator.clean("x".repeat(51)))
        assertEquals("x".repeat(50), NameValidator.clean("x".repeat(50)))
        assertNull(NameValidator.clean("bad\u0007name"))
    }
}
