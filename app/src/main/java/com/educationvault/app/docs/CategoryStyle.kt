package com.educationvault.app.docs

/** One emoji per category (works on every phone, no icon library needed). */
object CategoryStyle {
    fun emojiFor(id: String): String = when (id) {
        "matric" -> "\uD83C\uDF93"          // graduation cap
        "intermediate" -> "\uD83C\uDFDB\uFE0F"    // classical building
        "bachelor14" -> "\uD83D\uDCD6"      // open book
        "master16" -> "\uD83C\uDFC5"        // medal
        "mphil" -> "\uD83C\uDF10"           // globe
        "phd" -> "\u2728"                    // sparkles
        "diploma" -> "\uD83D\uDCDC"         // scroll
        "cv" -> "\uD83D\uDCC4"              // page
        "experience" -> "\uD83D\uDCBC"      // briefcase
        "employment" -> "\uD83E\uDDFE"      // receipt
        "identity" -> "\uD83C\uDD94"        // ID
        else -> "\uD83D\uDCC1"              // folder (custom / other)
    }
}
