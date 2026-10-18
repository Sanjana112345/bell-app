package com.college.bell.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.college.bell.Config
import com.college.bell.RingLog
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val FMT = DateTimeFormatter.ofPattern("EEE d MMM  ·  h:mm a", java.util.Locale.ENGLISH)

/** What actually rang. Kept for 30 days, trimmed automatically after each ring. */
@Composable
fun HistoryScreen(cfg: Config, update: (Config) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("History", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        Hint("The last 30 days of bells. Older entries are deleted automatically.")

        val failures = cfg.history.count { it.failed != null }
        if (failures > 0) {
            StatusBanner("$failures bell(s) failed to play",
                "Tap a red entry below for the reason.",
                androidx.compose.ui.graphics.Color(0xFFFFCDD2))
            Spacer(Modifier.height(12.dp))
        }

        if (cfg.history.isEmpty()) {
            Hint("Nothing has rung yet.")
        }

        LazyColumn {
            items(cfg.history.sortedByDescending(RingLog::at)) { entry ->
                ListItem(
                    headlineContent = {
                        Text(entry.label, fontWeight = FontWeight.Bold,
                            color = if (entry.failed != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface)
                    },
                    supportingContent = {
                        val when_ = runCatching {
                            com.college.bell.normaliseAmPm(LocalDateTime.parse(entry.at).format(FMT))
                        }.getOrDefault(entry.at)
                        Text(when_ +
                            (if (entry.manual) "  ·  manual" else "") +
                            (entry.failed?.let { "  ·  FAILED: $it" } ?: ""))
                    }
                )
                HorizontalDivider()
            }
        }
    }
}
