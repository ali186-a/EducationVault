package com.educationvault.app.docs

import java.util.Locale

object Formatting {
    fun formatSize(bytes: Long): String = when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }

    /** "2026-10-18T10:00:00.000Z" -> "2026-10-18" */
    fun dateOnly(iso: String): String = iso.take(10)

    fun mimeLabel(mime: String): String = when (mime) {
        "application/pdf" -> "PDF"
        "image/jpeg" -> "JPG"
        "image/png" -> "PNG"
        else -> mime.substringAfter('/').uppercase()
    }

    fun extensionFor(mime: String): String = when (mime) {
        "application/pdf" -> "pdf"
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        else -> "bin"
    }

    /** A safe file name for "save to device" (no slashes or other path characters). */
    fun suggestedFileName(title: String, mime: String): String {
        val base = title.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_").trim().ifEmpty { "document" }.take(80)
        return base + "." + extensionFor(mime)
    }

    fun titleFromFileName(name: String): String = name.substringBeforeLast('.', name).trim().take(120)
}
