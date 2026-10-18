package com.college.bell.ui

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.college.bell.Config
import com.college.bell.Store

/**
 * Settings, plus the device checks that decide whether this thing is reliable.
 * The checks matter more than the settings: a bell app that Android is allowed
 * to put to sleep will miss bells, and that is not a bug anyone can fix in code.
 */
@Composable
fun SettingsScreen(cfg: Config, update: (Config) -> Unit) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    var settingPin by remember { mutableStateOf(false) }

    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)!!.use {
                    it.write(Store.export(cfg).toByteArray())
                }
            }
                .onSuccess { message = "Backup saved." }
                .onFailure { message = "Could not save: ${it.message}" }
        }
    }

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)!!.bufferedReader().readText()
            }.getOrNull()
            val restored = text?.let(Store::import)
            if (restored == null) {
                message = "That file is not a valid College Bell backup. Nothing was changed."
            } else {
                update(restored)
                message = "Backup restored. ${restored.timetables.size} timetable(s) loaded."
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)

        // ---- device checks --------------------------------------------------
        SectionTitle("Device checks")
        Hint("These three things silently stop bells. Check them once, on the tablet " +
            "that stays in the office.")

        val pm = context.getSystemService(PowerManager::class.java)
        val unrestricted = pm.isIgnoringBatteryOptimizations(context.packageName)

        ListItem(
            headlineContent = { Text("Battery optimisation") },
            supportingContent = {
                Text(
                    if (unrestricted) "Off for this app — correct."
                    else "ON — Android may stop bells. Tap to fix.",
                    color = if (unrestricted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            },
            trailingContent = {
                if (!unrestricted) TextButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        )
                    }
                }) { Text("Fix") }
            }
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text("Autostart after reboot") },
            supportingContent = {
                Text("Some tablets (Xiaomi, Oppo, Vivo, Realme) need this switched on " +
                    "by hand in their own security app. Test it: restart the tablet and " +
                    "check that the next bell still rings.")
            }
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text("Keep it plugged in") },
            supportingContent = {
                Text("A flat tablet rings nothing. Use a permanent charger, ideally on " +
                    "the same UPS as the amplifier.")
            }
        )

        // ---- defaults -------------------------------------------------------
        SectionTitle("Defaults")
        OutlinedTextField(
            value = cfg.defaultDurationSec.toString(),
            onValueChange = {
                val n = it.toIntOrNull()?.coerceIn(1, 300) ?: return@OutlinedTextField
                update(cfg.copy(defaultDurationSec = n))
            },
            label = { Text("Default ring length (seconds)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Hint("Used for new bells and the manual Ring button. Each bell can override it.")

        // ---- PIN -------------------------------------------------------------
        SectionTitle("PIN lock")
        Hint("Stops students changing the timetable. Leave it off if the tablet is locked away.")
        ListItem(
            headlineContent = { Text(if (cfg.pin == null) "No PIN set" else "PIN is set") },
            trailingContent = {
                Row {
                    TextButton(onClick = { settingPin = true }) {
                        Text(if (cfg.pin == null) "Set PIN" else "Change")
                    }
                    if (cfg.pin != null) {
                        TextButton(onClick = { update(cfg.copy(pin = null)) }) { Text("Remove") }
                    }
                }
            }
        )

        // ---- backup -----------------------------------------------------------
        SectionTitle("Backup")
        Hint("Everything — timetables, calendar, settings — is one file. Save a copy to " +
            "Google Drive or a pen drive after you make changes.")
        Row {
            Button(onClick = { exporter.launch("college-bell-backup.json") }) {
                Text("Save backup")
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/*")) }) {
                Text("Restore backup")
            }
        }

        Spacer(Modifier.height(32.dp))
        Text("College Bell v1.0", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(32.dp))
    }

    if (settingPin) {
        TextPrompt("Set a 4-digit PIN", cfg.pin ?: "", { settingPin = false }) { entered ->
            val digits = entered.filter(Char::isDigit).take(4)
            if (digits.length == 4) update(cfg.copy(pin = digits))
            else message = "A PIN must be exactly 4 digits. Nothing was changed."
        }
    }

    message?.let {
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } }
        )
    }
}
