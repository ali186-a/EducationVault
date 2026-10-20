package com.educationvault.app

import com.educationvault.app.auth.AppSignature
import org.junit.Assert.assertEquals
import org.junit.Test

class AppSignatureTest {
    @Test fun formatsFingerprintLikeFirebaseExpects() {
        assertEquals("0A:FF:00:7F", AppSignature.hex(byteArrayOf(0x0A, 0xFF.toByte(), 0x00, 0x7F)))
    }
}
