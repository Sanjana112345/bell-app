package com.college.bell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.college.bell.*
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val DATE_FMT = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", java.util.Locale.ENGLISH)
private val CLOCK_FMT = DateTimeFormatter.ofPattern("h:mm:ss a", java.util.Locale.ENGLISH)

/**
 * What the dedicated tablet shows all day. Everything a passer-by needs to know
 * at a glance: is it working, what is next, what happens for the rest of today.
 */
@Composable
fun DashboardScreen(cfg: Config, update: (Config) -> Unit) {
    val context = LocalContext.current

    // Re-reads the clock every second. Also re-evaluates the next bell, so the
    // countdown and the "ringing now" state stay honest without any extra plumbing.
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000)
        }
    }

    val plan = planFor(cfg, now.toLocalDate())
    val next = nextBell(cfg, now)
    var confirmManual by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text(now.format(DATE_FMT), fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(normaliseAmPm(now.format(CLOCK_FMT)), fontSize = 52.sp, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(16.dp))

        // ---- status -------------------------------------------------------
        when (plan.reason) {
            NoBellReason.HOLIDAY -> StatusBanner(
                "Holiday — no bells today", "Scheduled bells are suppressed for this date.",
                Color(0xFFFFE0B2))
            NoBellReason.NOTHING_ASSIGNED -> StatusBanner(
                "No timetable assigned to today", "Assign one on the Calendar tab.",
                Color(0xFFFFCDD2))
            NoBellReason.EMPTY_TIMETABLE -> StatusBanner(
                "Timetable '${plan.timetable?.name}' has no bells",
                "Add bells on the Timetables tab.", Color(0xFFFFCDD2))
            null -> StatusBanner(
                "Running — ${plan.timetable?.name}",
                "${plan.events.size} bells scheduled today.", Color(0xFFC8E6C9))
        }

        Spacer(Modifier.height(16.dp))

        // ---- next bell ----------------------------------------------------
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("NEXT BELL", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (next == null) {
                    Text("Nothing scheduled", fontSize = 22.sp,
                        modifier = Modifier.padding(top = 4.dp))
                } else {
                    Text(next.event.label, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp))
                    val sameDay = next.at.toLocalDate() == now.toLocalDate()
                    Text(
                        prettyTime(next.event.time) +
                            if (sameDay) "" else "  ·  ${next.at.toLocalDate().dayOfWeek.name.lowercase()
                                .replaceFirstChar { it.uppercase() }}",
                        fontSize = 18.sp
                    )
                    Text("in ${countdown(Duration.between(now, next.at))}",
                        fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- manual ring ---------------------------------------------------
        // isRinging is a plain flag, not observable state — but this whole composable
        // recomposes every second from the clock above, so the button flips within 1s.
        if (BellService.isRinging) {
            Button(
                onClick = { BellService.stop(context) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Filled.Stop, null); Spacer(Modifier.width(8.dp))
                Text("STOP BELL", fontSize = 16.sp)
            }
            Hint("Stopping the current bell does not cancel any future scheduled bell.")
        } else {
            OutlinedButton(
                onClick = { confirmManual = true },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Filled.NotificationsActive, null); Spacer(Modifier.width(8.dp))
                Text("RING BELL NOW", fontSize = 16.sp)
            }
        }

        if (confirmManual) {
            ConfirmDialog(
                title = "Ring the bell now?",
                message = "This rings immediately through the speakers. Scheduled bells are unaffected.",
                confirmLabel = "Ring",
                onConfirm = {
                    BellService.ring(context, "Manual Bell", null, cfg.defaultDurationSec, manual = true)
                },
                onDismiss = { confirmManual = false }
            )
        }

        // ---- today's schedule ----------------------------------------------
        if (plan.events.isNotEmpty()) {
            SectionTitle("Today's schedule")
            plan.events.forEach { event ->
                // safe: planFor() has already dropped any event with an unparseable time
                val at = LocalDateTime.of(now.toLocalDate(), parseTime(event.time)!!)
                val past = at.isBefore(now)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(10.dp).background(
                            if (past) MaterialTheme.colorScheme.outlineVariant
                            else MaterialTheme.colorScheme.primary,
                            CircleShape
                        )
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        prettyTime(event.time),
                        fontWeight = FontWeight.Bold,
                        color = if (past) MaterialTheme.colorScheme.outline
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(92.dp)
                    )
                    Text(
                        event.label,
                        color = if (past) MaterialTheme.colorScheme.outline
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
                HorizontalDivider()
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun countdown(d: Duration): String {
    if (d.isNegative) return "now"
    val days = d.toDays()
    val h = d.toHours() % 24
    val m = d.toMinutes() % 60
    val s = d.seconds % 60
    return when {
        days > 0 -> "$days day${if (days > 1) "s" else ""} $h hr"
        h > 0 -> "$h hr $m min"
        m > 0 -> "$m min $s sec"
        else -> "$s sec"
    }
}
