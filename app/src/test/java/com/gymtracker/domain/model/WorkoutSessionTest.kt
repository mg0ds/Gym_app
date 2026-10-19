package com.gymtracker.domain.model

import com.gymtracker.domain.schedule.ScheduleLimits
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dzień treningu i okno jego poprawki.
 *
 * Dzień jest tym, co trafia do historii i statystyk, więc musi być policzalny
 * bez bazy: albo z przypisanej daty, albo ze startu w strefie urządzenia.
 */
class WorkoutSessionTest {

    private val zone = ZoneId.of("Europe/Warsaw")
    private val today = LocalDate.of(2026, 10, 18)

    @Test
    fun `dzien treningu to data przypisana, a bez niej dzien startu`() {
        val started = LocalDate.of(2026, 10, 18)

        assertEquals(
            "bez daty zostaje dzień startu",
            started,
            session(startedAt = started).dateIn(zone),
        )
        assertEquals(
            "data z kalendarza ma pierwszeństwo",
            LocalDate.of(2026, 10, 27),
            session(startedAt = started, date = LocalDate.of(2026, 10, 27)).dateIn(zone),
        )
    }

    @Test
    fun `okno poprawki dnia cofa sie o dobe i siega konca horyzontu`() {
        val range = session(startedAt = LocalDate.of(2026, 10, 18), startedHour = 0)
            .dayEditRange(zone, today)

        assertEquals("start po północy bywa dniem wcześniej", LocalDate.of(2026, 10, 17), range.start)
        assertEquals(ScheduleLimits.horizonEnd(today), range.endInclusive)
    }

    @Test
    fun `okno poprawki obejmuje dzien przypisany na przyszlosc`() {
        val future = today.plusDays(9)
        val range = session(startedAt = today, date = future).dayEditRange(zone, today)

        assertEquals(today.minusDays(1), range.start)
        assertTrue("dzień treningu zostaje w oknie", future in range)
    }

    private fun session(
        startedAt: LocalDate,
        date: LocalDate? = null,
        startedHour: Int = 20,
    ) = WorkoutSession(
        id = 1L,
        planId = null,
        planDayId = null,
        name = "Trening",
        startedAtEpochMs = startedAt.atTime(startedHour, 0).atZone(zone).toInstant().toEpochMilli(),
        finishedAtEpochMs = null,
        status = SessionStatus.FINISHED,
        note = null,
        sessionDate = date,
    )
}
