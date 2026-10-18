package com.educationvault.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.educationvault.app.AppConfig
import com.educationvault.app.AppContainer
import com.educationvault.app.InputValidator
import com.educationvault.app.R
import com.educationvault.app.auth.AccessResult
import com.educationvault.app.auth.AuthError
import com.educationvault.app.auth.AuthException
import com.educationvault.app.net.BackendFailure
import com.educationvault.app.net.MeInfo
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    data object Restoring : AuthUiState
    data class RestoreFailed(val messageRes: Int) : AuthUiState
    data object SignedOut : AuthUiState
    data class NeedsVerification(val email: String) : AuthUiState
    data class NotInvited(val email: String) : AuthUiState
    data class Ready(val me: MeInfo, val session: Int) : AuthUiState
}

fun InputValidator.Problem.toRes(): Int = when (this) {
    InputValidator.Problem.EMAIL_EMPTY -> R.string.err_email_empty
    InputValidator.Problem.EMAIL_INVALID -> R.string.err_email_invalid
    InputValidator.Problem.PASSWORD_EMPTY -> R.string.err_password_empty
    InputValidator.Problem.PASSWORD_TOO_SHORT -> R.string.err_password_short
    InputValidator.Problem.PASSWORD_MISMATCH -> R.string.err_password_mismatch
}

fun AuthError.toRes(): Int = when (this) {
    AuthError.EMAIL_EXISTS -> R.string.err_email_exists
    AuthError.INVALID_CREDENTIALS -> R.string.err_invalid_credentials
    AuthError.USER_DISABLED -> R.string.err_user_disabled
    AuthError.TOO_MANY_ATTEMPTS -> R.string.err_too_many
    AuthError.WEAK_PASSWORD -> R.string.err_weak_password
    AuthError.INVALID_EMAIL -> R.string.err_email_invalid
    AuthError.SESSION_EXPIRED -> R.string.err_session_expired
    AuthError.NETWORK -> R.string.err_network
    AuthError.SERVER -> R.string.err_server
    AuthError.CONFIG -> R.string.err_firebase_config
    AuthError.UNKNOWN -> R.string.err_unknown
}

private val SESSION_ENDING_CODES = setOf("TOKEN_EXPIRED", "UNAUTHENTICATED", "ACCOUNT_DISABLED")

private fun backendErrorRes(code: String): Int = when (code) {
    "TOKEN_EXPIRED", "UNAUTHENTICATED" -> R.string.err_session_expired
    "ACCOUNT_DISABLED" -> R.string.err_user_disabled
    "NOT_CONFIGURED" -> R.string.err_backend_not_configured
    "UPSTREAM_ERROR", "BUSY", "STORAGE_UNAVAILABLE" -> R.string.err_server
    else -> R.string.err_unknown
}

class AuthViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppContainer.auth(app)
    private var sessionCounter = 0

    var state by mutableStateOf<AuthUiState>(AuthUiState.Restoring)
        private set
    var busy by mutableStateOf(false)
        private set
    var errorRes by mutableStateOf<Int?>(null)
        private set
    var infoRes by mutableStateOf<Int?>(null)
        private set

    init {
        if (AppConfig.isConfigured) restore()
    }

    fun clearMessages() {
        errorRes = null
        infoRes = null
    }

    /** One network action at a time: ignores extra taps while busy (no duplicate requests). */
    private fun launchBusy(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        clearMessages()
        viewModelScope.launch {
            try {
                block()
            } finally {
                busy = false
            }
        }
    }

    private fun failWith(res: Int, endSession: Boolean) {
        if (endSession) {
            repo.signOut()
            state = AuthUiState.SignedOut
            errorRes = res
        } else if (state is AuthUiState.Restoring) {
            state = AuthUiState.RestoreFailed(res)
        } else {
            errorRes = res
        }
    }

    private suspend fun evaluateAccess() {
        try {
            state = when (val r = repo.checkAccess()) {
                is AccessResult.NeedsVerification -> AuthUiState.NeedsVerification(r.email)
                is AccessResult.NotInvited -> AuthUiState.NotInvited(r.email)
                is AccessResult.Ready -> AuthUiState.Ready(r.me, ++sessionCounter)
            }
        } catch (e: AuthException) {
            failWith(e.error.toRes(), e.error == AuthError.SESSION_EXPIRED)
        } catch (e: BackendFailure) {
            failWith(backendErrorRes(e.code), e.code in SESSION_ENDING_CODES)
        }
    }

    private fun restore() {
        viewModelScope.launch {
            state = AuthUiState.Restoring
            try {
                if (repo.restore()) {
                    evaluateAccess()
                } else {
                    state = AuthUiState.SignedOut
                }
            } catch (e: AuthException) {
                state = AuthUiState.RestoreFailed(e.error.toRes())
            }
        }
    }

    fun retryRestore() = restore()

    fun signIn(email: String, password: String) {
        val problem = InputValidator.validateLogin(email, password)
        if (problem != null) {
            infoRes = null
            errorRes = problem.toRes()
            return
        }
        launchBusy {
            try {
                repo.signIn(email, password)
                evaluateAccess()
            } catch (e: AuthException) {
                errorRes = e.error.toRes()
            }
        }
    }

    fun register(email: String, password: String, confirm: String) {
        val problem = InputValidator.validateRegistration(email, password, confirm)
        if (problem != null) {
            infoRes = null
            errorRes = problem.toRes()
            return
        }
        launchBusy {
            try {
                repo.signUp(email, password)
                runCatching { repo.sendVerificationEmail() } // can be resent from the next screen
                evaluateAccess()
            } catch (e: AuthException) {
                errorRes = e.error.toRes()
            }
        }
    }

    fun resetPassword(email: String) {
        val problem = InputValidator.validateEmail(email)
        if (problem != null) {
            infoRes = null
            errorRes = problem.toRes()
            return
        }
        launchBusy {
            try {
                repo.sendPasswordReset(email)
                infoRes = R.string.reset_sent
            } catch (e: AuthException) {
                // Never reveal whether an email is registered.
                if (e.error == AuthError.INVALID_CREDENTIALS) {
                    infoRes = R.string.reset_sent
                } else {
                    errorRes = e.error.toRes()
                }
            }
        }
    }

    fun resendVerification() {
        launchBusy {
            try {
                repo.sendVerificationEmail()
                infoRes = R.string.verify_resent
            } catch (e: AuthException) {
                errorRes = e.error.toRes()
            }
        }
    }

    /** "I have verified": asks Firebase again; never assumes the email is verified. */
    fun recheckAccess() {
        launchBusy {
            evaluateAccess()
            if (state is AuthUiState.NeedsVerification && errorRes == null) {
                errorRes = R.string.verify_still_not
            }
        }
    }

    /** From the "not invited" screen: ask the backend again. */
    fun recheckInvitation() {
        launchBusy {
            evaluateAccess()
            if (state is AuthUiState.NotInvited && errorRes == null) {
                errorRes = R.string.not_invited_still
            }
        }
    }

    /** Called by other screens when the backend says the login is no longer valid. */
    fun onSessionExpired() {
        repo.signOut()
        state = AuthUiState.SignedOut
        errorRes = R.string.err_session_expired
        infoRes = null
    }

    fun signOut() {
        repo.signOut()
        clearMessages()
        state = AuthUiState.SignedOut
    }
}
