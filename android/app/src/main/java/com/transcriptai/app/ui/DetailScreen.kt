package com.transcriptai.app.ui

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.transcriptai.app.AppViewModel
import com.transcriptai.app.data.RecordingDto
import com.transcriptai.app.data.SegmentDto
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(vm: AppViewModel, nav: NavController, id: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(id) { vm.loadDetail(id); vm.loadChat(id) }
    val rec = vm.detail
    var tab by remember { mutableStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }

    // In-app playback (private bucket -> short-lived presigned URL fetched once).
    val player = rememberRecordingPlayer()
    var audioUrl by remember(id) { mutableStateOf<String?>(null) }
    LaunchedEffect(rec?.id, rec?.objectKey) {
        if (rec?.objectKey != null && audioUrl == null) {
            runCatching { audioUrl = vm.repo.api.getAudioUrl(rec.id).url }
        }
    }
    // While transcription is in flight, poll so the transcript/summary appear automatically (no manual
    // refresh). The effect is keyed on the status, so it stops as soon as the recording is done.
    LaunchedEffect(rec?.transcriptStatus) {
        val pending = setOf("QUEUED", "PROCESSING", "PENDING")
        if (rec != null && rec.transcriptStatus.uppercase() in pending) {
            while (true) {
                kotlinx.coroutines.delay(5000)
                vm.loadDetail(id)
                if (vm.detail?.transcriptStatus?.uppercase() !in pending) break
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(rec?.aiTitle ?: rec?.title ?: "Recording", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (rec != null) {
                        IconButton(onClick = { vm.toggleFavorite(rec.id) }) {
                            Icon(if (rec.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite")
                        }
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            MenuItem("Rename", Icons.Default.Edit) { menuOpen = false; renameOpen = true }
                            MenuItem(if (rec.isPinned) "Unpin" else "Pin", Icons.Default.PushPin) { menuOpen = false; vm.togglePin(rec.id) }
                            MenuItem(if (rec.isArchived) "Unarchive" else "Archive", Icons.Default.Archive) { menuOpen = false; vm.toggleArchive(rec.id) }
                            MenuItem("Regenerate title (AI)", Icons.Default.AutoAwesome) { menuOpen = false; vm.regenerateTitle(rec.id) }
                            MenuItem("Re-transcribe", Icons.Default.Refresh) { menuOpen = false; vm.transcribeNow(rec.id) }
                            HorizontalDivider()
                            MenuItem("Copy transcript", Icons.Default.ContentCopy) { menuOpen = false; copyText(context, transcriptText(rec)) }
                            MenuItem("Share transcript", Icons.Default.Share) { menuOpen = false; shareText(context, transcriptText(rec)) }
                            MenuItem("Export .txt", Icons.Default.Description) { menuOpen = false; scope.launch { exportAndShare(context, vm, rec.id, rec.title, "txt") } }
                            MenuItem("Export .md", Icons.AutoMirrored.Filled.Article) { menuOpen = false; scope.launch { exportAndShare(context, vm, rec.id, rec.title, "md") } }
                            MenuItem("Export .pdf", Icons.Default.PictureAsPdf) { menuOpen = false; scope.launch { exportAndShare(context, vm, rec.id, rec.title, "pdf") } }
                            MenuItem("Export .docx", Icons.Default.Description) { menuOpen = false; scope.launch { exportAndShare(context, vm, rec.id, rec.title, "docx") } }
                            HorizontalDivider()
                            MenuItem("Delete", Icons.Default.Delete, destructive = true) { menuOpen = false; vm.delete(rec.id); nav.popBackStack() }
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (rec == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusChip(rec.transcriptStatus)
                Spacer(Modifier.width(8.dp))
                Text(fmtDuration(rec.durationSec), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            audioUrl?.let { url ->
                PlaybackBar(player, url, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp))
            }
            val tabs = listOf(
                "Transcript" to Icons.AutoMirrored.Filled.Subject,
                "Summary" to Icons.Default.Summarize,
                "Actions" to Icons.Default.Checklist,
                "Chat" to Icons.Default.Forum,
            )
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, (label, icon) ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icon, null, Modifier.size(20.dp)) },
                        text = {
                            // Never wrap: single line, no soft-wrap, ellipsis only as a last resort.
                            Text(label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                        },
                    )
                }
            }
            when (tab) {
                0 -> TranscriptTab(vm, rec, player, audioUrl)
                1 -> SummaryTab(vm, rec)
                2 -> ActionsTab(vm, rec)
                3 -> ChatTab(vm, rec)
            }
        }
    }

    if (renameOpen) {
        var newName by remember { mutableStateOf(rec?.title ?: "") }
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("Rename") },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { rec?.let { vm.rename(it.id, newName) }; renameOpen = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MenuItem(label: String, icon: ImageVector, destructive: Boolean = false, onClick: () -> Unit) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    DropdownMenuItem(
        text = { Text(label, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
        leadingIcon = { Icon(icon, null, tint = tint) },
        onClick = onClick,
    )
}

@Composable
private fun TranscriptTab(vm: AppViewModel, rec: RecordingDto, player: RecordingPlayer, audioUrl: String?) {
    val segments = rec.transcript?.segments ?: emptyList()
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<SegmentDto?>(null) }
    val showTimestamps = vm.showTimestamps

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("Search within transcript") },
            leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Spacer(Modifier.height(8.dp))
        if (segments.isEmpty()) {
            val raw = rec.transcript?.refinedText ?: rec.transcript?.rawText
            if (raw.isNullOrBlank()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        when (rec.transcriptStatus.uppercase()) {
                            "COMPLETED" -> "No speech detected."
                            "FAILED" -> "Transcription failed. Try Re-transcribe."
                            "UNAVAILABLE" -> "Transcription is unavailable (no API key)."
                            else -> "Transcribing… pull to refresh shortly."
                        }, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) { Text(raw); Spacer(Modifier.height(40.dp)) }
            }
        } else {
            val filtered = if (query.isBlank()) segments else segments.filter { (it.editedText ?: it.text).contains(query, ignoreCase = true) }
            val canSeek = audioUrl != null
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered, key = { it.idx }) { seg ->
                    val isCurrent = canSeek && player.positionMs >= seg.startMs && player.positionMs < seg.endMs
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable(enabled = canSeek) { audioUrl?.let { player.ensureLoaded(it); player.seekTo(seg.startMs.toLong()) } }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                    ) {
                        if (showTimestamps) {
                            Column(Modifier.width(64.dp)) {
                                Text(
                                    fmtMs(seg.startMs),
                                    color = if (canSeek) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                seg.speaker?.let { Text(it, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold) }
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(seg.editedText ?: seg.text, Modifier.weight(1f))
                        IconButton(onClick = { editing = seg }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Edit, "Edit", modifier = Modifier.size(16.dp))
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }

    editing?.let { seg ->
        var text by remember { mutableStateOf(seg.editedText ?: seg.text) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit segment") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { vm.editSegment(rec.id, seg.idx, text); editing = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SummaryTab(vm: AppViewModel, rec: RecordingDto) {
    val types = listOf("BRIEF", "DETAILED", "BULLETS", "MINUTES")
    var selected by remember { mutableStateOf("BRIEF") }
    val context = LocalContext.current
    val summary = rec.summaries.firstOrNull { it.type == selected }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        ScrollableTabRowChips(types, selected) { selected = it }
        Spacer(Modifier.height(12.dp))
        if (summary != null && summary.content.isNotBlank()) {
            Text(summary.content)
        } else {
            Text("No ${selected.lowercase()} summary yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        Row {
            Button(onClick = { vm.regenerateSummary(rec.id, selected) {} }) { Text("Generate ${selected.lowercase()}") }
            Spacer(Modifier.width(8.dp))
            summary?.takeIf { it.content.isNotBlank() }?.let { OutlinedButton(onClick = { copyText(context, it.content) }) { Text("Copy") } }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun ActionsTab(vm: AppViewModel, rec: RecordingDto) {
    val grouped = rec.actionItems.groupBy { it.kind }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        if (rec.actionItems.isEmpty()) {
            item { Text("No action items extracted yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        listOf("ACTION", "DECISION", "TAKEAWAY", "DEADLINE").forEach { kind ->
            val items = grouped[kind].orEmpty()
            if (items.isNotEmpty()) {
                item { SectionTitle(kind.lowercase().replaceFirstChar { it.uppercase() } + "s") }
                items(items, key = { it.id }) { ai ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (kind == "ACTION") {
                            Checkbox(checked = ai.done, onCheckedChange = { vm.toggleActionItem(rec.id, ai.id) })
                        } else {
                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(ai.text)
                            val meta = listOfNotNull(ai.assignee, ai.dueDate).joinToString(" • ")
                            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun ChatTab(vm: AppViewModel, rec: RecordingDto) {
    var input by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        LazyColumn(Modifier.weight(1f)) {
            items(vm.chatMessages) { m ->
                val isUser = m.role.equals("USER", true)
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
                    Surface(
                        color = if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                    ) { Text(m.content, Modifier.padding(10.dp)) }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                placeholder = { Text("Ask about this transcript…") },
                modifier = Modifier.weight(1f), maxLines = 3,
                keyboardOptions = KeyboardOptions(),
            )
            IconButton(onClick = { if (input.isNotBlank()) { vm.sendChat(rec.id, input.trim()); input = "" } }) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send")
            }
        }
    }
}

@Composable
private fun ScrollableTabRowChips(types: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row {
        types.forEach { t ->
            FilterChip(selected = selected == t, onClick = { onSelect(t) }, label = { Text(t.lowercase()) })
            Spacer(Modifier.width(6.dp))
        }
    }
}

// --- helpers -------------------------------------------------------------

private fun transcriptText(rec: RecordingDto): String {
    rec.transcript?.refinedText?.let { if (it.isNotBlank()) return it }
    val segs = rec.transcript?.segments ?: emptyList()
    if (segs.isNotEmpty()) return segs.joinToString("\n") { (it.speaker?.let { s -> "$s: " } ?: "") + (it.editedText ?: it.text) }
    return rec.transcript?.rawText ?: ""
}

private fun copyText(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("transcript", text))
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share"))
}

private suspend fun exportAndShare(context: Context, vm: AppViewModel, id: String, title: String, fmt: String) {
    try {
        val bytes = vm.repo.exportBytes(id, fmt)
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safe = title.replace(Regex("[^A-Za-z0-9._-]+"), "_").ifBlank { "recording" }
        val file = File(dir, "$safe.$fmt")
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = when (fmt) {
                "pdf" -> "application/pdf"
                "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                "md" -> "text/markdown"
                else -> "text/plain"
            }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export $fmt"))
    } catch (e: Exception) {
        vm.error = e.message ?: "Export failed"
    }
}
