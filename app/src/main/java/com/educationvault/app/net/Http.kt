package com.educationvault.app.net

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class HttpResult(val code: Int, val body: String)

object Http {
    /**
     * HTTPS POST. Throws IOException on network problems.
     * Redirects are followed automatically: Apps Script answers POST with a 302 that must be
     * followed with GET, which HttpURLConnection does (so streaming mode must stay OFF).
     * Nothing is ever logged here: bodies can contain tokens or passwords.
     */
    suspend fun post(url: String, body: String, contentType: String): HttpResult =
        withContext(Dispatchers.IO) {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 15_000
                conn.readTimeout = 60_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", contentType)
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                HttpResult(code, stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty())
            } finally {
                conn.disconnect()
            }
        }
}
