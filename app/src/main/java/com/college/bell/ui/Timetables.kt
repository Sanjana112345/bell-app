package com.college.bell.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.college.bell.*

/**
 * Timetables tab. Two levels: the list of timetables, and the events inside one.
 * [editing] holds the id of the timetable being opened, or null for the list.
 */
@Composable
fun TimetablesScreen(cfg: Config, update: (Config) -> Unit) {
    var editing by remember { mutableStateOf<String?>(null) }
    val open = cfg.timetable(editing)

    if (open == null) TimetableList(cfg, update) { editing = it }
    else EventList(cfg, open, update) { editing = null }
}

@Composable
private fun TimetableList(cfg: Config, update: (Config) -> Unit, onOpen: (String) -> Unit) {
    var renaming by remember { mutableStateOf<Timetable?>(null) }
    var deleting by remember { mutableStateOf<Timetable?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Timetables", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        Hint("A timetable is a named set of bells. Assign it to days on the Calendar tab.")

        LazyColumn(Modifier.weight(1f)) {
            items(cfg.timetables, key = { it.id }) { tt ->
                val usedBy = daysUsing(cfg, tt.id)
                ListItem(
                    headlineContent = { Text(tt.name, fontWeight = FontWeight.Bold) },
                    supportingContent = {
                        Text("${tt.events.size} bells" +
                            if (usedBy.isEmpty()) "  ·  not assigned to any day"
                            else "  ·  used by ${usedBy.size} day(s)")
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { renaming = tt }) {
                                Icon(Icons.Filled.Edit, "Rename")
                            }
                            IconButton(onClick = {
                                // A copy is fully independent: new ids all the way down,
                                // so editing it can never touch the original.
                                val copy = tt.copy(
                                    id = newId(),
                                    name = "${tt.name} (copy)",
                                    events = tt.events.map { it.copy(id = newId()) }
                                )
                                update(cfg.copy(timetables = cfg.timetables + copy))
                            }) { Icon(Icons.Filled.ContentCopy, "Duplicate") }
                            IconButton(onClick = { deleting = tt }) {
                                Icon(Icons.Filled.Delete, "Delete",
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                    modifier = Modifier.clickable { onOpen(tt.id) }
                )
                HorizontalDivider()
            }
        }

        Button(
            onClick = {
                val tt = Timetable(name = "Timetable ${cfg.timetables.size + 1}")
                update(cfg.copy(timetables = cfg.timetables + tt))
            },
            modifier = Modifier.fillMaxWidth()
        ) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("New timetable") }
    }

    renaming?.let { tt ->
        TextPrompt("Rename timetable", tt.name, { renaming = null }) { name ->
            update(cfg.copy(timetables = cfg.timetables.map {
                if (it.id == tt.id) it.copy(name = name) else it
            }))
        }
    }

    deleting?.let { tt ->
        val days = daysUsing(cfg, tt.id)
        if (days.isNotEmpty()) {
            // Refuse rather than silently leaving days pointing at nothing.
            AlertDialog(
                onDismissRequest = { deleting = null },
                title = { Text("Cannot delete '${tt.name}'") },
                text = {
                    Text("It is still assigned to: ${days.joinToString(", ")}.\n\n" +
                        "Change those assignments on the Calendar tab first.")
                },
                confirmButton = { TextButton(onClick = { deleting = null }) { Text("OK") } }
            )
        } else {
            ConfirmDialog(
                title = "Delete '${tt.name}'?",
                message = "${tt.events.size} bells will be deleted. This cannot be undone.",
                confirmLabel = "Delete", destructive = true,
                onConfirm = {
                    update(cfg.copy(timetables = cfg.timetables.filter { it.id != tt.id }))
                },
                onDismiss = { deleting = null }
            )
        }
    }
}

/** Human-readable list of the days/dates currently pointing at a timetable. */
private fun daysUsing(cfg: Config, id: String): List<String> =
    cfg.weekdayMap.filterValues { it == id }.keys.map { it.lowercase().replaceFirstChar(Char::uppercase) } +
        cfg.dateOverrides.filterValues { it == id }.keys.sorted()

@Composable
private fun EventList(
    cfg: Config,
    tt: Timetable,
    update: (Config) -> Unit,
    onBack: () -> Unit,
) {
    var editing by remember { mutableStateOf<BellEvent?>(null) }
    var deleting by remember { mutableStateOf<BellEvent?>(null) }

    fun saveEvents(events: List<BellEvent>) {
        update(cfg.copy(timetables = cfg.timetables.map {
            if (it.id == tt.id) it.copy(events = events.sortedBy(BellEvent::time)) else it
        }))
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(tt.name, style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
        }

        if (tt.events.isEmpty()) Hint("No bells yet. Add the first one below.")

        LazyColumn(Modifier.weight(1f)) {
            items(tt.events, key = { it.id }) { e ->
                ListItem(
                    headlineContent = {
                        Text(
                            prettyTime(e.time),
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (e.enabled) null else TextDecoration.LineThrough
                        )
                    },
                    supportingContent = {
                        val sound = if (e.soundUri == null) "default sound" else "custom sound"
                        val state = if (e.enabled) "" else "  ·  DISABLED"
                        Text("${e.label}  ·  ${e.durationSec}s  ·  $sound$state")
                    },
                    trailingContent = {
                        Row {
                            Switch(
                                checked = e.enabled,
                                onCheckedChange = { on ->
                                    saveEvents(tt.events.map {
                                        if (it.id == e.id) it.copy(enabled = on) else it
                                    })
                                }
                            )
                            IconButton(onClick = { deleting = e }) {
                                Icon(Icons.Filled.Delete, "Delete",
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                    modifier = Modifier.clickable { editing = e }
                )
                HorizontalDivider()
            }
        }

        Button(
            onClick = { editing = BellEvent(durationSec = cfg.defaultDurationSec) },
            modifier = Modifier.fillMaxWidth()
        ) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("Add bell") }
    }

    editing?.let { e ->
        EventEditor(
            event = e,
            others = tt.events.filter { it.id != e.id },
            onDismiss = { editing = null },
            onSave = { saved ->
                val exists = tt.events.any { it.id == saved.id }
                saveEvents(
                    if (exists) tt.events.map { if (it.id == saved.id) saved else it }
                    else tt.events + saved
                )
            }
        )
    }

    deleting?.let { e ->
        ConfirmDialog(
            title = "Delete this bell?",
            message = "${e.label} at ${prettyTime(e.time)} will be removed.",
            confirmLabel = "Delete", destructive = true,
            onConfirm = { saveEvents(tt.events.filter { it.id != e.id }) },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun EventEditor(
    event: BellEvent,
    others: List<BellEvent>,
    onDismiss: () -> Unit,
    onSave: (BellEvent) -> Unit,
) {
    val context = LocalContext.current
    var draft by remember { mutableStateOf(event) }

    // Picks an audio file and keeps permission to read it after a reboot —
    // without takePersistableUriPermission the sound silently stops working later.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            draft = draft.copy(soundUri = uri.toString())
        }
    }

    val clash = others.any { it.time == draft.time }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bell") },
        text = {
            Column {
                OutlinedTextField(
                    value = draft.label,
                    onValueChange = { draft = draft.copy(label = it) },
                    label = { Text("Name (e.g. Period 3, Lunch)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { pickTime(context, draft.time) { draft = draft.copy(time = it) } },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Time: ${prettyTime(draft.time)}") }

                if (clash) {
                    Text(
                        "Another bell is already set for this time. Two bells at the same " +
                            "minute will overlap — change one of them.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = draft.durationSec.toString(),
                    onValueChange = {
                        draft = draft.copy(durationSec = it.toIntOrNull()?.coerceIn(1, 300)
                            ?: draft.durationSec)
                    },
                    label = { Text("Ring for (seconds)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("audio/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (draft.soundUri == null) "Sound: built-in bell" else "Sound: custom file") }

                Row {
                    TextButton(onClick = { draft = draft.copy(soundUri = null) }) {
                        Text("Use built-in")
                    }
                    TextButton(onClick = {
                        BellService.ring(context, "Preview", draft.soundUri, draft.durationSec, manual = true)
                    }) { Text("Preview") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !clash && draft.label.isNotBlank(),
                onClick = { onSave(draft); onDismiss() }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun TextPrompt(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onSave(text); onDismiss() }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
