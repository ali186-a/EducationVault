package com.educationvault.app.ui

import android.app.Application
import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.educationvault.app.LanguageStore
import com.educationvault.app.R
import com.educationvault.app.docs.Category
import com.educationvault.app.docs.DocItem
import com.educationvault.app.docs.Formatting
import com.educationvault.app.docs.categoryLabel
import com.educationvault.app.docs.label
import com.educationvault.app.net.MeInfo

@Composable
private fun isUrdu(): Boolean = LanguageStore.get(LocalContext.current) == LanguageStore.URDU

// ---------- root ----------

@Composable
fun DocsRoot(authVm: AuthViewModel, me: MeInfo, session: Int) {
    val app = LocalContext.current.applicationContext as Application
    val vm = viewModel<DocsViewModel>(key = "docs-" + me.uid + "-" + session, factory = DocsViewModel.Factory(app, me))
    vm.onSessionEnded = { authVm.onSessionExpired() }
    BackHandler(enabled = vm.stack.size > 1) { if (!vm.busy) vm.pop() }

    when (val r = vm.route) {
        Route.Home -> HomeScreen(authVm, vm)
        Route.Categories -> CategoriesScreen(vm)
        is Route.DocList -> DocListScreen(vm)
        Route.Upload -> UploadScreen(vm)
        is Route.Detail -> DetailScreen(vm, r.docId)
        is Route.Edit -> EditScreen(vm, r.docId)
        Route.Admin -> AdminScreen(vm)
        Route.Settings -> SettingsScreen(authVm, vm)
        Route.Backup -> BackupScreen(vm)
    }
}

@Composable
internal fun DocsScaffold(titleRes: Int, vm: DocsViewModel, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { vm.pop() }, enabled = !vm.busy) { Text(stringResource(R.string.back_button)) }
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(titleRes),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Column(Modifier.fillMaxWidth().weight(1f), content = content)
    }
}

// ---------- home ----------

@Composable
private fun HomeScreen(authVm: AuthViewModel, vm: DocsViewModel) = ScreenFrame {
    Title(R.string.home_title)
    Text(stringResource(R.string.home_signed_in_as, vm.me.email))
    if (vm.me.isAdmin) {
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.home_admin_badge), color = MaterialTheme.colorScheme.primary)
    }
    Messages(vm.errorRes, vm.infoRes)
    Spacer(Modifier.height(16.dp))
    Button(onClick = { vm.openUpload() }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_upload))
    }
    Spacer(Modifier.height(8.dp))
    Button(onClick = { vm.push(Route.Categories) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_categories))
    }
    Spacer(Modifier.height(8.dp))
    Button(onClick = { vm.openList(null) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_all_docs))
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = { vm.openBackupInfo() }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_backup))
    }
    if (vm.me.isAdmin) {
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { vm.openAdmin() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_admin))
        }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = { vm.push(Route.Settings) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_settings))
    }
    Spacer(Modifier.height(24.dp))
    OutlinedButton(onClick = { authVm.signOut() }) { Text(stringResource(R.string.logout_button)) }
}

// ---------- categories ----------

@Composable
private fun CategoriesScreen(vm: DocsViewModel) {
    val urdu = isUrdu()
    var showAdd by remember { mutableStateOf(false) }
    var newName by rememberSaveable { mutableStateOf("") }

    DocsScaffold(R.string.categories_title, vm) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(vm.categories, key = { it.id }) { c ->
                Card(onClick = { vm.openList(c.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text(c.label(urdu), modifier = Modifier.padding(16.dp))
                }
            }
        }
        Column(Modifier.padding(16.dp)) {
            Messages(vm.errorRes, vm.infoRes)
            OutlinedButton(
                onClick = { showAdd = true },
                enabled = !vm.busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.add_category_button)) }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(stringResource(R.string.add_category_title)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.category_name_label)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.addCategory(newName)
                        newName = ""
                        showAdd = false
                    },
                    enabled = newName.isNotBlank()
                ) { Text(stringResource(R.string.save_button)) }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text(stringResource(R.string.cancel_button)) }
            }
        )
    }
}

// ---------- document list + search ----------

