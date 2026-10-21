package com.educationvault.app

import com.educationvault.app.auth.FirebaseParser
import com.educationvault.app.auth.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileTest {
    @Test fun lookupReadsNameAndPictureFromTopLevel() {
        val i = FirebaseParser.parseLookup(
            """{"users":[{"localId":"u1","email":"A@B.com","emailVerified":true,
            "displayName":"Ali Raza","photoUrl":"https://lh3.googleusercontent.com/a/x=s96-c"}]}"""
        )
        assertEquals("Ali Raza", i.displayName)
        assertEquals("https://lh3.googleusercontent.com/a/x=s96-c", i.photoUrl)
    }

    @Test fun lookupFallsBackToTheGoogleProviderEntry() {
        val i = FirebaseParser.parseLookup(
            """{"users":[{"localId":"u1","email":"a@b.com","emailVerified":true,
            "providerUserInfo":[{"providerId":"google.com","displayName":"Sara","photoUrl":"https://x.example/p.jpg"}]}]}"""
        )
        assertEquals("Sara", i.displayName)
        assertEquals("https://x.example/p.jpg", i.photoUrl)
    }

    @Test fun emailPasswordAccountsHaveNoProfile() {
        val i = FirebaseParser.parseLookup("""{"users":[{"localId":"u1","email":"a@b.com","emailVerified":true}]}""")
        assertEquals("", i.displayName)
        assertNull(i.photoUrl)
    }

    @Test fun avatarLetterAndShortName() {
        assertEquals("A", UserProfile("ali raza", null).initial("x@y.com"))
        assertEquals("X", UserProfile("", null).initial("x@y.com"))
        assertEquals("?", UserProfile("", null).initial(""))
        assertEquals("ali", UserProfile("ali raza", null).shortName("x@y.com"))
        assertEquals("sara", UserProfile("", null).shortName("sara@y.com"))
    }
}
