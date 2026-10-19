package com.educationvault.app

import com.educationvault.app.auth.AuthError
import com.educationvault.app.auth.AuthException
import com.educationvault.app.auth.FirebaseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleSignInLogicTest {
    private val okBody = """{"idToken":"ID","refreshToken":"RT","expiresIn":"3600","localId":"uid1",
        "email":"User@Gmail.com","isNewUser":true,"emailVerified":true}"""

    @Test fun parsesGoogleSignIn() {
        val t = FirebaseParser.parseIdpSignIn(okBody, 1_000L)
        assertEquals("ID", t.idToken)
        assertEquals("uid1", t.uid)
        assertEquals("user@gmail.com", t.email)
        assertEquals(1_000L + 3_600_000L, t.expiresAtMillis)
    }

    @Test fun needConfirmationIsAConflictNotACrash() {
        val e = assertThrows(AuthException::class.java) {
            FirebaseParser.parseIdpSignIn("""{"needConfirmation":true,"email":"a@b.com"}""", 0L)
        }
        assertEquals(AuthError.ACCOUNT_CONFLICT, e.error)
    }

    @Test fun garbageBodyIsUnknownError() {
        val e = assertThrows(AuthException::class.java) { FirebaseParser.parseIdpSignIn("not json", 0L) }
        assertEquals(AuthError.UNKNOWN, e.error)
    }

    @Test fun badIdpResponseMapsToConfigError() {
        val body = """{"error":{"code":400,"message":"INVALID_IDP_RESPONSE : Invalid Idp Response"}}"""
        assertEquals(AuthError.CONFIG, FirebaseParser.mapError(400, body))
    }

    @Test fun googleClientIdValidation() {
        assertTrue(ConfigValidator.isValidGoogleClientId("939919352422-l8vkq4vu0t5mmo8j7v7etf5li3qhoban.apps.googleusercontent.com"))
        assertFalse(ConfigValidator.isValidGoogleClientId("REPLACE_ME"))
        assertFalse(ConfigValidator.isValidGoogleClientId(""))
        assertFalse(ConfigValidator.isValidGoogleClientId("12345.example.com"))
    }
}