@Composable
private fun DocListScreen(vm: DocsViewModel) {
    val urdu = isUrdu()
    DocsScaffold(R.string.docs_title, vm) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = vm.query,
                onValueChange = vm::onQueryChange,
                label = { Text(stringResource(R.string.search_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            val filter = vm.categoryFilter
            if (filter != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.filter_label, categoryLabel(filter, vm.categories, urdu)),
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = { vm.clearCategoryFilter() }) { Text(stringResource(R.string.clear_filter)) }
                }
            }
            if (vm.listLoaded) {
                Text(stringResource(R.string.docs_count, vm.total), style = MaterialTheme.typography.bodySmall)
            }
        }
        vm.listErrorRes?.let { res ->
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(res), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { vm.loadList(true) }) { Text(stringResource(R.string.retry_button)) }
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(vm.items, key = { it.id }) { d -> DocCard(d, vm.categories, urdu) { vm.openDetail(d) } }
            item {
                when {
                    vm.listLoading -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                    vm.nextCursor != null -> OutlinedButton(
                        onClick = { vm.loadList(false) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.load_more)) }
                    vm.listLoaded && vm.items.isEmpty() && vm.listErrorRes == null -> EmptyState(
                        filtered = vm.query.isNotBlank() || vm.categoryFilter != null
                    )
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun EmptyState(filtered: Boolean) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (filtered) {
            Text(stringResource(R.string.empty_filtered), textAlign = TextAlign.Center)
        } else {
            Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.empty_body), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun DocCard(d: DocItem, categories: List<Category>, urdu: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(d.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                categoryLabel(d.category, categories, urdu) + " · " + Formatting.mimeLabel(d.mimeType) +
                    " · " + Formatting.formatSize(d.size),
                style = MaterialTheme.typography.bodySmall
            )
            val extra = listOfNotNull(d.institution.ifEmpty { null }, Formatting.dateOnly(d.createdAt).ifEmpty { null })
            if (extra.isNotEmpty()) {
                Text(extra.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ---------- shared form ----------

@Composable
private fun CategoryDropdown(
    categories: List<Category>,
    selected: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    val urdu = isUrdu()
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.category_label), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (selected == null) stringResource(R.string.choose_category)
                else categoryLabel(selected, categories, urdu)
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            categories.forEach { c ->
                DropdownMenuItem(
                    text = { Text(c.label(urdu)) },
                    onClick = {
                        onSelect(c.id)
                        open = false
                    }
                )
            }
        }
    }
}

@Composable
internal fun TextInput(
    value: String,
    onChange: (String) -> Unit,
    labelRes: Int,
    enabled: Boolean,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(labelRes)) },
        singleLine = singleLine,
        minLines = minLines,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth()
    )
}

// ---------- upload ----------

@Composable
private fun UploadScreen(vm: DocsViewModel) {
    var title by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var institution by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf("") }
    var issueDate by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        vm.onFilePicked(uri)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        vm.onCameraResult(taken)
    }
    LaunchedEffect(vm.picked) {
        val f = vm.picked
        if (f != null && title.isBlank()) title = Formatting.titleFromFileName(f.name)
    }

    DocsScaffold(R.string.upload_title, vm) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.upload_limit_note, Formatting.formatSize(vm.me.maxUploadBytes)),
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(
                onClick = { picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png")) },
                enabled = !vm.busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.pick_file_button)) }
            OutlinedButton(
                onClick = {
                    val uri = vm.newCameraUri()
                    if (uri == null) {
                        vm.onCameraUnavailable()
                    } else {
                        try {
                            camera.launch(uri)
                        } catch (e: ActivityNotFoundException) {
                            vm.onCameraUnavailable()
                        }
                    }
                },
                enabled = !vm.busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.action_camera)) }
            val f = vm.picked
            Text(
                if (f == null) stringResource(R.string.no_file)
                else stringResource(R.string.file_selected, f.name, Formatting.formatSize(f.bytes.size.toLong()))
            )
            TextInput(title, { title = it }, R.string.title_label, !vm.busy)
            CategoryDropdown(vm.categories, category, !vm.busy) { category = it }
            TextInput(institution, { institution = it }, R.string.institution_label, !vm.busy)
            TextInput(year, { year = it }, R.string.year_label, !vm.busy, KeyboardType.Number)
            TextInput(issueDate, { issueDate = it }, R.string.issue_date_label, !vm.busy)
            TextInput(notes, { notes = it }, R.string.notes_label, !vm.busy, singleLine = false, minLines = 3)
            Messages(vm.errorRes, vm.infoRes)
            if (vm.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.uploading), style = MaterialTheme.typography.bodySmall)
            }
            BusyButton(R.string.upload_button, vm.busy) {
                vm.upload(title, category, institution, year, issueDate, notes)
            }
        }
    }
}

// ---------- detail + preview ----------

@Composable
private fun MetaLine(labelRes: Int, value: String) {
    if (value.isNotBlank()) {
        Text(stringResource(R.string.meta_line, stringResource(labelRes), value))
    }
}

