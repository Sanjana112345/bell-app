package com.college.bell.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.college.bell.parseTime
import java.time.LocalDate
import java.time.LocalTime

/** Small shared bits of UI. Nothing clever lives here on purpose. */

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}

@Composable
fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

/**
 * Uses Android's own time picker rather than a Compose one: it already shows
 * AM/PM the way the teacher expects, and it is zero code to maintain.
 */
fun pickTime(context: Context, current: String, onPicked: (String) -> Unit) {
    val t = parseTime(current) ?: LocalTime.of(9, 0)
    TimePickerDialog(
        context,
        { _, hour, minute -> onPicked("%02d:%02d".format(hour, minute)) },
        t.hour, t.minute,
        false   // false = show AM/PM, which is what the SRD asks for
    ).show()
}

fun pickDate(context: Context, onPicked: (String) -> Unit) {
    val today = LocalDate.now()
    DatePickerDialog(
        context,
        { _, y, m, d -> onPicked(LocalDate.of(y, m + 1, d).toString()) },
        today.year, today.monthValue - 1, today.dayOfMonth
    ).show()
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Confirm",
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** A big, hard-to-miss status strip for the top of the dashboard. */
@Composable
fun StatusBanner(text: String, detail: String?, colour: androidx.compose.ui.graphics.Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = colour),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (detail != null) Text(detail, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** PIN entry shown in front of the edit screens when a PIN is set. */
@Composable
fun PinGate(expected: String, onUnlocked: () -> Unit) {
    var entry by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Enter PIN", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = entry,
            onValueChange = {
                entry = it.filter(Char::isDigit).take(4)
                wrong = false
                if (entry == expected) onUnlocked()
            },
            isError = wrong,
            label = { Text("4-digit PIN") },
            singleLine = true,
        )
        if (entry.length == 4 && entry != expected) {
            Text("Incorrect PIN", color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp))
        }
    }
}
