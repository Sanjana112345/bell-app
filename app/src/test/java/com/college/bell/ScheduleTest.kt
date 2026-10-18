package com.college.bell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Covers the only logic in this app that is hard to eyeball: which timetable applies
 * on a given day, and which bell is next. Runs on a laptop, no phone needed.
 *
 * Run from Android Studio: right-click this file -> Run 'ScheduleTest'
 */
class ScheduleTest {

    private val regular = Timetable(
        id = "reg", name = "Regular",
        events = listOf(
            BellEvent(id = "a", label = "First", time = "09:00"),
            BellEvent(id = "b", label = "Lunch", time = "12:30"),
            BellEvent(id = "c", label = "Final", time = "15:10"),
        )
    )
    private val exam = Timetable(
        id = "exam", name = "Exam",
        events = listOf(BellEvent(id = "x", label = "Exam Start", time = "10:00"))
    )

    // Monday 19 Oct 2026, Saturday 24 Oct 2026
    private val monday = LocalDate.of(2026, 10, 19)
    private val saturday = LocalDate.of(2026, 10, 24)

    private val cfg = Config(
        timetables = listOf(regular, exam),
        weekdayMap = mapOf(
            "MONDAY" to "reg", "TUESDAY" to "reg", "WEDNESDAY" to "reg",
            "THURSDAY" to "reg", "FRIDAY" to "reg",
        ),
        dateOverrides = mapOf("2026-10-20" to "exam"),
        holidays = listOf("2026-10-21"),
    )

    @Test
    fun `weekday map applies on a normal day`() {
        assertEquals("Regular", planFor(cfg, monday).timetable?.name)
        assertEquals(3, planFor(cfg, monday).events.size)
    }

    @Test
    fun `date override beats the weekday map`() {
        val plan = planFor(cfg, LocalDate.of(2026, 10, 20))   // a Tuesday, mapped to Regular
        assertEquals("Exam", plan.timetable?.name)
    }

    @Test
    fun `holiday suppresses all bells and beats the override`() {
        val plan = planFor(cfg, LocalDate.of(2026, 10, 21))
        assertEquals(0, plan.events.size)
        assertEquals(NoBellReason.HOLIDAY, plan.reason)
    }

    @Test
    fun `unassigned day rings nothing and says why`() {
        val plan = planFor(cfg, saturday)
        assertEquals(0, plan.events.size)
        assertEquals(NoBellReason.NOTHING_ASSIGNED, plan.reason)
    }

    @Test
    fun `disabled events are skipped`() {
        val off = cfg.copy(
            timetables = listOf(regular.copy(
                events = regular.events.map { it.copy(enabled = it.id != "a") }
            ), exam)
        )
        assertEquals(listOf("Lunch", "Final"), planFor(off, monday).events.map { it.label })
    }

    @Test
    fun `next bell skips times already past today`() {
        val next = nextBell(cfg, LocalDateTime.of(monday, java.time.LocalTime.of(10, 0)))
        assertEquals("Lunch", next?.event?.label)
    }

    @Test
    fun `next bell rolls into the following day`() {
        val next = nextBell(cfg, LocalDateTime.of(monday, java.time.LocalTime.of(23, 0)))
        // Tuesday 20 Oct has an exam-day override, so the next bell is the exam's
        // 10:00 start — NOT the regular 09:00 First Bell.
        assertEquals("Exam Start", next?.event?.label)
        assertEquals(LocalDate.of(2026, 10, 20).atTime(10, 0), next?.at)
    }

    @Test
    fun `next bell jumps the weekend`() {
        val friday = LocalDate.of(2026, 10, 23)
        val next = nextBell(cfg, LocalDateTime.of(friday, java.time.LocalTime.of(20, 0)))
        assertEquals(LocalDate.of(2026, 10, 26), next?.at?.toLocalDate())   // the next Monday
    }

    @Test
    fun `a bell exactly now is not returned again - this is what stops a ring loop`() {
        val atNineExactly = LocalDateTime.of(monday, java.time.LocalTime.of(9, 0))
        assertEquals("Lunch", nextBell(cfg, atNineExactly)?.event?.label)
    }

    @Test
    fun `empty config schedules nothing instead of crashing`() {
        assertNull(nextBell(Config(), LocalDateTime.of(monday, java.time.LocalTime.NOON)))
    }

    @Test
    fun `malformed time is ignored rather than crashing the scheduler`() {
        val bad = cfg.copy(timetables = listOf(
            regular.copy(events = regular.events + BellEvent(id = "z", label = "Bad", time = "25:99")),
            exam,
        ))
        assertEquals(3, planFor(bad, monday).events.size)
    }

    @Test
    fun `times are shown to teachers in 12-hour form`() {
        assertEquals("9:00 AM", prettyTime("09:00"))
        assertEquals("12:30 PM", prettyTime("12:30"))
        assertEquals("3:10 PM", prettyTime("15:10"))
    }
}
