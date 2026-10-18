package com.educationvault.app

import android.content.Context
import com.educationvault.app.auth.AuthRepository

/** One shared AuthRepository so every screen uses the same in-memory login tokens. */
object AppContainer {
    @Volatile
    private var authRepo: AuthRepository? = null

    fun auth(context: Context): AuthRepository =
        authRepo ?: synchronized(this) {
            authRepo ?: AuthRepository(context.applicationContext).also { authRepo = it }
        }
}
