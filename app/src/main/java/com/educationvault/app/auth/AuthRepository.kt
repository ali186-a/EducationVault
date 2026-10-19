package com.educationvault.app.auth

import android.content.Context
import com.educationvault.app.AppConfig
import com.educationvault.app.net.BackendApi
import com.educationvault.app.net.BackendFailure
import com.educationvault.app.net.BackendResult
import com.educationvault.app.net.MeInfo
import java.io.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AuthRepository(context: Context) {
    private val store = SessionStore(context.applicationContext)
    private val firebase = FirebaseAuthApi(AppConfig.firebaseApiKey)
    private val backend = BackendApi(AppConfig.appsScriptUrl)
    private val mutex = Mutex()

    @Volatile
    private var tokens: AuthTokens? = null

    private suspend fun <T> net(block: suspend () -> T): T =
        try {
            block()
        } catch (e: IOException) {
            throw AuthException(AuthError.NETWORK)
        }

    private fun remember(t: AuthTokens) {
        tokens = t
        // If the Keystore is unavailable the user stays signed in for this run only.
        runCatching { store.save(t.refreshToken, t.email, t.uid) }
    }

    /** A valid Firebase ID token; refreshes it first when it expires within a minute. */
    suspend fun idToken(): String = mutex.withLock {
        val t = tokens ?: throw AuthException(AuthError.SESSION_EXPIRED)
        if (t.idToken.isNotEmpty() && t.expiresAtMillis - System.currentTimeMillis() > 60_000L) {
            return@withLock t.idToken
        }
        try {
            val fresh = net { firebase.refresh(t.refreshToken, t.email) }
            remember(fresh)
            fresh.idToken
        } catch (e: AuthException) {
            if (e.error == AuthError.SESSION_EXPIRED) signOut()
            throw e
        }
    }

    /** true = a saved session was restored; false = nobody is signed in. Throws on network problems. */
    suspend fun restore(): Boolean {
        val s = store.load() ?: return false
        tokens = AuthTokens("", s.refreshToken, 0L, s.uid, s.email)
        return try {
            idToken()
            true
        } catch (e: AuthException) {
            if (e.error == AuthError.SESSION_EXPIRED) false else throw e
        }
    }

    suspend fun signIn(email: String, password: String) {
        remember(net { firebase.signIn(email.trim(), password) })
    }

    suspend fun signInWithGoogle(googleIdToken: String) {
        remember(net { firebase.signInWithGoogle(googleIdToken) })
    }

    suspend fun signUp(email: String, password: String) {
        remember(net { firebase.signUp(email.trim(), password) })
    }

    suspend fun sendVerificationEmail() {
        val t = idToken()
        net { firebase.sendVerifyEmail(t) }
    }

    suspend fun sendPasswordReset(email: String) {
        net { firebase.sendPasswordReset(email.trim()) }
    }

    /** Authenticated backend call: always sends a fresh, valid ID token. */
    suspend fun call(action: String, params: Map<String, Any?> = emptyMap()): BackendResult {
        val t = idToken()
        return net { backend.call(action, t, params) }
    }

    fun signOut() {
        tokens = null
        store.clear()
    }

    /**
     * Asks Firebase whether the email is verified (never assumed), then asks the backend
     * whether this account is invited. Unexpected backend errors are thrown as BackendFailure.
     */
    suspend fun checkAccess(): AccessResult {
        val t = idToken()
        val info = net { firebase.lookup(t) }
        if (!info.emailVerified) return AccessResult.NeedsVerification(info.email)
        return when (val r = net { backend.call("me", t) }) {
            is BackendResult.Ok -> AccessResult.Ready(MeInfo.fromJson(r.data))
            is BackendResult.Err -> when (r.code) {
                "NOT_INVITED" -> AccessResult.NotInvited(info.email)
                "EMAIL_NOT_VERIFIED" -> AccessResult.NeedsVerification(info.email)
                else -> throw BackendFailure(r.code, r.message)
            }
        }
    }
}
