package com.educationvault.app.ui

import android.app.Application
import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.educationvault.app.AppContainer
import com.educationvault.app.R
import com.educationvault.app.auth.AuthError
import com.educationvault.app.auth.AuthException
import com.educationvault.app.auth.UserProfile
import com.educationvault.app.docs.Category
import com.educationvault.app.docs.DocItem
import com.educationvault.app.docs.DocsException
import com.educationvault.app.docs.DocumentsRepository
import com.educationvault.app.docs.FileSupport
import com.educationvault.app.docs.FormValidator
import com.educationvault.app.docs.ImageCompressor
import com.educationvault.app.docs.NewDoc
import com.educationvault.app.docs.PickResult
import com.educationvault.app.docs.PickedFile
import com.educationvault.app.docs.PreviewRenderer
import com.educationvault.app.net.MeInfo
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Route {
    data object Home : Route
    data class DocList(val category: String?) : Route
    data object Upload : Route
    data class Detail(val docId: String) : Route
    data class Edit(val docId: String) : Route
    data object Settings : Route
}

sealed interface Preview {
    data object Loading : Preview
    data class Image(val bitmap: Bitmap) : Preview
    data class Pdf(val page: Bitmap, val index: Int, val count: Int) : Preview
    data class Failed(val messageRes: Int) : Preview
}

fun FormValidator.Problem.toRes(): Int = when (this) {
    FormValidator.Problem.FILE_MISSING -> R.string.err_file_missing
    FormValidator.Problem.TITLE_EMPTY -> R.string.err_title_empty
    FormValidator.Problem.TITLE_TOO_LONG -> R.string.err_title_long
    FormValidator.Problem.CATEGORY_MISSING -> R.string.err_category_missing
    FormValidator.Problem.YEAR_INVALID -> R.string.err_year_invalid
    FormValidator.Problem.DATE_INVALID -> R.string.err_date_invalid
    FormValidator.Problem.NOTES_TOO_LONG -> R.string.err_notes_long
}

private val SESSION_ENDING = setOf(
    "TOKEN_EXPIRED", "UNAUTHENTICATED", "ACCOUNT_DISABLED", "NOT_INVITED", "EMAIL_NOT_VERIFIED", "FORBIDDEN"
)

private fun docsErrorRes(code: String): Int = when (code) {
    "FILE_TOO_LARGE" -> R.string.err_file_too_large
    "UNSUPPORTED_TYPE" -> R.string.err_unsupported_type
    "NOT_FOUND" -> R.string.err_doc_not_found
    "LIMIT_REACHED" -> R.string.err_limit_reached
    "BAD_REQUEST" -> R.string.err_bad_request
    "METADATA_CORRUPT", "STORAGE_ERROR", "STORAGE_UNAVAILABLE" -> R.string.err_storage
    "UPSTREAM_ERROR", "BUSY", "REQUEST_TOO_LARGE" -> R.string.err_server
    "NOT_CONFIGURED" -> R.string.err_backend_not_configured
    else -> R.string.err_unknown
}

class DocsViewModel(app: Application, val me: MeInfo, val profile: UserProfile) : AndroidViewModel(app) {

    class Factory(
        private val app: Application,
        private val me: MeInfo,
        private val profile: UserProfile
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DocsViewModel(app, me, profile) as T
    }

    private val repo = DocumentsRepository(AppContainer.auth(app))
    private val cameraDir = File(app.cacheDir, "camera")
    private val pdfFile = File(app.cacheDir, "ev_preview.pdf")

    /** Set by the UI: called when the login is no longer valid. */
    var onSessionEnded: (() -> Unit)? = null

    // ----- navigation -----
    val stack = mutableStateListOf<Route>(Route.Home)
    val route: Route get() = stack.last()

    fun push(r: Route) {
        clearMessages()
        stack.add(r)
    }

    fun pop() {
        if (stack.size <= 1) return
        val leaving = stack.removeAt(stack.lastIndex)
        if (leaving is Route.Detail) releasePreview()
    }

    // ----- shared state -----
    var busy by mutableStateOf(false)
        private set
    var errorRes by mutableStateOf<Int?>(null)
        private set
    var infoRes by mutableStateOf<Int?>(null)
        private set

    fun clearMessages() {
        errorRes = null
        infoRes = null
    }

