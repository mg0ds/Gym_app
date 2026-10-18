package com.gymtracker.domain.body

import com.gymtracker.domain.model.BodyEntry
import com.gymtracker.domain.model.MetricDirection
import com.gymtracker.domain.stats.StatsRange
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Arytmetyka karty postaci: co widać w zakresie, jak liczy się zmiana i kiedy
 * seria pomiarów jest jeszcze żywa.
 *
 * Testy nie znają bazy ani zegara - daty wchodzą argumentami, więc każdy
 * przypadek da się opisać jedną datą i trzema pomiarami.
 */
class BodyProgressTest {

    private val today = LocalDate.of(2026, 9, 2)

    private fun entry(date: String, value: Double, measuredAt: Long = 0L) = BodyEntry(
        id = 0,
        metricTypeId = 1,
        date = LocalDate.parse(date),
        measuredAtEpochMs = measuredAt,
        value = value,
    )

    @Test
    fun `bez pomiarow nie ma czego liczyc`() {
        val progress = BodyProgressCalculator.calculate(emptyList(), StatsRange.MONTH_3, today)

        assertFalse(progress.hasData)
        assertFalse(progress.hasChart)
        assertNull(progress.latest)
        assertEquals(0, progress.entriesInRange)
    }

    @Test
    fun `jeden pomiar to kropka, a nie wykres`() {
        val progress = BodyProgressCalculator.calculate(
            listOf(entry("2026-09-01", 80.0)),
            StatsRange.MONTH_3,
            today,
        )

        assertTrue(progress.hasData)
        assertFalse(progress.hasChart)
        assertEquals(1, progress.points.size)
        assertNull(progress.previous)
    }

    @Test
    fun `wykres pokazuje tylko wybrany zakres, a podsumowanie cala historie`() {
        val entries = listOf(
            entry("2026-08-01", 80.0),
            entry("2026-09-01", 81.5),
            entry("2026-09-02", 81.0),
        )

        val progress = BodyProgressCalculator.calculate(entries, StatsRange.MONTH_1, today)

        // Zakres 1M sięga 3.08, więc sierpniowy pomiar wypada z wykresu.
        assertEquals(listOf(LocalDate.parse("2026-09-01"), today), progress.points.map { it.date })
        assertEquals(2, progress.entriesInRange)
        // Zmiana „od poprzedniego” patrzy na całą historię: 81.0 - 81.5.
        assertEquals(-0.5, requireNotNull(progress.previous).value, 0.0001)
        assertEquals(1L, requireNotNull(progress.previous).days)
    }

    @Test
    fun `zmiana trzydziestodniowa liczy sie od najnowszego pomiaru z tego dnia albo starszego`() {
        val entries = listOf(
            entry("2026-08-01", 80.0),
            entry("2026-09-02", 81.0),
        )

        val progress = BodyProgressCalculator.calculate(entries, StatsRange.MONTH_1, today)
        val change = requireNotNull(progress.last30Days)

        // 30 dni temu to 3.08 - najnowszy pomiar przed tą datą jest z 1.08.
        assertEquals(LocalDate.parse("2026-08-01"), change.from.date)
        assertEquals(1.0, change.value, 0.0001)
        assertEquals(32L, change.days)
    }

    @Test
    fun `brak punktu odniesienia to brak zmiany, a nie zmiana od zera`() {
        // Tylko jeden pomiar: nie ma od czego liczyć ani „30 dni”, ani „90 dni”.
        val progress = BodyProgressCalculator.calculate(
            listOf(entry("2026-09-02", 81.0)),
            StatsRange.MONTH_3,
            today,
        )

        assertNull(progress.previous)
        assertNull(progress.last30Days)
        assertNull(progress.last90Days)
        assertEquals(1, progress.entriesInRange)
    }

