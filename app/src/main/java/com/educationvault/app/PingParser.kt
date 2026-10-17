package com.educationvault.app

import org.json.JSONException
import org.json.JSONObject

sealed class PingOutcome {
    data class Reachable(val configured: Boolean) : PingOutcome()
    data class HttpError(val code: Int) : PingOutcome()
    data object NotBackend : PingOutcome()
}

/** Interprets the answer of the backend's `ping`/GET endpoint. Never trusts a bare HTTP 200. */
object PingParser {
    fun parse(code: Int, body: String): PingOutcome {
        if (code !in 200..299) return PingOutcome.HttpError(code)
        return try {
            val root = JSONObject(body)
            val data = root.optJSONObject("data")
            if (root.optBoolean("ok", false) && data != null && data.optString("service") == "EducationVault") {
                PingOutcome.Reachable(data.optBoolean("configured", false))
            } else {
                PingOutcome.NotBackend
            }
        } catch (e: JSONException) {
            PingOutcome.NotBackend
        }
    }
}
