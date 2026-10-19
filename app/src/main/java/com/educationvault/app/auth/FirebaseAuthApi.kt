package com.educationvault.app.auth

import com.educationvault.app.net.Http
import java.net.URLEncoder
import org.json.JSONObject

/** Firebase Authentication through the official REST endpoints (no google-services.json needed). */
class FirebaseAuthApi(private val apiKey: String) {

    private val key get() = URLEncoder.encode(apiKey, "UTF-8")

    private suspend fun accounts(path: String, body: JSONObject): String {
        val url = "https://identitytoolkit.googleapis.com/v1/accounts:$path?key=$key"
        val r = Http.post(url, body.toString(), "application/json")
        if (r.code !in 200..299) throw AuthException(FirebaseParser.mapError(r.code, r.body))
        return r.body
    }

    suspend fun signUp(email: String, password: String): AuthTokens {
        val body = JSONObject().put("email", email).put("password", password).put("returnSecureToken", true)
        return FirebaseParser.parseSignIn(accounts("signUp", body), System.currentTimeMillis())
    }

    suspend fun signIn(email: String, password: String): AuthTokens {
        val body = JSONObject().put("email", email).put("password", password).put("returnSecureToken", true)
        return FirebaseParser.parseSignIn(accounts("signInWithPassword", body), System.currentTimeMillis())
    }

    /** Exchanges a Google ID token (from the Google account chooser) for a Firebase login. */
    suspend fun signInWithGoogle(googleIdToken: String): AuthTokens {
        val body = JSONObject()
            .put("postBody", "id_token=" + URLEncoder.encode(googleIdToken, "UTF-8") + "&providerId=google.com")
            .put("requestUri", "http://localhost")
            .put("returnIdpCredential", true)
            .put("returnSecureToken", true)
        return FirebaseParser.parseIdpSignIn(accounts("signInWithIdp", body), System.currentTimeMillis())
    }

    suspend fun refresh(refreshToken: String, email: String): AuthTokens {
        val url = "https://securetoken.googleapis.com/v1/token?key=$key"
        val form = "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(refreshToken, "UTF-8")
        val r = Http.post(url, form, "application/x-www-form-urlencoded")
        if (r.code !in 200..299) throw AuthException(FirebaseParser.mapError(r.code, r.body))
        return FirebaseParser.parseRefresh(r.body, System.currentTimeMillis(), email)
    }

    suspend fun lookup(idToken: String): LookupInfo =
        FirebaseParser.parseLookup(accounts("lookup", JSONObject().put("idToken", idToken)))

    suspend fun sendVerifyEmail(idToken: String) {
        accounts("sendOobCode", JSONObject().put("requestType", "VERIFY_EMAIL").put("idToken", idToken))
    }

    suspend fun sendPasswordReset(email: String) {
        accounts("sendOobCode", JSONObject().put("requestType", "PASSWORD_RESET").put("email", email))
    }
}