    private fun launchBusy(block: suspend () -> Unit) {
        if (busy) return // ignores duplicate taps
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

    private fun resFor(e: Exception): Int = when (e) {
        is AuthException -> e.error.toRes()
        is DocsException -> docsErrorRes(e.code)
        else -> R.string.err_unknown
    }

    private fun endsSession(e: Exception): Boolean = when (e) {
        is AuthException -> e.error == AuthError.SESSION_EXPIRED
        is DocsException -> e.code in SESSION_ENDING
        else -> false
    }

    /** Returns the message to show, or ends the session (back to the login screen). */
    private fun handle(e: Exception): Int {
        if (endsSession(e)) onSessionEnded?.invoke()
        return resFor(e)
    }

    override fun onCleared() {
        runCatching { pdfFile.delete() }
        runCatching { cameraDir.deleteRecursively() }
    }

    // ----- categories -----
    var categories by mutableStateOf<List<Category>>(emptyList())
        private set

    fun refreshCategories() {
        viewModelScope.launch {
            try {
                categories = repo.categories()
            } catch (e: AuthException) {
                if (endsSession(e)) onSessionEnded?.invoke()
            } catch (e: DocsException) {
                if (endsSession(e)) onSessionEnded?.invoke()
            }
        }
    }

    fun addCategory(name: String) {
        launchBusy {
            try {
                val c = repo.addCategory(name.trim())
                categories = categories + c
            } catch (e: AuthException) {
                errorRes = handle(e)
            } catch (e: DocsException) {
                errorRes = handle(e)
            }
        }
    }

    // ----- home overview (file counts per category) -----
    var counts by mutableStateOf<Map<String, Int>>(emptyMap())
        private set
    var totalDocs by mutableStateOf<Int?>(null)
        private set
    private var overviewStale = true
    private var overviewLoading = false
    private var overviewVersion = 0

    private fun markOverviewStale() {
        overviewVersion++
        overviewStale = true
    }

    /** Counts documents per category by reading the list once (no backend change needed). */
    fun refreshOverviewIfNeeded() {
        if (!overviewStale || overviewLoading) return
        overviewLoading = true
        val version = overviewVersion
        viewModelScope.launch {
            try {
                val map = mutableMapOf<String, Int>()
                var total = 0
                var cursor: String? = null
                var pages = 0
                do {
                    val page = repo.list(null, "", cursor, 100)
                    for (d in page.items) map[d.category] = (map[d.category] ?: 0) + 1
                    total += page.items.size
                    cursor = page.nextCursor
                    pages++
                } while (cursor != null && pages < 10)
                counts = map
                totalDocs = total
                if (version == overviewVersion) overviewStale = false
            } catch (e: AuthException) {
                if (endsSession(e)) onSessionEnded?.invoke()
            } catch (e: DocsException) {
                if (endsSession(e)) onSessionEnded?.invoke()
            } finally {
                overviewLoading = false
                // something changed while we were counting: count again
                if (overviewStale && version != overviewVersion) refreshOverviewIfNeeded()
            }
        }
    }

    // ----- document list -----
    val items = mutableStateListOf<DocItem>()
    var nextCursor by mutableStateOf<String?>(null)
        private set
    var total by mutableIntStateOf(0)
        private set
    var listLoading by mutableStateOf(false)
        private set
    var listLoaded by mutableStateOf(false)
        private set
    var listErrorRes by mutableStateOf<Int?>(null)
        private set
    var query by mutableStateOf("")
        private set
    var categoryFilter by mutableStateOf<String?>(null)
        private set

    private var listToken = 0
    private var searchJob: Job? = null

    /** Set when the list is opened from the home search bar, so the keyboard opens at once. */
    var focusSearch = false

    fun consumeFocusSearch() {
        focusSearch = false
    }

    fun openList(category: String?, focusSearch: Boolean = false) {
        this.focusSearch = focusSearch
        categoryFilter = category
        query = ""
        listLoaded = false
        push(Route.DocList(category))
        loadList(true)
    }

    fun onQueryChange(q: String) {
        query = q
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(400)
            loadList(true)
        }
    }

    fun clearCategoryFilter() {
        categoryFilter = null
        loadList(true)
    }

    fun loadList(reset: Boolean) {
        if (!reset && (listLoading || nextCursor == null)) return
        val token = if (reset) ++listToken else listToken
        if (reset) {
            items.clear()
            nextCursor = null
        }
        val cursor = if (reset) null else nextCursor
        listLoading = true
        listErrorRes = null
        viewModelScope.launch {
            try {
                val page = repo.list(categoryFilter, query, cursor)
                if (token == listToken) {
                    items.addAll(page.items)
                    nextCursor = page.nextCursor
                    total = page.total
                    listLoaded = true
                }
            } catch (e: AuthException) {
                if (token == listToken) listErrorRes = handle(e)
            } catch (e: DocsException) {
                if (token == listToken) listErrorRes = handle(e)
            } finally {
                if (token == listToken) listLoading = false
            }
        }
    }