@Composable
private fun DetailScreen(vm: DocsViewModel, docId: String) {
    val urdu = isUrdu()
    val doc = vm.detailDoc?.takeIf { it.id == docId } ?: vm.items.firstOrNull { it.id == docId }
    var confirmDelete by remember { mutableStateOf(false) }

    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(doc?.mimeType ?: "application/octet-stream")
    ) { uri -> vm.saveToUri(uri) }

    DocsScaffold(R.string.detail_title, vm) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (doc == null) {
                CircularProgressIndicator()
            } else {
                Text(doc.title, style = MaterialTheme.typography.headlineSmall)
                PreviewArea(vm)
                MetaLine(R.string.meta_category, categoryLabel(doc.category, vm.categories, urdu))
                MetaLine(R.string.meta_type, Formatting.mimeLabel(doc.mimeType))
                MetaLine(R.string.meta_size, Formatting.formatSize(doc.size))
                MetaLine(R.string.meta_uploaded, Formatting.dateOnly(doc.createdAt))
                MetaLine(R.string.meta_institution, doc.institution)
                MetaLine(R.string.meta_year, doc.passingYear?.toString().orEmpty())
                MetaLine(R.string.meta_issue, doc.issueDate.orEmpty())
                MetaLine(R.string.meta_notes, doc.notes)
                Messages(vm.errorRes, vm.infoRes)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { saver.launch(Formatting.suggestedFileName(doc.title, doc.mimeType)) },
                    enabled = !vm.busy && vm.canSave,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.download_button)) }
                OutlinedButton(
                    onClick = { vm.push(Route.Edit(doc.id)) },
                    enabled = !vm.busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.edit_button)) }
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    enabled = !vm.busy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.delete_button)) }
            }
        }
    }

    if (confirmDelete && doc != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_body, doc.title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteDoc(doc.id)
                }) { Text(stringResource(R.string.delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel_button)) }
            }
        )
    }
}

@Composable
private fun PreviewArea(vm: DocsViewModel) {
    when (val p = vm.preview) {
        null -> Unit
        Preview.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator()
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.preview_loading))
        }
        is Preview.Image -> {
            val img = remember(p.bitmap) { p.bitmap.asImageBitmap() }
            Image(bitmap = img, contentDescription = null, modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
        }
        is Preview.Pdf -> {
            val img = remember(p.page) { p.page.asImageBitmap() }
            Image(bitmap = img, contentDescription = null, modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { vm.pdfPage(-1) }, enabled = p.index > 0) { Text(stringResource(R.string.prev_page)) }
                Text(
                    stringResource(R.string.page_of, p.index + 1, p.count),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                TextButton(onClick = { vm.pdfPage(1) }, enabled = p.index < p.count - 1) {
                    Text(stringResource(R.string.next_page))
                }
            }
        }
        is Preview.Failed -> Text(stringResource(p.messageRes), color = MaterialTheme.colorScheme.error)
    }
}

// ---------- edit ----------

@Composable
private fun EditScreen(vm: DocsViewModel, docId: String) {
    val doc = vm.detailDoc?.takeIf { it.id == docId } ?: vm.items.firstOrNull { it.id == docId }
    if (doc == null) {
        DocsScaffold(R.string.edit_title, vm) { Text(stringResource(R.string.err_doc_not_found), Modifier.padding(16.dp)) }
        return
    }
    var title by rememberSaveable(docId) { mutableStateOf(doc.title) }
    var category by rememberSaveable(docId) { mutableStateOf<String?>(doc.category) }
    var institution by rememberSaveable(docId) { mutableStateOf(doc.institution) }
    var year by rememberSaveable(docId) { mutableStateOf(doc.passingYear?.toString().orEmpty()) }
    var issueDate by rememberSaveable(docId) { mutableStateOf(doc.issueDate.orEmpty()) }
    var notes by rememberSaveable(docId) { mutableStateOf(doc.notes) }

    DocsScaffold(R.string.edit_title, vm) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextInput(title, { title = it }, R.string.title_label, !vm.busy)
            CategoryDropdown(vm.categories, category, !vm.busy) { category = it }
            TextInput(institution, { institution = it }, R.string.institution_label, !vm.busy)
            TextInput(year, { year = it }, R.string.year_label, !vm.busy, KeyboardType.Number)
            TextInput(issueDate, { issueDate = it }, R.string.issue_date_label, !vm.busy)
            TextInput(notes, { notes = it }, R.string.notes_label, !vm.busy, singleLine = false, minLines = 3)
            Messages(vm.errorRes, vm.infoRes)
            BusyButton(R.string.save_button, vm.busy) {
                vm.updateDoc(docId, title, category, institution, year, issueDate, notes)
            }
        }
    }
}
