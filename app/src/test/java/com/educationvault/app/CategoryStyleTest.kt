package com.educationvault.app

import com.educationvault.app.docs.CategoryStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryStyleTest {
    private val ids = listOf(
        "matric", "intermediate", "bachelor14", "master16", "mphil", "phd",
        "diploma", "cv", "experience", "employment", "identity"
    )

    @Test fun everyBuiltInCategoryHasItsOwnEmoji() {
        val emojis = ids.map { CategoryStyle.emojiFor(it) }
        assertTrue(emojis.all { it.isNotEmpty() })
        assertEquals(ids.size, emojis.toSet().size)
    }

    @Test fun otherAndCustomCategoriesGetTheFolder() {
        assertEquals(CategoryStyle.emojiFor("other"), CategoryStyle.emojiFor("custom_ab12cd34"))
    }
}
