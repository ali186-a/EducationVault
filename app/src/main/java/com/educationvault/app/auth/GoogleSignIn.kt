package com.educationvault.app.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

sealed interface GoogleResult {
    data class Token(val idToken: String) : GoogleResult
    data object Cancelled : GoogleResult
    data object Unavailable : GoogleResult
    data class Failed(val detail: String) : GoogleResult
}

/** Shows the system "Sign in with Google" account chooser and returns a Google ID token. */
object GoogleSignInHelper {
    suspend fun fetchIdToken(context: Context, webClientId: String): GoogleResult {
        return try {
            val option = GetSignInWithGoogleOption.Builder(webClientId).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val credential = CredentialManager.create(context).getCredential(context, request).credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                GoogleResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                GoogleResult.Failed("unexpected credential type")
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleResult.Cancelled
        } catch (e: NoCredentialException) {
            GoogleResult.Unavailable
        } catch (e: GetCredentialException) {
            GoogleResult.Failed(describe(e))
        } catch (e: GoogleIdTokenParsingException) {
            GoogleResult.Failed(describe(e))
        }
    }

    /** Short technical hint shown under the error so a problem can be diagnosed from a screenshot. */
    private fun describe(e: Exception): String =
        (e.javaClass.simpleName + ": " + (e.message ?: "")).take(160)
}
