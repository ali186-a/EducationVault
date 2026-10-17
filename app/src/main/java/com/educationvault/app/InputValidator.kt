package com.educationvault.app

/** Validation for the login / registration / reset forms (used by the screens built next). */
object InputValidator {
    enum class Problem {
        EMAIL_EMPTY, EMAIL_INVALID, PASSWORD_EMPTY, PASSWORD_TOO_SHORT, PASSWORD_MISMATCH
    }

    const val MIN_PASSWORD_LENGTH = 8

    private val EMAIL_RE =
        Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9\\-]+(\\.[A-Za-z0-9\\-]+)*\\.[A-Za-z]{2,}$")

    fun validateEmail(raw: String): Problem? {
        val s = raw.trim()
        if (s.isEmpty()) return Problem.EMAIL_EMPTY
        if (s.length > 254 || !EMAIL_RE.matches(s)) return Problem.EMAIL_INVALID
        return null
    }

    fun validatePassword(password: String): Problem? {
        if (password.isEmpty()) return Problem.PASSWORD_EMPTY
        if (password.length < MIN_PASSWORD_LENGTH) return Problem.PASSWORD_TOO_SHORT
        return null
    }

    fun validateLogin(email: String, password: String): Problem? =
        validateEmail(email) ?: (if (password.isEmpty()) Problem.PASSWORD_EMPTY else null)

    fun validateRegistration(email: String, password: String, confirm: String): Problem? =
        validateEmail(email) ?: validatePassword(password)
            ?: (if (password != confirm) Problem.PASSWORD_MISMATCH else null)
}
