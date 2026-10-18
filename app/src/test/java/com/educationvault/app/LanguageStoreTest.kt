package com.educationvault.app

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageStoreTest {
    @Test fun englishIsOnlyChosenWhenExplicit() {
        assertEquals("en", LanguageStore.normalize("en"))
    }

    @Test fun everythingElseFallsBackToUrdu() {
        assertEquals("ur", LanguageStore.normalize("ur"))
        assertEquals("ur", LanguageStore.normalize(null))
        assertEquals("ur", LanguageStore.normalize(""))
        assertEquals("ur", LanguageStore.normalize("fr"))
        assertEquals("ur", LanguageStore.normalize("EN"))
    }
}
