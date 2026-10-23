package com.educationvault.app.update

import org.json.JSONObject

data class UpdateInfo(
    val latestCode: Int,
    val latestName: String,
    val minCode: Int,
    val url: String,
    val notes: String
) {
    /** What to show the person: the version name if there is one, else its number. */
    val label: String get() = latestName.ifEmpty { latestCode.toString() }
}

sealed interface UpdateDecision {
    data object None : UpdateDecision
    data class Optional(val info: UpdateInfo) : UpdateDecision
    data class Required(val info: UpdateInfo) : UpdateDecision
}

object UpdateLogic {
    fun parse(data: JSONObject): UpdateInfo = UpdateInfo(
        latestCode = data.optInt("latestVersionCode", 0),
        latestName = data.optString("latestVersionName"),
        minCode = data.optInt("minVersionCode", 0),
        url = data.optString("updateUrl"),
        notes = data.optString("notes")
    )

    /**
     * Without a download link nothing is shown (a person must never be blocked with no way out).
     * Older than the minimum -> update is required; older than the latest -> update is offered.
     */
    fun decide(currentCode: Int, info: UpdateInfo): UpdateDecision = when {
        info.url.isEmpty() -> UpdateDecision.None
        info.minCode > currentCode -> UpdateDecision.Required(info)
        info.latestCode > currentCode -> UpdateDecision.Optional(info)
        else -> UpdateDecision.None
    }
}
