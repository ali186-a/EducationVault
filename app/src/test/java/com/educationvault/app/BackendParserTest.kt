package com.educationvault.app

import com.educationvault.app.net.BackendParser
import com.educationvault.app.net.BackendResult
import com.educationvault.app.net.MeInfo
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendParserTest {
    @Test fun okResponseExposesData() {
        val r = BackendParser.parse(200, """{"ok":true,"data":{"uid":"u1","email":"a@b.com","isAdmin":true,"maxUploadBytes":5242880}}""")
        assertTrue(r is BackendResult.Ok)
        val me = MeInfo.fromJson((r as BackendResult.Ok).data)
        assertEquals("u1", me.uid)
        assertTrue(me.isAdmin)
        assertEquals(5_242_880L, me.maxUploadBytes)
    }

    @Test fun okWithoutDataGivesEmptyObject() {
        val r = BackendParser.parse(200, """{"ok":true}""") as BackendResult.Ok
        assertEquals(0, r.data.length())
    }

    @Test fun errorEnvelopeKeepsCodeAndMessage() {
        val r = BackendParser.parse(200, """{"ok":false,"error":{"code":"NOT_INVITED","message":"nope"}}""")
        assertEquals(BackendResult.Err("NOT_INVITED", "nope"), r)
    }

    @Test fun errorWithoutCodeBecomesBadResponse() {
        val r = BackendParser.parse(200, """{"ok":false}""") as BackendResult.Err
        assertEquals("BAD_RESPONSE", r.code)
    }

    @Test fun httpErrorsAndNonJsonAreErrors() {
        assertEquals("HTTP_500", (BackendParser.parse(500, "x") as BackendResult.Err).code)
        assertEquals("BAD_RESPONSE", (BackendParser.parse(200, "<html>Sign in</html>") as BackendResult.Err).code)
        assertFalse(BackendParser.parse(200, "") is BackendResult.Ok)
    }

    @Test fun meInfoDefaults() {
        val me = MeInfo.fromJson(JSONObject("""{"uid":"u","email":"e@x.com"}"""))
        assertFalse(me.isAdmin)
        assertEquals(5_242_880L, me.maxUploadBytes)
    }
}
