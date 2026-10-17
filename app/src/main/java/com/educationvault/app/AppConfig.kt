package com.educationvault.app

/** Pure checks on configuration values (unit-testable, no Android classes). */
object ConfigValidator {
    const val PLACEHOLDER = "REPLACE_ME"

    private fun isReal(v: String) = v.isNotBlank() && !v.contains(PLACEHOLDER, ignoreCase = true)

    fun isValidScriptUrl(url: String): Boolean =
        isReal(url) &&
            url.startsWith("https://script.google.com/macros/s/") &&
            url.endsWith("/exec")

    /** Names of the settings that are still missing or invalid. */
    fun missing(apiKey: String, projectId: String, scriptUrl: String): List<String> = buildList {
        if (!isReal(apiKey)) add("FIREBASE_API_KEY")
        if (!isReal(projectId)) add("FIREBASE_PROJECT_ID")
        if (!isValidScriptUrl(scriptUrl)) add("APPS_SCRIPT_URL")
    }

    fun isConfigured(apiKey: String, projectId: String, scriptUrl: String): Boolean =
        missing(apiKey, projectId, scriptUrl).isEmpty()
}

/** Central app configuration. Values come from app-config.properties via BuildConfig. */
object AppConfig {
    const val APP_NAME = "EducationVault"
    const val PACKAGE_NAME = "com.educationvault.app"

    val firebaseApiKey: String = BuildConfig.FIREBASE_API_KEY
    val firebaseProjectId: String = BuildConfig.FIREBASE_PROJECT_ID
    val appsScriptUrl: String = BuildConfig.APPS_SCRIPT_URL
    val maxUploadBytes: Long = BuildConfig.MAX_UPLOAD_BYTES

    val missingKeys: List<String>
        get() = ConfigValidator.missing(firebaseApiKey, firebaseProjectId, appsScriptUrl)

    val isConfigured: Boolean
        get() = missingKeys.isEmpty()
}
