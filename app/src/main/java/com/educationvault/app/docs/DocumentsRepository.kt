package com.educationvault.app.docs

import android.util.Base64
import com.educationvault.app.auth.AuthRepository
import com.educationvault.app.net.BackendResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/** A backend error the caller may want to translate (code = the backend's error code). */
class DocsException(val code: String, message: String = code) : Exception(message)

class NewDoc(
    val title: String,
    val category: String,
    val mimeType: String,
    val fileName: String,
    val bytes: ByteArray,
    val institution: String,
    val passingYear: Int?,
    val issueDate: String?,
    val notes: String
)

class DocumentsRepository(private val auth: AuthRepository) {

    private suspend fun call(action: String, params: Map<String, Any?> = emptyMap()): JSONObject =
        when (val r = auth.call(action, params)) {
            is BackendResult.Ok -> r.data
            is BackendResult.Err -> throw DocsException(r.code, r.message)
        }

    private inline fun <T> parsed(block: () -> T): T = try {
        block()
    } catch (e: JSONException) {
        throw DocsException("BAD_RESPONSE")
    }

    suspend fun categories(): List<Category> {
        val data = call("listCategories")
        return parsed { DocParser.parseCategories(data) }
    }

    suspend fun addCategory(name: String): Category {
        val data = call("addCategory", mapOf("name" to name))
        return parsed { DocParser.parseCategory(data.getJSONObject("category")) }
    }

    suspend fun list(category: String?, query: String, cursor: String?, limit: Int = 30): DocPage {
        val p = mutableMapOf<String, Any?>("limit" to limit)
        if (!category.isNullOrEmpty()) p["category"] = category
        if (query.isNotBlank()) p["query"] = query.trim()
        if (cursor != null) p["cursor"] = cursor
        val data = call("listDocs", p)
        return parsed { DocParser.parsePage(data) }
    }

    suspend fun upload(d: NewDoc): DocItem {
        val b64 = withContext(Dispatchers.Default) { Base64.encodeToString(d.bytes, Base64.NO_WRAP) }
        val p = mutableMapOf<String, Any?>(
            "title" to d.title,
            "category" to d.category,
            "mimeType" to d.mimeType,
            "fileName" to d.fileName,
            "contentBase64" to b64,
            "institution" to d.institution,
            "notes" to d.notes
        )
        if (d.passingYear != null) p["passingYear"] = d.passingYear
        if (!d.issueDate.isNullOrEmpty()) p["issueDate"] = d.issueDate
        val data = call("upload", p)
        return parsed { DocParser.parseDoc(data.getJSONObject("doc")) }
    }

    suspend fun download(docId: String): Pair<DocItem, ByteArray> {
        val data = call("download", mapOf("docId" to docId))
        val doc = parsed { DocParser.parseDoc(data.getJSONObject("doc")) }
        val b64 = data.optString("contentBase64")
        val bytes = withContext(Dispatchers.Default) { Base64.decode(b64, Base64.DEFAULT) }
        if (bytes.isEmpty()) throw DocsException("BAD_RESPONSE")
        return doc to bytes
    }

    suspend fun update(docId: String, fields: Map<String, Any?>): DocItem {
        val data = call("updateDoc", fields + ("docId" to docId))
        return parsed { DocParser.parseDoc(data.getJSONObject("doc")) }
    }

    suspend fun delete(docId: String) {
        call("deleteDoc", mapOf("docId" to docId))
    }
}
