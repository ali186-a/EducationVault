package com.educationvault.app.docs

import org.json.JSONObject

data class Category(val id: String, val ur: String, val en: String, val custom: Boolean)

data class DocItem(
    val id: String,
    val title: String,
    val category: String,
    val institution: String,
    val passingYear: Int?,
    val issueDate: String?,
    val notes: String,
    val mimeType: String,
    val size: Long,
    val originalName: String,
    val createdAt: String,
    val updatedAt: String
)

data class DocPage(val items: List<DocItem>, val nextCursor: String?, val total: Int)

fun Category.label(urdu: Boolean): String = if (urdu) ur else en

/** Falls back to the raw id if the category is unknown (e.g. list not loaded yet). */
fun categoryLabel(id: String, categories: List<Category>, urdu: Boolean): String =
    categories.firstOrNull { it.id == id }?.label(urdu) ?: id

/** Pure parsing of backend JSON (unit-tested). Throws JSONException on malformed data. */
object DocParser {
    fun parseDoc(o: JSONObject): DocItem = DocItem(
        id = o.getString("id"),
        title = o.optString("title"),
        category = o.optString("category"),
        institution = o.optString("institution"),
        passingYear = if (o.isNull("passingYear")) null else o.optInt("passingYear"),
        issueDate = if (o.isNull("issueDate")) null else o.optString("issueDate").ifEmpty { null },
        notes = o.optString("notes"),
        mimeType = o.optString("mimeType"),
        size = o.optLong("size"),
        originalName = o.optString("originalName"),
        createdAt = o.optString("createdAt"),
        updatedAt = o.optString("updatedAt")
    )

    fun parsePage(data: JSONObject): DocPage {
        val arr = data.getJSONArray("items")
        val items = (0 until arr.length()).map { parseDoc(arr.getJSONObject(it)) }
        val next = if (data.isNull("nextCursor")) null else data.optString("nextCursor").ifEmpty { null }
        return DocPage(items, next, data.optInt("total", items.size))
    }

    fun parseCategory(o: JSONObject): Category = Category(
        id = o.getString("id"),
        ur = o.optString("ur"),
        en = o.optString("en"),
        custom = o.optBoolean("custom", false)
    )

    fun parseCategories(data: JSONObject): List<Category> {
        val arr = data.getJSONArray("categories")
        return (0 until arr.length()).map { parseCategory(arr.getJSONObject(it)) }
    }
}
