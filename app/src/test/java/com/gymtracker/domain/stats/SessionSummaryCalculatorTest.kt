package com.gymtracker.domain.stats

import com.gymtracker.domain.model.RestEndReason
import com.gymtracker.domain.model.RestInterval
import com.gymtracker.domain.model.SessionStatus
import com.gymtracker.domain.model.WorkoutSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lista historii: co w ogóle jest treningiem, w jakiej kolejności i co pokazuje
 * w wierszu. Czysta funkcja, więc bez bazy i bez zegara.
 */
class SessionSummaryCalculatorTest {

    private val zone = ZoneId.of("UTC")
    private val day = LocalDate.of(2026, 10, 10)

    private fun at(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun session(
        id: Long,
        date: LocalDate,
        name: String = "Dzień A",
        status: SessionStatus = SessionStatus.FINISHED,
        durationMs: Long = 3_600_000L,
        sessionDate: LocalDate? = null,
    ): WorkoutSession = WorkoutSession(
        id = id,
        planId = null,
        planDayId = null,
        name = name,
        startedAtEpochMs = at(date),
        finishedAtEpochMs = at(date) + durationMs,
        status = status,
        note = null,
        sessionDate = sessionDate,
    )

    private fun aggregate(sessionId: Long, sets: Int = 3, volumeKg: Double = 1_000.0, best: Double? = 100.0) =
        SessionAggregate(
            sessionId = sessionId,
            workingSets = sets,
            volumeKg = volumeKg,
            totalReps = sets * 5,
            bestWeightKg = best,
        )

    private fun rest(sessionId: Long?, seconds: Int, reason: RestEndReason = RestEndReason.COMPLETED) =
        RestInterval(
            id = 0,
            exerciseId = 1,
            sessionId = sessionId,
            setLogId = null,
            startedAtEpochMs = 0,
            endedAtEpochMs = seconds * 1_000L,
            plannedSeconds = 90,
            actualSeconds = seconds,
            reason = reason,
            isAuto = true,
        )

    private fun calculate(
        sessions: List<WorkoutSession>,
        aggregates: List<SessionAggregate> = emptyList(),
        rests: List<RestInterval> = emptyList(),
        exercises: Map<Long, Set<Long>> = emptyMap(),
    ): List<SessionSummary> =
        SessionSummaryCalculator.calculate(sessions, aggregates, rests, exercises, zone)

    @Test
    fun `trening w toku i przerwany nie sa historia`() {
        val summaries = calculate(
            listOf(
                session(1, day, status = SessionStatus.ACTIVE),
                session(2, day, status = SessionStatus.CANCELLED),
                session(3, day.plusDays(1)),
            ),
        )

        assertEquals(listOf(3L), summaries.map { summary -> summary.sessionId })
    }

    @Test
    fun `najnowsze treningi sa pierwsze`() {
        val summaries = calculate(
            listOf(
                session(1, day),
                session(2, day.plusDays(2)),
                session(3, day.plusDays(1)),
            ),
        )

        assertEquals(listOf(2L, 3L, 1L), summaries.map { summary -> summary.sessionId })
    }

    @Test
    fun `dzien treningu ma pierwszenstwo nad dniem startu`() {
        val summaries = calculate(
            listOf(
                session(1, day, sessionDate = day.plusDays(9)),
            ),
        )

        assertEquals(day.plusDays(9), summaries.single().date)
    }

    @Test
    fun `wiersz bierze liczby z agregatu, a braki zastepuje zerami`() {
        val summaries = calculate(
            sessions = listOf(session(1, day), session(2, day)),
            aggregates = listOf(aggregate(1, sets = 4, volumeKg = 2_500.0, best = 120.0)),
        )

        val withData = summaries.first { summary -> summary.sessionId == 1L }
        assertEquals(4, withData.workingSets)
        assertEquals(2_500.0, withData.volumeKg, 0.001)
        assertEquals(120.0, withData.bestWeightKg!!, 0.001)
        assertEquals(3_600_000L, withData.durationMs)
        assertTrue(withData.hasSets)

        val empty = summaries.first { summary -> summary.sessionId == 2L }
        assertEquals(0, empty.workingSets)
        assertEquals(0.0, empty.volumeKg, 0.001)
        assertNull(empty.bestWeightKg)
        assertNull("trening bez przerw nie ma średniej", empty.restAverageSeconds)
        assertTrue(!empty.hasSets)
    }

    @Test
    fun `srednia przerwa w wierszu liczy tylko odbyte przerwy tego treningu`() {
        val summaries = calculate(
            sessions = listOf(session(1, day), session(2, day)),
            rests = listOf(
                rest(1, 60),
                rest(1, 120, RestEndReason.EXTENDED),
                rest(1, 30, RestEndReason.SKIPPED),
                rest(1, 5),
                rest(2, 300),
            ),
        )

        assertEquals(90, summaries.first { summary -> summary.sessionId == 1L }.restAverageSeconds)
        assertEquals(300, summaries.first { summary -> summary.sessionId == 2L }.restAverageSeconds)
    }

    @Test
    fun `filtr zawęża po zakresie, cwiczeniu i nazwie`() {
        val summaries = calculate(
            sessions = listOf(
                session(1, day, name = "Dzień A"),
                session(2, day.minusDays(10), name = "Dzień B"),
                session(3, day.minusDays(200), name = "Dzień B"),
            ),
            exercises = mapOf(1L to setOf(10L, 20L), 2L to setOf(20L), 3L to setOf(30L)),
        )

        assertEquals(
            listOf(1L, 2L),
            SessionSummaryCalculator.filter(summaries, StatsRange.MONTH_1, day).map { it.sessionId },
        )
        assertEquals(
            listOf(1L),
            SessionSummaryCalculator.filter(summaries, StatsRange.ALL, day, exerciseId = 10L).map { it.sessionId },
        )
        assertEquals(
            listOf(2L, 3L),
            SessionSummaryCalculator.filter(summaries, StatsRange.ALL, day, name = "Dzień B").map { it.sessionId },
        )
        assertEquals(
            listOf(3L),
            SessionSummaryCalculator.filter(summaries, StatsRange.ALL, day, exerciseId = 30L, name = "Dzień B")
                .map { it.sessionId },
        )
    }

    @Test
    fun `data treningu to dzien lokalny, a czas bez pauz`() {
        val summary = calculate(
            listOf(
                WorkoutSession(
                    id = 1,
                    planId = null,
                    planDayId = null,
                    name = "Dzień A",
                    startedAtEpochMs = Instant.parse("2026-10-10T22:00:00Z").toEpochMilli(),
                    finishedAtEpochMs = Instant.parse("2026-10-10T23:30:00Z").toEpochMilli(),
                    status = SessionStatus.FINISHED,
                    note = null,
                    pausedTotalMs = 10 * 60_000L,
                ),
            ),
        ).single()

        assertEquals(LocalDate.of(2026, 10, 10), summary.date)
        assertEquals(80 * 60_000L, summary.durationMs)
    }
}