    // ----- upload -----
    var picked by mutableStateOf<PickedFile?>(null)
        private set

    fun openUpload() {
        picked = null
        push(Route.Upload)
    }

    fun onFilePicked(uri: Uri?) {
        if (uri == null) return
        launchBusy {
            val resolver = getApplication<Application>().contentResolver
            when (val r = withContext(Dispatchers.IO) { readPicked(resolver, uri, me.maxUploadBytes) }) {
                is PickResult.Ok -> picked = r.file
                PickResult.TooLarge -> errorRes = R.string.err_file_too_large
                PickResult.Unsupported -> errorRes = R.string.err_unsupported_type
                PickResult.Failed -> errorRes = R.string.err_file_read
            }
        }
    }

    private fun readPicked(resolver: ContentResolver, uri: Uri, limit: Long): PickResult {
        return try {
            var name = ""
            var size = -1L
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(0).orEmpty()
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
            val mime = FileSupport.normalizeMime(resolver.getType(uri), name)
                ?: return PickResult.Unsupported
            if (size > limit) return PickResult.TooLarge
            val stream = resolver.openInputStream(uri) ?: return PickResult.Failed
            // readLimited returns null when the file is bigger than the limit (never truncates)
            val bytes = stream.use { FileSupport.readLimited(it, limit) } ?: return PickResult.TooLarge
            if (bytes.isEmpty()) PickResult.Failed else PickResult.Ok(PickedFile(name.ifEmpty { "document" }, mime, bytes))
        } catch (e: IOException) {
            PickResult.Failed
        } catch (e: SecurityException) {
            PickResult.Failed
        }
    }

    fun upload(
        title: String, category: String?, institution: String,
        yearText: String, issueDate: String, notes: String
    ) {
        val file = picked
        val problem = FormValidator.validate(file != null, title, category, yearText, issueDate, notes)
        if (problem != null || file == null || category == null) {
            infoRes = null
            errorRes = (problem ?: FormValidator.Problem.FILE_MISSING).toRes()
            return
        }
        launchBusy {
            try {
                repo.upload(
                    NewDoc(
                        title = title.trim(), category = category, mimeType = file.mime, fileName = file.name,
                        bytes = file.bytes, institution = institution.trim(),
                        passingYear = yearText.trim().toIntOrNull(),
                        issueDate = issueDate.trim().ifEmpty { null }, notes = notes.trim()
                    )
                )
                picked = null
                listLoaded = false
                markOverviewStale()
                pop()
                infoRes = R.string.upload_done
            } catch (e: AuthException) {
                errorRes = handle(e)
            } catch (e: DocsException) {
                errorRes = handle(e)
            }
        }
    }

    // ----- detail / preview -----
    var detailDoc by mutableStateOf<DocItem?>(null)
        private set
    var preview by mutableStateOf<Preview?>(null)
        private set
    private var previewBytes: ByteArray? = null
    private var previewJob: Job? = null

    val canSave: Boolean get() = previewBytes != null

    fun openDetail(item: DocItem) {
        detailDoc = item
        push(Route.Detail(item.id))
        loadPreview(item.id)
    }

    private fun loadPreview(docId: String) {
        previewJob?.cancel()
        preview = Preview.Loading
        previewBytes = null
        previewJob = viewModelScope.launch {
            try {
                val (doc, bytes) = repo.download(docId)
                detailDoc = doc
                previewBytes = bytes
                preview = withContext(Dispatchers.Default) { buildPreview(doc.mimeType, bytes) }
            } catch (e: AuthException) {
                preview = Preview.Failed(handle(e))
            } catch (e: DocsException) {
                preview = Preview.Failed(handle(e))
            }
        }
    }

    private fun buildPreview(mime: String, bytes: ByteArray): Preview = when (mime) {
        "application/pdf" -> {
            pdfFile.writeBytes(bytes)
            val r = PreviewRenderer.renderPdfPage(pdfFile, 0)
            if (r != null) Preview.Pdf(r.bitmap, 0, r.pageCount) else Preview.Failed(R.string.err_preview_failed)
        }
        "image/jpeg", "image/png" -> {
            val bmp = PreviewRenderer.decodeImage(bytes)
            if (bmp != null) Preview.Image(bmp) else Preview.Failed(R.string.err_preview_failed)
        }
        else -> Preview.Failed(R.string.err_preview_failed)
    }

