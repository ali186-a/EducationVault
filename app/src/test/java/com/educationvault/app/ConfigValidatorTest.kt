package com.educationvault.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigValidatorTest {
    private val goodUrl = "https://script.google.com/macros/s/AKfycbxExample123/exec"

    @Test fun realValuesAreConfigured() {
        assertTrue(ConfigValidator.isConfigured("AIzaSyExampleKey", "my-project", goodUrl))
        assertTrue(ConfigValidator.missing("AIzaSyExampleKey", "my-project", goodUrl).isEmpty())
    }

    @Test fun placeholdersAreReportedAsMissing() {
        val missing = ConfigValidator.missing("REPLACE_ME", "REPLACE_ME", "REPLACE_ME")
        assertEquals(listOf("FIREBASE_API_KEY", "FIREBASE_PROJECT_ID", "APPS_SCRIPT_URL"), missing)
        assertFalse(ConfigValidator.isConfigured("REPLACE_ME", "p", goodUrl))
    }

    @Test fun blankValuesAreMissing() {
        assertEquals(listOf("FIREBASE_API_KEY"), ConfigValidator.missing("  ", "p", goodUrl))
        assertEquals(listOf("FIREBASE_PROJECT_ID"), ConfigValidator.missing("k", "", goodUrl))
    }

    @Test fun scriptUrlMustBeHttpsGoogleWebAppEndingInExec() {
        assertTrue(ConfigValidator.isValidScriptUrl(goodUrl))
        assertFalse(ConfigValidator.isValidScriptUrl("http://script.google.com/macros/s/abc/exec"))
        assertFalse(ConfigValidator.isValidScriptUrl("https://evil.example.com/macros/s/abc/exec"))
        assertFalse(ConfigValidator.isValidScriptUrl("https://script.google.com/macros/s/abc/dev"))
        assertFalse(ConfigValidator.isValidScriptUrl(""))
    }
}
