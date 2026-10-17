package com.educationvault.app

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BackendPing {
    /** Simple GET to the web-app URL. Failure = network problem (timeout, no internet, ...). */
    suspend fun ping(url: String): Result<PingOutcome> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 15_000
                conn.readTimeout = 20_000
                conn.requestMethod = "GET"
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                PingParser.parse(code, body)
            } finally {
                conn.disconnect()
            }
        }
    }
}