    @Test
    fun `zmiana liczy sie od najnowszego pomiaru przed data odniesienia`() {
        val entries = listOf(
            entry("2026-01-01", 80.0),
            entry("2026-09-02", 81.0),
        )

        val progress = BodyProgressCalculator.calculate(entries, StatsRange.MONTH_3, today)
        val change = requireNotNull(progress.last90Days)

        // Zakres 3M sięga 4.06 - styczniowy pomiar wypada z wykresu, ale jest
        // punktem odniesienia dla zmiany z 90 dni (i to on jest w opisie).
        assertEquals(1, progress.points.size)
        assertEquals(LocalDate.parse("2026-01-01"), change.from.date)
        assertEquals(1.0, change.value, 0.0001)
        assertEquals(244L, change.days)
        // „Od poprzedniego” nadal działa - patrzy na całą historię.
        assertEquals(1.0, requireNotNull(progress.previous).value, 0.0001)
    }

    @Test
    fun `dwa pomiary tego samego dnia - liczy sie pozniejszy zapis`() {
        val entries = listOf(
            entry("2026-09-02", 81.0, measuredAt = 100L),
            entry("2026-09-02", 80.5, measuredAt = 200L),
        )

        val progress = BodyProgressCalculator.calculate(entries, StatsRange.MONTH_1, today)

        // Jeden dzień to jeden punkt wykresu - oś X nie może się cofać.
        assertEquals(1, progress.points.size)
        assertEquals(80.5, requireNotNull(progress.latest).value, 0.0001)
        assertNull(progress.previous)
    }

    @Test
    fun `seria liczy kolejne dni z pomiarem wstecz od ostatniego`() {
        val entries = listOf(
            entry("2026-08-31", 80.0),
            entry("2026-09-01", 80.2),
            entry("2026-09-02", 80.4),
        )

        val progress = BodyProgressCalculator.calculate(entries, StatsRange.MONTH_1, today)

        assertEquals(3, progress.streakDays)
        assertEquals(0L, progress.daysSinceLatest)
    }

    @Test
    fun `seria sprzed tygodnia jest zakonczona, a nie dwunasta`() {
        val entries = listOf(
            entry("2026-08-19", 80.0),
            entry("2026-08-20", 80.1),
            entry("2026-08-21", 80.2),
        )

        val progress = BodyProgressCalculator.calculate(entries, StatsRange.MONTH_1, today)

        assertEquals(0, progress.streakDays)
        assertEquals(12L, progress.daysSinceLatest)
    }

    @Test
    fun `seria z wczoraj liczy sie dalej`() {
        val progress = BodyProgressCalculator.calculate(
            listOf(entry("2026-09-01", 80.0)),
            StatsRange.MONTH_1,
            today,
        )

        assertEquals(1, progress.streakDays)
    }

    @Test
    fun `kierunek mowi, czy zmiana idzie w dobra strone`() {
        assertEquals(true, MetricDirection.DOWN_GOOD.isImprovement(-1.5))
        assertEquals(false, MetricDirection.DOWN_GOOD.isImprovement(1.5))
        assertEquals(true, MetricDirection.UP_GOOD.isImprovement(1.5))
        assertEquals(false, MetricDirection.UP_GOOD.isImprovement(-1.5))
        // Bez preferencji i bez zmiany nie mamy nic do powiedzenia.
        assertNull(MetricDirection.NEUTRAL.isImprovement(2.0))
        assertNull(MetricDirection.UP_GOOD.isImprovement(0.0))
        assertNull(MetricDirection.UP_GOOD.isImprovement(null))
    }

    @Test
    fun `nieznany kierunek z bazy znaczy brak preferencji`() {
        assertEquals(MetricDirection.NEUTRAL, MetricDirection.fromWire("kiedys_tak"))
        assertEquals(MetricDirection.NEUTRAL, MetricDirection.fromWire(null))
        assertEquals(MetricDirection.UP_GOOD, MetricDirection.fromWire("up_good"))
    }
}
