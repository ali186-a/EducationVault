package com.educationvault.app

import com.educationvault.app.docs.ImageCompressor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtrasLogicTest {
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
