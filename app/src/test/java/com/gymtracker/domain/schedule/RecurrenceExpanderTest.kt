package com.gymtracker.domain.schedule

import com.gymtracker.domain.model.RecurrenceDraft
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rozwijanie reguły w daty - czysta logika, więc testujemy bez bazy i zegara.
 *
 * Najważniejszy przypadek to kryterium M3: „co 3 dni, od 1.10 do 31.12,
 * rotacja A/B/C”. Do tego horyzont: 20 tygodni w przód i ani dnia dalej.
 */
class RecurrenceExpanderTest {

    private val today = LocalDate.of(2026, 10, 1)

    private val days = listOf(
        PlannedDayName(id = 11, name = "Workout A"),
        PlannedDayName(id = 12, name = "Workout B"),
        PlannedDayName(id = 13, name = "Workout C"),
    )

    @Test
    fun `co 3 dni od 1 pazdziernika do 31 grudnia z rotacja A B C`() {
        val draft = draft(start = today, end = LocalDate.of(2026, 12, 31), interval = 3)

        val dates = RecurrenceExpander.dates(draft, today)
        val workouts = RecurrenceExpander.assignDays(dates, days)

        // 1.10, 4.10, 7.10, ... - 31 dat (91 dni / 3 + 1), ostatnia 30.12.
        assertEquals(31, dates.size)
        assertEquals(LocalDate.of(2026, 10, 1), dates.first())
        assertEquals(LocalDate.of(2026, 10, 4), dates[1])
        assertEquals(LocalDate.of(2026, 10, 7), dates[2])
        assertEquals(LocalDate.of(2026, 12, 30), dates.last())

        // Rotacja: A, B, C, A, B, C...
        assertEquals(listOf("Workout A", "Workout B", "Workout C"), workouts.take(3).map { it.title })
        assertEquals("Workout A", workouts[3].title)
        assertEquals("Workout A", workouts[30].title)

        // Każde odstępy równe 3 dni - ani dnia przerwy więcej.
        dates.zipWithNext().forEach { (a, b) ->
            assertEquals(3L, java.time.temporal.ChronoUnit.DAYS.between(a, b))
        }
    }

    @Test
    fun `bez daty do horyzont to 20 tygodni od dzis`() {
        val draft = draft(start = today, end = null, interval = 7)

        val dates = RecurrenceExpander.dates(draft, today)

        assertEquals(today, dates.first())
        assertEquals(today.plusWeeks(20), dates.last())
        assertEquals(ScheduleLimits.horizonEnd(today), RecurrenceExpander.horizon(today, null))
    }

    @Test
    fun `data do dalej niz horyzont jest przycinana`() {
        val beyond = today.plusWeeks(30)
        val draft = draft(start = today, end = beyond, interval = 1)

        val dates = RecurrenceExpander.dates(draft, today)

        assertEquals(today.plusWeeks(20), dates.last())
        assertTrue("żadna data nie wychodzi poza horyzont", dates.none { it.isAfter(today.plusWeeks(20)) })
    }

    @Test
    fun `dni tygodnia zawężają regułę`() {
        // Od poniedziałku, co dzień, tylko pon/śr/pt.
        val monday = LocalDate.of(2026, 10, 5)
        val draft = RecurrenceDraft(
            planId = 1,
            planDayId = null,
            startDate = monday,
            endDate = monday.plusDays(6),
            intervalDays = 1,
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        )

        val dates = RecurrenceExpander.dates(draft, monday)

        assertEquals(
            listOf(
                LocalDate.of(2026, 10, 5),
                LocalDate.of(2026, 10, 7),
                LocalDate.of(2026, 10, 9),
            ),
            dates,
        )
    }

    @Test
    fun `odstep i dni tygodnia dzialaja razem`() {
        // Co 2 dni, tylko dni robocze: 5.10 (pon), 7.10 (śr), 9.10 (pt), 13.10 (wt)...
        val monday = LocalDate.of(2026, 10, 5)
        val draft = RecurrenceDraft(
            planId = 1,
            planDayId = null,
            startDate = monday,
            endDate = monday.plusDays(9),
            intervalDays = 2,
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
        )

        val dates = RecurrenceExpander.dates(draft, monday)

        assertEquals(
            listOf(
                LocalDate.of(2026, 10, 5),
                LocalDate.of(2026, 10, 7),
                LocalDate.of(2026, 10, 9),
                LocalDate.of(2026, 10, 13),
            ),
            dates,
        )
    }

