package com.educationvault.app.auth

import com.educationvault.app.net.MeInfo

enum class AuthError {
    EMAIL_EXISTS, INVALID_CREDENTIALS, USER_DISABLED, TOO_MANY_ATTEMPTS, WEAK_PASSWORD,
    INVALID_EMAIL, SESSION_EXPIRED, NETWORK, SERVER, CONFIG, UNKNOWN
}

class AuthException(val error: AuthError) : Exception(error.name)

data class AuthTokens(
    val idToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
    val uid: String,
    val email: String
)

data class LookupInfo(val uid: String, val email: String, val emailVerified: Boolean)

sealed class AccessResult {
    data class NeedsVerification(val email: String) : AccessResult()
    data class NotInvited(val email: String) : AccessResult()
    data class Ready(val me: MeInfo) : AccessResult()
}
