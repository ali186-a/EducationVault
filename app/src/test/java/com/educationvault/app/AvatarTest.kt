package com.educationvault.app

import com.educationvault.app.docs.AvatarProcessor
import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarTest {
    @Test fun squareIsCenteredInLandscapeAndPortraitPictures() {
        assertEquals(Triple(500, 0, 1000), AvatarProcessor.centerSquare(2000, 1000))
        assertEquals(Triple(0, 250, 500), AvatarProcessor.centerSquare(500, 1000))
        assertEquals(Triple(0, 0, 800), AvatarProcessor.centerSquare(800, 800))
    }
}
