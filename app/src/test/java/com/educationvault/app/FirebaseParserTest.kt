package com.educationvault.app

import com.educationvault.app.auth.AuthError
import com.educationvault.app.auth.AuthException
import com.educationvault.app.auth.FirebaseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseParserTest {
    private fun err(msg: String) = """{"error":{"code":400,"message":"$msg"}}"""

    @Test fun mapsKnownFirebaseErrors() {
        assertEquals(AuthError.EMAIL_EXISTS, FirebaseParser.mapError(400, err("EMAIL_EXISTS")))
        assertEquals(AuthError.INVALID_CREDENTIALS, FirebaseParser.mapError(400, err("INVALID_LOGIN_CREDENTIALS")))
        assertEquals(AuthError.INVALID_CREDENTIALS, FirebaseParser.mapError(400, err("INVALID_PASSWORD")))
        assertEquals(AuthError.INVALID_CREDENTIALS, FirebaseParser.mapError(400, err("EMAIL_NOT_FOUND")))
        assertEquals(AuthError.USER_DISABLED, FirebaseParser.mapError(400, err("USER_DISABLED")))
        assertEquals(AuthError.TOO_MANY_ATTEMPTS, FirebaseParser.mapError(400, err("TOO_MANY_ATTEMPTS_TRY_LATER")))
        assertEquals(AuthError.INVALID_EMAIL, FirebaseParser.mapError(400, err("INVALID_EMAIL")))
        assertEquals(AuthError.SESSION_EXPIRED, FirebaseParser.mapError(400, err("INVALID_REFRESH_TOKEN")))
        assertEquals(AuthError.SESSION_EXPIRED, FirebaseParser.mapError(400, err("TOKEN_EXPIRED")))
        assertEquals(AuthError.SESSION_EXPIRED, FirebaseParser.mapError(400, err("USER_NOT_FOUND")))
    }

    @Test fun errorDetailAfterColonIsIgnored() {
        val body = err("WEAK_PASSWORD : Password should be at least 6 characters")
        assertEquals(AuthError.WEAK_PASSWORD, FirebaseParser.mapError(400, body))
    }

    @Test fun configurationAndServerProblems() {
        val badKey = """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key."}}"""
        assertEquals(AuthError.CONFIG, FirebaseParser.mapError(400, badKey))
        assertEquals(AuthError.CONFIG, FirebaseParser.mapError(400, err("OPERATION_NOT_ALLOWED")))
        assertEquals(AuthError.SERVER, FirebaseParser.mapError(503, "<html>down</html>"))
        assertEquals(AuthError.UNKNOWN, FirebaseParser.mapError(400, "garbage"))
        assertEquals(AuthError.UNKNOWN, FirebaseParser.mapError(400, err("SOMETHING_NEW")))
    }

    @Test fun parsesSignInResponse() {
        val body = """{"idToken":"abc","email":"A@B.com","refreshToken":"r1","expiresIn":"3600","localId":"uid12345"}"""
        val t = FirebaseParser.parseSignIn(body, 1_000L)
        assertEquals("abc", t.idToken)
        assertEquals("r1", t.refreshToken)
        assertEquals("uid12345", t.uid)
        assertEquals("a@b.com", t.email)
        assertEquals(1_000L + 3_600_000L, t.expiresAtMillis)
    }

    @Test fun incompleteSignInResponseIsRejected() {
        val e = assertThrows(AuthException::class.java) { FirebaseParser.parseSignIn("""{"email":"a@b.com"}""", 0L) }
        assertEquals(AuthError.UNKNOWN, e.error)
        assertThrows(AuthException::class.java) { FirebaseParser.parseSignIn("not json", 0L) }
        val badExpiry = """{"idToken":"a","refreshToken":"r","expiresIn":"soon","localId":"u"}"""
        assertThrows(AuthException::class.java) { FirebaseParser.parseSignIn(badExpiry, 0L) }
    }

    @Test fun parsesRefreshResponseAndKeepsEmail() {
        val body = """{"id_token":"new","refresh_token":"r2","expires_in":"1800","user_id":"uid12345"}"""
        val t = FirebaseParser.parseRefresh(body, 5_000L, "a@b.com")
        assertEquals("new", t.idToken)
        assertEquals("r2", t.refreshToken)
        assertEquals("a@b.com", t.email)
        assertEquals(5_000L + 1_800_000L, t.expiresAtMillis)
    }

    @Test fun parsesLookup() {
        val body = """{"users":[{"localId":"uid12345","email":"A@B.com","emailVerified":true}]}"""
        val info = FirebaseParser.parseLookup(body)
        assertTrue(info.emailVerified)
        assertEquals("a@b.com", info.email)
        val unverified = FirebaseParser.parseLookup("""{"users":[{"localId":"u1","email":"x@y.com"}]}""")
        assertFalse(unverified.emailVerified)
    }

    @Test fun lookupWithoutUserMeansSessionExpired() {
        val e = assertThrows(AuthException::class.java) { FirebaseParser.parseLookup("""{"users":[]}""") }
        assertEquals(AuthError.SESSION_EXPIRED, e.error)
        assertThrows(AuthException::class.java) { FirebaseParser.parseLookup("{}") }
    }
}