    @Test
    fun `rotacja kontynuuje sie po dosypaniu horyzontu`() {
        val dates = (1..4).map { today.plusDays(it.toLong()) }

        val workouts = RecurrenceExpander.assignDays(dates, days, startIndex = 2)

        // Skoro ostatni wpis był C (indeks 2), kolejny nowy to znów A.
        assertEquals(listOf("Workout C", "Workout A", "Workout B", "Workout C"), workouts.map { it.title })
    }

    @Test
    fun `jeden dzien planu nie rotuje`() {
        val single = listOf(PlannedDayName(id = 7, name = "Dzień A"))
        val dates = listOf(today, today.plusDays(3), today.plusDays(6))

        val workouts = RecurrenceExpander.assignDays(dates, single)

        assertEquals(listOf(7L, 7L, 7L), workouts.map { it.planDayId })
    }

    @Test
    fun `zmiana czasu nie gubi ani nie dubluje dnia`() {
        // W Polsce czas letni zaczyna się 29.03.2026 - dzień ma 23 godziny.
        val springForward = LocalDate.of(2026, 3, 28)
        val rule = RecurrenceDraft(
            planId = 1,
            startDate = springForward,
            endDate = springForward.plusDays(3),
            intervalDays = 1,
        )

        val dates = RecurrenceExpander.dates(rule, springForward)

        assertEquals(
            listOf(
                LocalDate.of(2026, 3, 28),
                LocalDate.of(2026, 3, 29),
                LocalDate.of(2026, 3, 30),
                LocalDate.of(2026, 3, 31),
            ),
            dates,
        )
    }

    @Test
    fun `reguła zaczęta w przeszłości planuje tylko od dziś`() {
        // Reguła „co 3 dni” od 25.09, dziś 1.10 - rytm liczy się od 25.09,
        // ale wpisy powstają dopiero od dziś: 28.09 pomijamy, 1.10 wchodzi.
        val monthAgo = today.minusDays(6)
        val draft = draft(start = monthAgo, end = today.plusDays(6), interval = 3)

        val dates = RecurrenceExpander.dates(draft, today)

        assertEquals(listOf(today, today.plusDays(3), today.plusDays(6)), dates)
        assertTrue("nie ma dat z przeszłości", dates.none { it.isBefore(today) })
        assertTrue("przeszły start nie jest błędem", RecurrenceExpander.validate(draft, today, days).isEmpty())
    }

    @Test
    fun `walidacja - start i koniec poza horyzontem`() {
        val far = today.plusWeeks(21)

        assertEquals(
            listOf(ScheduleError.START_BEYOND_HORIZON),
            RecurrenceExpander.validate(draft(start = far, end = null, interval = 1), today, days),
        )
        assertEquals(
            listOf(ScheduleError.END_BEYOND_HORIZON),
            RecurrenceExpander.validate(draft(start = today, end = far, interval = 1), today, days),
        )
    }

    @Test
    fun `walidacja - koniec przed startem i zly odstep`() {
        val draft = draft(start = today.plusDays(10), end = today.plusDays(5), interval = 0)

        val errors = RecurrenceExpander.validate(draft, today, days)

        assertEquals(
            listOf(ScheduleError.END_BEFORE_START, ScheduleError.INTERVAL_TOO_SMALL),
            errors,
        )
    }

    @Test
    fun `walidacja - plan bez dni`() {
        val errors = RecurrenceExpander.validate(draft(today, null, 1), today, emptyList())

        assertEquals(listOf(ScheduleError.NO_PLAN_DAY), errors)
    }

    @Test
    fun `plan bez dni nie produkuje wpisow`() {
        val dates = RecurrenceExpander.dates(draft(today, null, 1), today)

        assertTrue(RecurrenceExpander.assignDays(dates, emptyList()).isEmpty())
    }

    @Test
    fun `start dzis jest poprawny`() {
        val errors = RecurrenceExpander.validate(draft(today, today.plusWeeks(20), 1), today, days)

        assertTrue(errors.isEmpty())
    }

    private fun draft(start: LocalDate, end: LocalDate?, interval: Int) = RecurrenceDraft(
        planId = 1,
        planDayId = null,
        startDate = start,
        endDate = end,
        intervalDays = interval,
    )
}
