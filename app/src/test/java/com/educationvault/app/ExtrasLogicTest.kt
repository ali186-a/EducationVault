package com.educationvault.app

import com.educationvault.app.admin.InviteParser
import com.educationvault.app.admin.InviteValidator
import com.educationvault.app.docs.ImageCompressor
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtrasLogicTest {
    @Test fun parsesInvites() {
        val list = InviteParser.parseList(
            JSONObject(
                """{"invites":[
                {"email":"a@x.com","status":"active","invitedAt":"2026-10-18T10:00:00.000Z","uid":null},
                {"email":"b@x.com","status":"revoked","invitedAt":"2026-10-17T10:00:00.000Z","uid":"u123"}]}"""
            )
        )
        assertEquals(2, list.size)
        assertTrue(list[0].active)
        assertFalse(list[0].joined)
        assertFalse(list[1].active)
        assertTrue(list[1].joined)
    }

    @Test fun emailsAreNormalized() {
        assertEquals("a@x.com", InviteValidator.normalize("  A@X.Com "))
    }

    @Test fun samplingKeepsEnoughDetail() {
        assertEquals(1, ImageCompressor.sampleSizeFor(4000, 3000, 2400))
        assertEquals(2, ImageCompressor.sampleSizeFor(5000, 3000, 2400))
        assertEquals(4, ImageCompressor.sampleSizeFor(10000, 8000, 2400))
        assertEquals(1, ImageCompressor.sampleSizeFor(1000, 800, 2400))
    }

    @Test fun targetSizeKeepsAspectRatio() {
        assertEquals(1000 to 800, ImageCompressor.targetSize(1000, 800, 2400))
        assertEquals(2400 to 1800, ImageCompressor.targetSize(4000, 3000, 2400))
        assertEquals(1800 to 2400, ImageCompressor.targetSize(3000, 4000, 2400))
    }

    @Test fun cameraFileNameIsSafe() {
        val n = ImageCompressor.cameraFileName(0L)
        assertTrue(n.startsWith("scan_") && n.endsWith(".jpg"))
        assertFalse(n.contains("/"))
    }
}
