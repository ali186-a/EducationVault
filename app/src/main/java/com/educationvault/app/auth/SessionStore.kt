package com.educationvault.app.auth

import android.content.Context

data class StoredSession(val refreshToken: String, val email: String, val uid: String)

/**
 * Keeps only the refresh token (encrypted with the Keystore key), plus the email and uid.
 * The password is never stored anywhere.
 */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("ev_session", Context.MODE_PRIVATE)

    fun save(refreshToken: String, email: String, uid: String) {
        prefs.edit()
            .putString("rt", KeystoreCipher.encrypt(refreshToken.toByteArray(Charsets.UTF_8)))
            .putString("email", email)
            .putString("uid", uid)
            .apply()
    }

    fun load(): StoredSession? {
        val enc = prefs.getString("rt", null) ?: return null
        val rt = KeystoreCipher.decrypt(enc)?.toString(Charsets.UTF_8)
        if (rt.isNullOrEmpty()) {
            clear()
            return null
        }
        return StoredSession(rt, prefs.getString("email", "").orEmpty(), prefs.getString("uid", "").orEmpty())
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
