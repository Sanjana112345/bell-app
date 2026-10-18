package com.college.bell

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Everything the app knows lives in these classes, and the whole lot is saved as
 * ONE readable JSON file (config.json in the app's private folder).
 *
 * Why a plain file and not a database: there is no schema to migrate, you can open
 * it in Notepad to see what went wrong, and "backup" is just copying it somewhere.
 */

/** One bell: a time of day, a sound, and how long to ring it. */
@Serializable
data class BellEvent(
    val id: String = newId(),
    val label: String = "Bell",
    /** 24-hour "HH:mm". Stored 24-hour, always SHOWN to teachers as 12-hour. */
    val time: String = "09:00",
    /** A content:// URI the teacher picked, or null to use the built-in bell sound. */
    val soundUri: String? = null,
    val durationSec: Int = 10,
    val enabled: Boolean = true,
)

/** A named set of bells, e.g. "Regular Weekday", "Saturday", "Exam Day". */
@Serializable
data class Timetable(
    val id: String = newId(),
    val name: String = "New Timetable",
    val events: List<BellEvent> = emptyList(),
)

/** One line in the ring log. */
@Serializable
data class RingLog(
    /** ISO "2026-10-18T09:00" */
    val at: String,
    val label: String,
    val manual: Boolean = false,
    val failed: String? = null,
)

@Serializable
data class Config(
    val timetables: List<Timetable> = emptyList(),

    /** "MONDAY".."SUNDAY" -> timetable id. A missing day means no bells that day. */
    val weekdayMap: Map<String, String> = emptyMap(),

    /** "2026-10-18" -> timetable id. Beats the weekday map for that one date. */
    val dateOverrides: Map<String, String> = emptyMap(),

    /** "2026-10-18" dates with no bells at all. Beats everything. */
    val holidays: List<String> = emptyList(),

    val defaultDurationSec: Int = 10,

    /** 4-digit PIN for the edit screens, or null for no lock. */
    val pin: String? = null,

    val history: List<RingLog> = emptyList(),

    /** Set to true once someone has been through the Setup screen. */
    val setupDone: Boolean = false,
) {
    fun timetable(id: String?): Timetable? = timetables.firstOrNull { it.id == id }
}

fun newId(): String = java.util.UUID.randomUUID().toString().take(8)

/**
 * Loads and saves the single config file. Held in memory while the app runs so the
 * UI and the alarm receiver always read the same thing.
 */
object Store {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true   // lets an older APK read a newer file without crashing
        encodeDefaults = true
    }

    private var cached: Config? = null

    fun file(context: Context) = File(context.filesDir, "config.json")

    fun load(context: Context): Config {
        cached?.let { return it }
        val f = file(context)
        val loaded = if (f.exists()) {
            runCatching { json.decodeFromString<Config>(f.readText()) }
                .getOrElse { seed() }   // corrupt file: start fresh rather than crash on boot
        } else {
            seed()
        }
        cached = loaded
        return loaded
    }

    /** Saves, then re-arms the next alarm. Every change goes through here. */
    fun save(context: Context, config: Config) {
        cached = config
        file(context).writeText(json.encodeToString(config))
        Scheduler.armNext(context)
    }

    fun export(config: Config): String = json.encodeToString(config)

    /** Returns null if the text is not a valid config, so the caller can show an error. */
    fun import(text: String): Config? =
        runCatching { json.decodeFromString<Config>(text) }.getOrNull()

    /** A first-run timetable so the app is not an empty screen on day one. */
    private fun seed(): Config {
        val regular = Timetable(
            name = "Regular Weekday",
            events = listOf(
                BellEvent(label = "First Bell", time = "09:00"),
                BellEvent(label = "Period 2", time = "09:50"),
                BellEvent(label = "Short Break", time = "10:40"),
                BellEvent(label = "Period 3", time = "10:55"),
                BellEvent(label = "Period 4", time = "11:45"),
                BellEvent(label = "Lunch", time = "12:35"),
                BellEvent(label = "Period 5", time = "13:30"),
                BellEvent(label = "Period 6", time = "14:20"),
                BellEvent(label = "Final Bell", time = "15:10"),
            )
        )
        return Config(
            timetables = listOf(regular),
            weekdayMap = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY")
                .associateWith { regular.id },
        )
    }
}
