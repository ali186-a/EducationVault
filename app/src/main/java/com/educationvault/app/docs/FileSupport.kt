package com.educationvault.app.docs

import java.io.ByteArrayOutputStream
import java.io.InputStream

object FileSupport {
    private val ALLOWED = setOf("application/pdf", "image/jpeg", "image/png")

    /** Declared type if allowed, otherwise guessed from the file extension; null = unsupported. */
    fun normalizeMime(declared: String?, fileName: String): String? {
        var m = declared?.trim()?.lowercase().orEmpty()
        if (m == "image/jpg") m = "image/jpeg"
        if (m in ALLOWED) return m
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            else -> null
        }
    }

    /** Reads at most [limit] bytes. Returns null if the stream is longer: files are never truncated. */
    fun readLimited(input: InputStream, limit: Long): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > limit) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}

class PickedFile(val name: String, val mime: String, val bytes: ByteArray)

sealed interface PickResult {
    data class Ok(val file: PickedFile) : PickResult
    data object TooLarge : PickResult
    data object Unsupported : PickResult
    data object Failed : PickResult
}
