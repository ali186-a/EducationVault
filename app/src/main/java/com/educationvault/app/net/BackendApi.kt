package com.educationvault.app.net

import org.json.JSONException
import org.json.JSONObject

sealed class BackendResult {
    data class Ok(val data: JSONObject) : BackendResult()
    data class Err(val code: String, val message: String) : BackendResult()
}

/** Unexpected backend error code that the caller cannot handle itself. */
class BackendFailure(val code: String, message: String) : Exception(message)

data class MeInfo(val uid: String, val email: String, val isAdmin: Boolean, val maxUploadBytes: Long) {
    companion object {
        fun fromJson(o: JSONObject) = MeInfo(
            uid = o.optString("uid"),
            email = o.optString("email"),
            isAdmin = o.optBoolean("isAdmin", false),
            maxUploadBytes = o.optLong("maxUploadBytes", 5_242_880L)
        )
    }
}

/** Interprets the JSON envelope { ok, data } / { ok:false, error:{code,message} } of the backend. */
object BackendParser {
    fun parse(httpCode: Int, body: String): BackendResult {
        if (httpCode !in 200..299) return BackendResult.Err("HTTP_$httpCode", "HTTP error $httpCode")
        return try {
            val root = JSONObject(body)
            if (root.optBoolean("ok", false)) {
                BackendResult.Ok(root.optJSONObject("data") ?: JSONObject())
            } else {
                val err = root.optJSONObject("error")
                BackendResult.Err(
                    err?.optString("code").orEmpty().ifEmpty { "BAD_RESPONSE" },
                    err?.optString("message").orEmpty()
                )
            }
        } catch (e: JSONException) {
            BackendResult.Err("BAD_RESPONSE", "Backend did not return JSON")
        }
    }
}

class BackendApi(private val baseUrl: String) {
    /** The token travels in the JSON body: Apps Script web apps cannot read custom headers. */
    suspend fun call(action: String, idToken: String?, params: Map<String, Any?> = emptyMap()): BackendResult {
        val json = JSONObject().put("action", action)
        if (idToken != null) json.put("idToken", idToken)
        params.forEach { (k, v) -> json.put(k, v ?: JSONObject.NULL) }
        val r = Http.post(baseUrl, json.toString(), "text/plain;charset=utf-8")
        return BackendParser.parse(r.code, r.body)
    }
}
