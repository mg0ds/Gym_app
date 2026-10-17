package com.gymtracker.domain.stats

import com.gymtracker.domain.model.RestEndReason
import com.gymtracker.domain.model.RestInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Co wchodzi do średniej przerwy i jak liczą się podsumowania.
 *
 * Najważniejsze reguły: pominięte i przerwane przerwy nie mówią nic o formie,
 * a skrajne wartości (3 s albo pół godziny) to raczej pomyłka niż trening.
 */
class RestStatsCalculatorTest {

    @Test
    fun `brak przerw daje pusty wynik`() {
        val stats = RestStatsCalculator.calculate(emptyList())

        assertFalse(stats.hasSamples)
        assertEquals(0, stats.samples)
        assertNull(stats.averageSeconds)
    }

    @Test
    fun `pominiete i przerwane przerwy nie wchodza do sredniej`() {
        val stats = RestStatsCalculator.calculate(
            listOf(
                rest(actual = 120, reason = RestEndReason.COMPLETED),
                rest(actual = 5, reason = RestEndReason.SKIPPED),
                rest(actual = 300, reason = RestEndReason.CANCELLED),
            ),
        )

        assertEquals(1, stats.samples)
        assertEquals(120, stats.averageSeconds)
    }

    @Test
    fun `skrajne czasy wypadaja z proby`() {
        val stats = RestStatsCalculator.calculate(
            listOf(
                rest(actual = 5, reason = RestEndReason.COMPLETED),
                rest(actual = 9, reason = RestEndReason.COMPLETED),
                rest(actual = 10, reason = RestEndReason.COMPLETED),
                rest(actual = 900, reason = RestEndReason.COMPLETED),
                rest(actual = 901, reason = RestEndReason.COMPLETED),
                rest(actual = null, reason = RestEndReason.COMPLETED),
            ),
        )

        assertEquals(2, stats.samples)
        assertEquals(10, stats.minSeconds)
        assertEquals(900, stats.maxSeconds)
    }

    @Test
    fun `srednia mediana i zakres licza sie z proby`() {
        // Próba: 60, 90, 120, 180 → średnia 112 (112,5), mediana 105.
        val stats = RestStatsCalculator.calculate(
            listOf(
                rest(actual = 60, reason = RestEndReason.COMPLETED, startedAt = 1_000L),
                rest(actual = 90, reason = RestEndReason.COMPLETED, startedAt = 2_000L),
                rest(actual = 120, reason = RestEndReason.EXTENDED, startedAt = 3_000L),
                rest(actual = 180, reason = RestEndReason.COMPLETED, startedAt = 4_000L),
            ),
        )

        assertEquals(4, stats.samples)
        assertEquals(113, stats.averageSeconds)
        assertEquals(105, stats.medianSeconds)
        assertEquals(60, stats.minSeconds)
        assertEquals(180, stats.maxSeconds)
        assertEquals(180, stats.lastSeconds)
    }

    @Test
    fun `ostatnia przerwa to najnowsza w czasie`() {
        val stats = RestStatsCalculator.calculate(
            listOf(
                rest(actual = 200, reason = RestEndReason.COMPLETED, startedAt = 9_000L),
                rest(actual = 80, reason = RestEndReason.COMPLETED, startedAt = 1_000L),
            ),
        )

        assertEquals(200, stats.lastSeconds)
    }

    @Test
    fun `srednia z ostatnich treningow bierze tylko piec najnowszych sesji`() {
        // Sześć treningów po jednej przerwie; najstarszy (sesja 1) nie liczy się
        // do „ostatnich”, ale wchodzi do średniej z całości.
        val intervals = (1L..6L).map { session ->
            rest(
                actual = (session * 100).toInt(), // 100, 200, 300, 400, 500, 600
                reason = RestEndReason.COMPLETED,
                startedAt = session * 1_000L,
                sessionId = session,
            )
        }

        val stats = RestStatsCalculator.calculate(intervals)

        assertEquals(6, stats.samples)
        assertEquals(6, stats.sessions)
        assertEquals(5, stats.recentSessions)
        // Najnowsze pięć sesji: 200, 300, 400, 500, 600 → 400.
        assertEquals(400, stats.recentAverageSeconds)
    }

    @Test
    fun `przerwa bez sesji nie liczy sie jako trening`() {
        val stats = RestStatsCalculator.calculate(
            listOf(
                rest(actual = 120, reason = RestEndReason.COMPLETED, sessionId = null),
            ),
        )

        assertEquals(1, stats.samples)
        assertEquals(0, stats.sessions)
        assertNull(stats.recentAverageSeconds)
    }

    @Test
    fun `mediana parzystej proby jest srednia dwoch srodkowych`() {
        val stats = RestStatsCalculator.calculate(
            listOf(
                rest(actual = 100, reason = RestEndReason.COMPLETED, startedAt = 1_000L),
                rest(actual = 121, reason = RestEndReason.COMPLETED, startedAt = 2_000L),
            ),
        )

        assertEquals(111, stats.medianSeconds)
    }

    private fun rest(
        actual: Int?,
        reason: RestEndReason,
        startedAt: Long = 1_000L,
        sessionId: Long? = 7L,
    ): RestInterval = RestInterval(
        id = startedAt,
        exerciseId = 1L,
        sessionId = sessionId,
        setLogId = null,
        startedAtEpochMs = startedAt,
        endedAtEpochMs = startedAt + (actual ?: 0) * 1_000L,
        plannedSeconds = actual,
        actualSeconds = actual,
        reason = reason,
        isAuto = true,
    )
}
