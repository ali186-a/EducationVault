package com.educationvault.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PingParserTest {
    @Test fun configuredBackend() {
        val body = """{"ok":true,"data":{"service":"EducationVault","configured":true}}"""
        assertEquals(PingOutcome.Reachable(true), PingParser.parse(200, body))
    }

    @Test fun backendWithoutConfiguration() {
        val body = """{"ok":true,"data":{"service":"EducationVault","configured":false}}"""
        assertEquals(PingOutcome.Reachable(false), PingParser.parse(200, body))
    }

    @Test fun httpErrorCodesAreReported() {
        assertEquals(PingOutcome.HttpError(500), PingParser.parse(500, "oops"))
        assertEquals(PingOutcome.HttpError(404), PingParser.parse(404, ""))
    }

    @Test fun htmlLoginPageIsNotTheBackend() {
        assertEquals(PingOutcome.NotBackend, PingParser.parse(200, "<html><body>Sign in</body></html>"))
        assertEquals(PingOutcome.NotBackend, PingParser.parse(200, ""))
    }

    @Test fun otherJsonIsNotTheBackend() {
        assertEquals(PingOutcome.NotBackend, PingParser.parse(200, """{"ok":true,"data":{"service":"Other"}}"""))
        assertEquals(PingOutcome.NotBackend, PingParser.parse(200, """{"ok":false,"error":{"code":"X"}}"""))
        assertEquals(PingOutcome.NotBackend, PingParser.parse(200, """{"hello":"world"}"""))
    }
}
