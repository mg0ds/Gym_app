package com.gymtracker.domain.stats

import com.gymtracker.domain.model.RestEndReason
import com.gymtracker.domain.model.RestInterval
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Czysta arytmetyka postępu ćwiczenia - bez bazy, bez zegara.
 *
 * Punktem odniesienia jest jawnie podane „dziś”, więc testy nie zmienią
 * znaczenia po zmianie daty w systemie.
 */
class ExerciseStatsCalculatorTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 17)

    private fun set(
        sessionId: Long,
        daysAgo: Long,
        weightKg: Double? = null,
        reps: Int? = null,
        isWarmup: Boolean = false,
        setIndex: Int = 1,
    ): ExerciseSetEntry = ExerciseSetEntry(
        sessionId = sessionId,
        date = today.minusDays(daysAgo),
        setIndex = setIndex,
        weightKg = weightKg,
        reps = reps,
        isWarmup = isWarmup,
    )

    private fun rest(
        sessionId: Long?,
        seconds: Int?,
        reason: RestEndReason = RestEndReason.COMPLETED,
    ): RestInterval = RestInterval(
        id = 0,
        exerciseId = 1,
        sessionId = sessionId,
        setLogId = null,
        startedAtEpochMs = 0,
        endedAtEpochMs = (seconds ?: 0) * 1_000L,
        plannedSeconds = 90,
        actualSeconds = seconds,
        reason = reason,
        isAuto = true,
    )

    private fun calculate(
        entries: List<ExerciseSetEntry>,
        rests: List<RestInterval> = emptyList(),
        range: StatsRange = StatsRange.ALL,
    ): ExerciseStats = ExerciseStatsCalculator.calculate(entries, rests, range, today)

    @Test
    fun `brak serii daje puste statystyki`() {
        val stats = calculate(emptyList())

        assertFalse(stats.hasData)
        assertFalse(stats.hasWeightData)
        assertEquals(0, stats.sessions)
        assertEquals(0, stats.sets)
        assertNull(stats.records.heaviestSet)
        assertNull(stats.records.bestOneRepMax)
        assertNull(stats.records.bestVolume)
    }

    @Test
    fun `serie rozgrzewkowe nie wchodza do objetosci, serii ani rekordu`() {
        val stats = calculate(
            listOf(
                set(1, 0, weightKg = 60.0, reps = 10, isWarmup = true),
                set(1, 0, weightKg = 100.0, reps = 5),
            ),
        )

        val point = stats.points.single()
        assertEquals(100.0, point.topSetKg!!, 0.001)
        assertEquals(1, point.sets)
        assertEquals(100.0 * 5, point.volumeKg, 0.001)
        assertEquals(100.0, stats.records.heaviestSet!!.weightKg, 0.001)
    }

    @Test
    fun `dwa treningi jednego dnia daja jeden punkt`() {
        val stats = calculate(
            listOf(
                set(1, 0, weightKg = 80.0, reps = 5),
                set(2, 0, weightKg = 100.0, reps = 3),
            ),
        )

        val point = stats.points.single()
        assertEquals("dwa treningi to jeden dzień", 2, point.sets)
        assertEquals(100.0, point.topSetKg!!, 0.001)
        assertEquals(80.0 * 5 + 100.0 * 3, point.volumeKg, 0.001)
        assertEquals(8, point.totalReps)
    }

    @Test
    fun `punkty sa posortowane po dacie, a 1RM liczy sie z najlepszej serii dnia`() {
        val stats = calculate(
            listOf(
                set(1, 10, weightKg = 100.0, reps = 5),
                set(2, 1, weightKg = 90.0, reps = 10),
            ),
        )

        assertEquals(listOf(today.minusDays(10), today.minusDays(1)), stats.points.map { it.date })
        // 100 × (1 + 5/30) = 116,67; 90 × (1 + 10/30) = 120 -> wygrywa seria 90 kg.
        assertEquals(120.0, stats.points.last().estimatedOneRepMaxKg!!, 0.01)
    }

    @Test
    fun `1RM powyzej 12 powtorzen jest oznaczone jako przyblizenie`() {
        val reliable = calculate(listOf(set(1, 0, weightKg = 100.0, reps = 12))).points.single()
        val approximate = calculate(listOf(set(1, 0, weightKg = 60.0, reps = 20))).points.single()

        assertFalse(reliable.approximateOneRepMax)
        assertTrue(approximate.approximateOneRepMax)
        assertTrue(approximate.estimatedOneRepMaxKg!! > 0)
    }

    @Test
    fun `zakres odsiewa stare punkty, ale rekordy zostaja z calej historii`() {
        val entries = listOf(
            set(1, 200, weightKg = 140.0, reps = 3),
            set(2, 5, weightKg = 100.0, reps = 5),
        )

        val all = calculate(entries)
        val threeMonths = calculate(entries, range = StatsRange.MONTH_3)

        assertEquals(2, all.points.size)
        assertEquals(1, threeMonths.points.size)
        assertEquals(today.minusDays(5), threeMonths.points.single().date)
        assertEquals("rekord jest rekordem, choć wypadł z zakresu", 140.0, threeMonths.records.heaviestSet!!.weightKg, 0.001)
        assertEquals(today.minusDays(200), threeMonths.records.heaviestSet!!.date)
        assertEquals(1, threeMonths.sessions)
    }

    @Test
    fun `rekord 1RM niesie serie, z ktorej wyszedl, a nie najciezsza serie dnia`() {
        val stats = calculate(
            listOf(
                set(1, 10, weightKg = 100.0, reps = 5),
                set(1, 10, weightKg = 90.0, reps = 10),
            ),
        )

        val record = stats.records.bestOneRepMax!!
        assertEquals(90.0, record.weightKg, 0.001)
        assertEquals(10, record.reps)
        assertEquals(120.0, record.estimatedOneRepMaxKg!!, 0.01)
        assertEquals("najcięższa seria to nadal 100 kg", 100.0, stats.records.heaviestSet!!.weightKg, 0.001)
        assertEquals(5, stats.records.heaviestSet!!.reps)
    }

    @Test
    fun `rekord objetosci to najlepszy dzien`() {
        val stats = calculate(
            listOf(
                set(1, 10, weightKg = 100.0, reps = 5),
                set(1, 10, weightKg = 100.0, reps = 5),
                set(2, 2, weightKg = 60.0, reps = 5),
            ),
        )

        val record = stats.records.bestVolume!!
        assertEquals(1_000.0, record.volumeKg, 0.001)
        assertEquals(2, record.sets)
        assertEquals(today.minusDays(10), record.date)
    }

    @Test
    fun `srednia przerwa dnia liczy tylko odbyte przerwy w rozsadnym zakresie`() {
        val stats = calculate(
            listOf(set(1, 0, weightKg = 100.0, reps = 5), set(2, 0, weightKg = 100.0, reps = 5)),
            rests = listOf(
                rest(1, 60),
                rest(1, 120, RestEndReason.EXTENDED),
                rest(2, 50, RestEndReason.SKIPPED),
                rest(2, 300, RestEndReason.CANCELLED),
                rest(2, 5),
                rest(9, 60),
            ),
        )

        assertEquals(90, stats.points.single().restAverageSeconds)
        assertEquals("podsumowanie ćwiczenia widzi wszystkie jego przerwy", 3, stats.rest.samples)
        assertEquals("także te z innych sesji", 80, stats.rest.averageSeconds)
    }

    @Test
    fun `cwiczenie na czas liczy sie jako trening, ale nie ma danych o ciezarze`() {
        val stats = calculate(
            listOf(
                set(1, 0, weightKg = null, reps = null),
                set(1, 0, weightKg = null, reps = null, setIndex = 2),
            ),
        )

        assertEquals(1, stats.sessions)
        assertEquals(2, stats.sets)
        assertFalse(stats.hasWeightData)
        assertTrue("trening się odbył", stats.hasData)
        assertNull(stats.points.single().topSetKg)
        assertNull(stats.records.heaviestSet)
        assertNull(stats.records.bestVolume)
    }

    @Test
    fun `podsumowanie zakresu liczy serie i objetosc tylko z zakresu`() {
        val stats = calculate(
            listOf(
                set(1, 200, weightKg = 100.0, reps = 10),
                set(2, 5, weightKg = 100.0, reps = 5),
            ),
            range = StatsRange.MONTH_1,
        )

        assertEquals(1, stats.sessions)
        assertEquals(1, stats.sets)
        assertEquals(500.0, stats.volumeKg, 0.001)
        assertEquals(5, stats.totalReps)
    }

    @Test
    fun `1RM Epleya wymaga ciezar i powtorzen`() {
        assertEquals(100.0 * (1 + 5 / 30.0), OneRepMax.estimate(100.0, 5)!!, 0.001)
        assertNull(OneRepMax.estimate(null, 5))
        assertNull(OneRepMax.estimate(100.0, null))
        assertNull(OneRepMax.estimate(0.0, 5))
        assertNull(OneRepMax.estimate(100.0, 0))
        assertTrue(OneRepMax.isReliable(12))
        assertFalse(OneRepMax.isReliable(13))
        assertFalse(OneRepMax.isReliable(null))
    }

    @Test
    fun `zakres liczy dni w tyl i wpuszcza dni przyszle`() {
        assertEquals(today.minusDays(90), StatsRange.MONTH_3.start(today))
        assertNull(StatsRange.ALL.start(today))
        assertTrue(StatsRange.MONTH_1.contains(today, today))
        assertTrue(StatsRange.MONTH_1.contains(today.minusDays(30), today))
        assertFalse(StatsRange.MONTH_1.contains(today.minusDays(31), today))
        // Dzień przyszły wchodzi: trening da się przypisać do zaplanowanego dnia
        // (start z wpisu w kalendarzu), a wtedy nie może zniknąć z historii.
        assertTrue(StatsRange.MONTH_1.contains(today.plusDays(1), today))
        assertTrue(StatsRange.ALL.contains(today.plusDays(365), today))
    }
}
