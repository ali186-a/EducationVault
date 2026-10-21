package com.educationvault.app.auth

import org.json.JSONException
import org.json.JSONObject

/** Pure parsing of Firebase Identity Toolkit / Secure Token responses (unit-tested). */
object FirebaseParser {

    fun errorMessage(body: String): String = try {
        JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
    } catch (e: JSONException) {
        ""
    }

    fun mapError(httpCode: Int, body: String): AuthError {
        val full = errorMessage(body)
        val msg = full.substringBefore(" :").trim()
        return when {
            msg == "EMAIL_EXISTS" -> AuthError.EMAIL_EXISTS
            msg == "INVALID_LOGIN_CREDENTIALS" || msg == "INVALID_PASSWORD" || msg == "EMAIL_NOT_FOUND" ->
                AuthError.INVALID_CREDENTIALS
            msg == "USER_DISABLED" -> AuthError.USER_DISABLED
            msg == "TOO_MANY_ATTEMPTS_TRY_LATER" -> AuthError.TOO_MANY_ATTEMPTS
            msg == "WEAK_PASSWORD" -> AuthError.WEAK_PASSWORD
            msg == "INVALID_EMAIL" || msg == "MISSING_EMAIL" -> AuthError.INVALID_EMAIL
            msg in SESSION_ERRORS -> AuthError.SESSION_EXPIRED
            full.contains("API key", ignoreCase = true) || msg == "OPERATION_NOT_ALLOWED" ||
                msg.startsWith("INVALID_IDP_RESPONSE") -> AuthError.CONFIG
            httpCode >= 500 || httpCode == 429 -> AuthError.SERVER
            else -> AuthError.UNKNOWN
        }
    }

    private val SESSION_ERRORS = setOf(
        "TOKEN_EXPIRED", "INVALID_ID_TOKEN", "INVALID_REFRESH_TOKEN", "USER_NOT_FOUND",
        "CREDENTIAL_TOO_OLD_LOGIN_AGAIN"
    )

    private inline fun <T> parsing(block: () -> T): T = try {
        block()
    } catch (e: JSONException) {
        throw AuthException(AuthError.UNKNOWN)
    } catch (e: NumberFormatException) {
        throw AuthException(AuthError.UNKNOWN)
    }

    /** Response of accounts:signUp and accounts:signInWithPassword. */
    fun parseSignIn(body: String, nowMillis: Long): AuthTokens = parsing {
        val o = JSONObject(body)
        AuthTokens(
            idToken = o.getString("idToken"),
            refreshToken = o.getString("refreshToken"),
            expiresAtMillis = nowMillis + o.optString("expiresIn", "3600").toLong() * 1000L,
            uid = o.getString("localId"),
            email = o.optString("email").trim().lowercase()
        )
    }

    /** Response of accounts:signInWithIdp (Google). "needConfirmation" means Firebase will not merge accounts silently. */
    fun parseIdpSignIn(body: String, nowMillis: Long): AuthTokens {
        val needs = try {
            JSONObject(body).optBoolean("needConfirmation", false)
        } catch (e: JSONException) {
            false
        }
        if (needs) throw AuthException(AuthError.ACCOUNT_CONFLICT)
        return parseSignIn(body, nowMillis)
    }

    /** Response of securetoken.googleapis.com/v1/token (it does not contain the email). */
    fun parseRefresh(body: String, nowMillis: Long, email: String): AuthTokens = parsing {
        val o = JSONObject(body)
        AuthTokens(
            idToken = o.getString("id_token"),
            refreshToken = o.getString("refresh_token"),
            expiresAtMillis = nowMillis + o.optString("expires_in", "3600").toLong() * 1000L,
            uid = o.getString("user_id"),
            email = email
        )
    }

    /** Response of accounts:lookup. */
    fun parseLookup(body: String): LookupInfo = parsing {
        val users = JSONObject(body).optJSONArray("users")
        val u = users?.optJSONObject(0) ?: throw AuthException(AuthError.SESSION_EXPIRED)
        // Name/picture: top-level fields first, then the linked sign-in provider (e.g. google.com)
        var name = u.optString("displayName").trim()
        var photo = u.optString("photoUrl").trim()
        val providers = u.optJSONArray("providerUserInfo")
        if (providers != null) {
            for (i in 0 until providers.length()) {
                val p = providers.optJSONObject(i) ?: continue
                if (name.isEmpty()) name = p.optString("displayName").trim()
                if (photo.isEmpty()) photo = p.optString("photoUrl").trim()
            }
        }
        LookupInfo(
            uid = u.getString("localId"),
            email = u.optString("email").trim().lowercase(),
            emailVerified = u.optBoolean("emailVerified", false),
            displayName = name,
            photoUrl = photo.ifEmpty { null }
        )
    }
}