    fun pdfPage(delta: Int) {
        val cur = preview as? Preview.Pdf ?: return
        val target = (cur.index + delta).coerceIn(0, cur.count - 1)
        if (target == cur.index) return
        viewModelScope.launch {
            val r = withContext(Dispatchers.Default) { PreviewRenderer.renderPdfPage(pdfFile, target) }
            if (r != null) preview = Preview.Pdf(r.bitmap, target, r.pageCount)
        }
    }

    private fun releasePreview() {
        previewJob?.cancel()
        preview = null
        previewBytes = null
        runCatching { pdfFile.delete() }
    }

    fun saveToUri(uri: Uri?) {
        val bytes = previewBytes
        if (uri == null || bytes == null) return
        launchBusy {
            val resolver = getApplication<Application>().contentResolver
            val ok = withContext(Dispatchers.IO) {
                try {
                    resolver.openOutputStream(uri)?.use { it.write(bytes) } != null
                } catch (e: IOException) {
                    false
                }
            }
            if (ok) infoRes = R.string.download_done else errorRes = R.string.err_save_failed
        }
    }

    // ----- edit / delete -----
    fun updateDoc(
        docId: String, title: String, category: String?, institution: String,
        yearText: String, issueDate: String, notes: String
    ) {
        val problem = FormValidator.validate(true, title, category, yearText, issueDate, notes)
        if (problem != null || category == null) {
            infoRes = null
            errorRes = (problem ?: FormValidator.Problem.CATEGORY_MISSING).toRes()
            return
        }
        launchBusy {
            try {
                val doc = repo.update(
                    docId,
                    mapOf(
                        "title" to title.trim(),
                        "category" to category,
                        "institution" to institution.trim(),
                        "passingYear" to yearText.trim().toIntOrNull(),
                        "issueDate" to issueDate.trim().ifEmpty { null },
                        "notes" to notes.trim()
                    )
                )
                detailDoc = doc
                val i = items.indexOfFirst { it.id == doc.id }
                if (i >= 0) items[i] = doc
                markOverviewStale()
                pop()
                infoRes = R.string.edit_done
            } catch (e: AuthException) {
                errorRes = handle(e)
            } catch (e: DocsException) {
                errorRes = handle(e)
            }
        }
    }

    fun deleteDoc(docId: String) {
        launchBusy {
            try {
                repo.delete(docId)
                items.removeAll { it.id == docId }
                total = (total - 1).coerceAtLeast(0)
                markOverviewStale()
                pop()
                infoRes = R.string.deleted_done
            } catch (e: AuthException) {
                errorRes = handle(e)
            } catch (e: DocsException) {
                errorRes = handle(e)
            }
        }
    }

    // ----- camera -----
    private var cameraFile: File? = null

    /** A fresh private file the camera app may write into, or null if it cannot be created. */
    fun newCameraUri(): Uri? = try {
        val app = getApplication<Application>()
        cameraDir.mkdirs()
        val f = File(cameraDir, ImageCompressor.cameraFileName(System.currentTimeMillis()))
        cameraFile = f
        FileProvider.getUriForFile(app, app.packageName + ".fileprovider", f)
    } catch (e: Exception) {
        null
    }

    fun onCameraUnavailable() {
        cameraFile?.delete()
        cameraFile = null
        infoRes = null
        errorRes = R.string.err_no_camera
    }

    fun onCameraResult(taken: Boolean) {
        val f = cameraFile
        cameraFile = null
        if (!taken) {
            f?.delete()
            return
        }
        if (f == null || !f.exists()) {
            errorRes = R.string.err_file_read
            return
        }
        launchBusy {
            val bytes = withContext(Dispatchers.IO) {
                val b = ImageCompressor.compressJpeg(f)
                f.delete()
                b
            }
            if (bytes == null || bytes.isEmpty()) {
                errorRes = R.string.err_file_read
            } else if (bytes.size > me.maxUploadBytes) {
                errorRes = R.string.err_file_too_large
            } else {
                picked = PickedFile(f.name, "image/jpeg", bytes)
            }
        }
    }

    // ----- settings -----
    fun sendPasswordResetToSelf() {
        launchBusy {
            try {
                AppContainer.auth(getApplication<Application>()).sendPasswordReset(me.email)
                infoRes = R.string.reset_sent
            } catch (e: AuthException) {
                errorRes = e.error.toRes()
            }
        }
    }

    // Must stay the LAST member: it uses properties declared above.
    init {
        runCatching { pdfFile.delete() } // never leave an old decrypted copy behind
        runCatching { cameraDir.deleteRecursively() }
        refreshCategories()
    }
}
