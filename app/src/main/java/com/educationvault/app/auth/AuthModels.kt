package com.educationvault.app.auth

import com.educationvault.app.net.MeInfo

enum class AuthError {
    EMAIL_EXISTS, INVALID_CREDENTIALS, USER_DISABLED, TOO_MANY_ATTEMPTS, WEAK_PASSWORD,
    INVALID_EMAIL, SESSION_EXPIRED, NETWORK, SERVER, CONFIG, ACCOUNT_CONFLICT, UNKNOWN
}

class AuthException(val error: AuthError) : Exception(error.name)

data class AuthTokens(
    val idToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
    val uid: String,
    val email: String
)

data class LookupInfo(
    val uid: String,
    val email: String,
    val emailVerified: Boolean,
    val displayName: String = "",
    val photoUrl: String? = null
)

/** Name and picture of the signed-in person (from their Google account, if they used Google). */
data class UserProfile(val name: String, val photoUrl: String?) {
    /** Letter shown in the avatar when there is no picture. */
    fun initial(email: String): String {
        val s = name.trim().ifEmpty { email.trim() }
        return if (s.isEmpty()) "?" else String(Character.toChars(s.codePointAt(0))).uppercase()
    }

    /** First word of the name, or the part of the email before the @. */
    fun shortName(email: String): String =
        name.trim().substringBefore(' ').ifEmpty { email.substringBefore('@') }
}

sealed class AccessResult {
    data class NeedsVerification(val email: String) : AccessResult()
    data class NotInvited(val email: String) : AccessResult()
    data class Ready(val me: MeInfo, val profile: UserProfile) : AccessResult()
}
