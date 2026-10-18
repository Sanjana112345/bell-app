package com.college.bell

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Works out WHICH bells ring and WHEN. Deliberately plain Kotlin with no Android
 * imports, so it can be tested on a laptop without a phone or emulator.
 * See ScheduleTest.kt.
 */

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

// Locale.ENGLISH on purpose. Left to the device locale, an en-IN or en-GB tablet
// renders "9:00 am" and some newer Android versions put a narrow no-break space
// before the marker. The bell board should read the same on every device.
val DISPLAY_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

/** Forces "9:00 AM" however the device's locale data feels about it. */
fun normaliseAmPm(s: String): String =
    s.replace('\u202F', ' ')
        .replace('\u00A0', ' ')
        .replace(Regex("\\b([ap])m\\b")) { it.groupValues[1].uppercase() + "M" }

fun parseTime(hhmm: String): LocalTime? = runCatching { LocalTime.parse(hhmm, HHMM) }.getOrNull()

/** "09:05" -> "9:05 AM". Teachers only ever see this form. */
fun prettyTime(hhmm: String): String =
    parseTime(hhmm)?.format(DISPLAY_TIME)?.let(::normaliseAmPm) ?: hhmm

/** Why a given date has no bells — shown on the dashboard so it is never a mystery. */
enum class NoBellReason { HOLIDAY, NOTHING_ASSIGNED, EMPTY_TIMETABLE }

data class DayPlan(
    val timetable: Timetable?,
    val events: List<BellEvent>,
    val reason: NoBellReason?,
)

/**
 * The precedence rule, top to bottom:
 *   1. holiday          -> no bells at all
 *   2. date override    -> that exact date uses this timetable
 *   3. weekday map      -> the normal case
 *   4. nothing assigned -> no bells, and the dashboard says so
 */
fun planFor(cfg: Config, date: LocalDate): DayPlan {
    val iso = date.toString()

    if (iso in cfg.holidays) return DayPlan(null, emptyList(), NoBellReason.HOLIDAY)

    val id = cfg.dateOverrides[iso] ?: cfg.weekdayMap[date.dayOfWeek.name]
    val tt = cfg.timetable(id)
        ?: return DayPlan(null, emptyList(), NoBellReason.NOTHING_ASSIGNED)

    val events = tt.events
        .filter { it.enabled && parseTime(it.time) != null }
        .sortedBy { it.time }

    return DayPlan(tt, events, if (events.isEmpty()) NoBellReason.EMPTY_TIMETABLE else null)
}

data class ScheduledBell(val at: LocalDateTime, val event: BellEvent)

/**
 * The next bell strictly AFTER [now], looking up to [searchDays] ahead so that
 * Friday evening correctly finds Monday morning. Null means nothing is scheduled.
 *
 * Strictly-after matters: when a bell fires we immediately ask for the next one,
 * and "after" is what stops it finding the bell that just rang and looping forever.
 */
fun nextBell(cfg: Config, now: LocalDateTime, searchDays: Int = 14): ScheduledBell? {
    for (offset in 0..searchDays) {
        val date = now.toLocalDate().plusDays(offset.toLong())
        for (event in planFor(cfg, date).events) {
            val at = LocalDateTime.of(date, parseTime(event.time) ?: continue)
            if (at.isAfter(now)) return ScheduledBell(at, event)
        }
    }
    return null
}
