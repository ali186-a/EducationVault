package com.educationvault.app

import android.content.Context

/** Remembers the language the user picked. Urdu is the default until they choose otherwise. */
object LanguageStore {
    const val URDU = "ur"
    const val ENGLISH = "en"
    private const val PREFS = "ev_prefs"
    private const val KEY = "language"

    /** Anything other than "en" means Urdu. */
    fun normalize(tag: String?): String = if (tag == ENGLISH) ENGLISH else URDU

    fun get(context: Context): String =
        normalize(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))

    fun set(context: Context, language: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, normalize(language))
            .apply()
    }
}
