package com.educationvault.app.admin

import com.educationvault.app.auth.AuthRepository
import com.educationvault.app.docs.DocsException
import com.educationvault.app.net.BackendResult
import org.json.JSONException
import org.json.JSONObject

data class Invite(
    val email: String,
    val active: Boolean,
    val invitedAt: String,
    val joined: Boolean // true once that person has logged in at least once
)

object InviteParser {
    fun parseInvite(o: JSONObject): Invite = Invite(
        email = o.getString("email"),
        active = o.optString("status") == "active",
        invitedAt = o.optString("invitedAt"),
        joined = !o.isNull("uid") && o.optString("uid").isNotEmpty()
    )

    fun parseList(data: JSONObject): List<Invite> {
        val arr = data.getJSONArray("invites")
        return (0 until arr.length()).map { parseInvite(arr.getJSONObject(it)) }
    }
}

object InviteValidator {
    /** Emails are compared in lower case everywhere (the backend does the same). */
    fun normalize(raw: String): String = raw.trim().lowercase()
}

class AdminRepository(private val auth: AuthRepository) {
    private suspend fun call(action: String, params: Map<String, Any?> = emptyMap()): JSONObject =
        when (val r = auth.call(action, params)) {
            is BackendResult.Ok -> r.data
            is BackendResult.Err -> throw DocsException(r.code, r.message)
        }

    suspend fun list(): List<Invite> {
        val data = call("listInvites")
        return try {
            InviteParser.parseList(data)
        } catch (e: JSONException) {
            throw DocsException("BAD_RESPONSE")
        }
    }

    suspend fun invite(email: String) {
        call("inviteUser", mapOf("email" to InviteValidator.normalize(email)))
    }

    suspend fun revoke(email: String) {
        call("revokeUser", mapOf("email" to InviteValidator.normalize(email)))
    }
}
