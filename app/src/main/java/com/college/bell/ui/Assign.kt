package com.college.bell.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.college.bell.Config
import com.college.bell.prettyTime
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Decides which timetable runs on which day. Three lists, matching the three
 * precedence rules, shown in the same order the app applies them.
 */
@Composable
fun AssignScreen(cfg: Config, update: (Config) -> Unit) {
    val context = LocalContext.current
    var picking by remember { mutableStateOf<String?>(null) }   // weekday name or ISO date

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Calendar", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        Hint("Holidays win over everything. A specific date wins over the weekly pattern.")

        // ---- 1. weekly pattern ---------------------------------------------
        SectionTitle("Weekly pattern")
        DayOfWeek.entries.forEach { day ->
            val assigned = cfg.timetable(cfg.weekdayMap[day.name])
            ListItem(
                headlineContent = {
                    Text(day.name.lowercase().replaceFirstChar(Char::uppercase))
                },
                supportingContent = {
                    Text(assigned?.name ?: "No bells",
                        color = if (assigned == null) MaterialTheme.colorScheme.outline
                        else MaterialTheme.colorScheme.primary)
                },
                trailingContent = {
                    TextButton(onClick = { picking = day.name }) { Text("Change") }
                }
            )
            HorizontalDivider()
        }

        // ---- 2. specific dates ----------------------------------------------
        SectionTitle("Specific dates")
        Hint("Exam days, functions, rescheduled days — these override the weekly pattern.")
        cfg.dateOverrides.toSortedMap().forEach { (date, ttId) ->
            ListItem(
                headlineContent = { Text(prettyDate(date)) },
                supportingContent = { Text(cfg.timetable(ttId)?.name ?: "(deleted timetable)") },
                trailingContent = {
                    IconButton(onClick = {
                        update(cfg.copy(dateOverrides = cfg.dateOverrides - date))
                    }) { Icon(Icons.Filled.Close, "Remove") }
                }
            )
            HorizontalDivider()
        }
        OutlinedButton(
            onClick = { pickDate(context) { date -> picking = date } },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("Add a specific date") }

        // ---- 3. holidays -----------------------------------------------------
        SectionTitle("Holidays")
        Hint("No bells at all on these dates. Your weekly pattern is left untouched.")
        cfg.holidays.sorted().forEach { date ->
            ListItem(
                headlineContent = { Text(prettyDate(date)) },
                trailingContent = {
                    IconButton(onClick = {
                        update(cfg.copy(holidays = cfg.holidays - date))
                    }) { Icon(Icons.Filled.Close, "Remove") }
                }
            )
            HorizontalDivider()
        }
        OutlinedButton(
            onClick = {
                pickDate(context) { date ->
                    if (date !in cfg.holidays) {
                        update(cfg.copy(holidays = cfg.holidays + date))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("Add a holiday") }

        Spacer(Modifier.height(32.dp))
    }

    // One chooser, reused for both weekdays and dates.
    picking?.let { target ->
        val isWeekday = '-' !in target   // ISO dates have dashes, weekday names do not
        AlertDialog(
            onDismissRequest = { picking = null },
            title = {
                Text(if (isWeekday)
                    target.lowercase().replaceFirstChar(Char::uppercase)
                else prettyDate(target))
            },
            text = {
                Column {
                    cfg.timetables.forEach { tt ->
                        ListItem(
                            headlineContent = { Text(tt.name) },
                            supportingContent = {
                                val first = tt.events.firstOrNull { it.enabled }
                                Text(if (first == null) "no bells"
                                else "${tt.events.count { it.enabled }} bells, first at ${prettyTime(first.time)}")
                            },
                            modifier = Modifier.clickable {
                                update(
                                    if (isWeekday) cfg.copy(weekdayMap = cfg.weekdayMap + (target to tt.id))
                                    else cfg.copy(dateOverrides = cfg.dateOverrides + (target to tt.id))
                                )
                                picking = null
                            }
                        )
                        HorizontalDivider()
                    }
                    TextButton(onClick = {
                        update(
                            if (isWeekday) cfg.copy(weekdayMap = cfg.weekdayMap - target)
                            else cfg.copy(dateOverrides = cfg.dateOverrides - target)
                        )
                        picking = null
                    }) { Text("No bells on this day") }
                }
            },
            confirmButton = { TextButton(onClick = { picking = null }) { Text("Cancel") } }
        )
    }
}

private fun prettyDate(iso: String): String =
    runCatching {
        LocalDate.parse(iso)
            .format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM yyyy", java.util.Locale.ENGLISH))
    }.getOrDefault(iso)
