package com.gymtracker.domain.body

import com.gymtracker.domain.model.BodyEntry
import com.gymtracker.domain.stats.StatsRange
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Postęp jednego wskaźnika karty postaci.
 *
 * Podział na [points] i resztę jest ten sam co w statystykach ćwiczenia:
 * wykres pokazuje **wybrany zakres**, a „ostatnio”, zmiany i seria liczą się
 * z **całej historii** - zmiana z ostatnich 90 dni nie przestaje istnieć
 * dlatego, że patrzymy na ostatni miesiąc.
 */
data class BodyProgress(
    /** Punkty w wybranym zakresie, rosnąco po dacie. */
    val points: List<BodyPoint> = emptyList(),
    /** Ostatni pomiar w całej historii. */
    val latest: BodyPoint? = null,
    /** Zmiana względem poprzedniego pomiaru. */
    val previous: BodyChange? = null,
    val last30Days: BodyChange? = null,
    val last90Days: BodyChange? = null,
    /** Ile dni minęło od ostatniego pomiaru (0 = dziś). */
    val daysSinceLatest: Long? = null,
    /** Ile dni z rzędu mierzymy, licząc od ostatniego pomiaru. */
    val streakDays: Int = 0,
    /** Liczba pomiarów w wybranym zakresie. */
    val entriesInRange: Int = 0,
) {
    val hasData: Boolean get() = latest != null

    /** Czy w zakresie jest coś do narysowania (punkt albo dwa). */
    val hasChart: Boolean get() = points.size >= MIN_CHART_POINTS

    private companion object {
        /** Jeden punkt to kropka, nie wykres - do linii potrzeba dwóch pomiarów. */
        const val MIN_CHART_POINTS = 2
    }
}

/** Punkt wykresu: dzień i wartość. */
data class BodyPoint(val date: LocalDate, val value: Double)

/**
 * Zmiana wartości względem punktu odniesienia.
 *
 * [days] jest częścią wyniku, a nie ozdobnikiem: „-2 kg” w tydzień i w rok to
 * dwie zupełnie różne informacje, a bez daty obie wyglądają tak samo.
 */
data class BodyChange(
    val value: Double,
    val days: Long,
    /** Wartość, od której liczymy zmianę - pokazywana w opisie. */
    val from: BodyPoint,
    val to: BodyPoint,
)

/**
 * Czysta arytmetyka karty postaci.
 *
 * Trzy decyzje warte zapisania:
 *
 * 1. **Punktem odniesienia dla „30 dni” jest najnowszy pomiar z tego dnia albo
 *    starszy**, a nie pomiar dokładnie sprzed 30 dni - mierzymy się wtedy, kiedy
 *    się mierzymy, więc „od miesiąca” znaczy „od tego, co mieliśmy miesiąc temu”.
 * 2. **Brak punktu odniesienia to brak zmiany.** Zamiast liczyć od zera
 *    („przytyłem 82 kg”) oddajemy `null`, a UI po prostu nie pokazuje wiersza.
 * 3. **Seria liczy się tylko wtedy, gdy ostatni pomiar jest świeży** (dziś albo
 *    wczoraj). Inaczej „seria 12 dni” sprzed pół roku wisiałaby na ekranie jako
 *    aktualny wynik.
 */
object BodyProgressCalculator {

    fun calculate(
        entries: List<BodyEntry>,
        range: StatsRange,
        today: LocalDate,
    ): BodyProgress {
        if (entries.isEmpty()) return BodyProgress()

        // Historia rosnąco po dacie i **jeden punkt na dzień**: przy dwóch
        // pomiarach tego samego dnia wygrywa później zapisany (poprawka), bo to
        // on jest aktualną wartością dnia. Baza pilnuje tego kluczem `UNIQUE`,
        // ale wykres nie może dostać dwóch punktów w tym samym dniu - oś X
        // cofałaby się, a linia rysowałaby pionową kreskę.
        val history = entries
            .sortedBy { entry -> entry.measuredAtEpochMs }
            .associateBy { entry -> entry.date }
            .map { (date, entry) -> BodyPoint(date, entry.value) }
            .sortedBy { point -> point.date }

        val latest = history.last()
        val previous = history.getOrNull(history.size - 2)
        val points = history.filter { point -> range.contains(point.date, today) }

        return BodyProgress(
            points = points,
            latest = latest,
            previous = change(from = previous, to = latest),
            last30Days = change(from = reference(history, today.minusDays(30)), to = latest),
            last90Days = change(from = reference(history, today.minusDays(90)), to = latest),
            daysSinceLatest = ChronoUnit.DAYS.between(latest.date, today).coerceAtLeast(0),
            streakDays = streak(history, today),
            entriesInRange = points.size,
        )
    }

    /**
     * Najnowszy pomiar z [day] albo starszy - punkt odniesienia dla zmiany.
     * Dla pomiaru dokładnie z tego dnia zmiana jest zerowa i to jest w porządku.
     */
    private fun reference(history: List<BodyPoint>, day: LocalDate): BodyPoint? =
        history.lastOrNull { point -> !point.date.isAfter(day) }

    private fun change(from: BodyPoint?, to: BodyPoint): BodyChange? {
        if (from == null || from.date == to.date) return null
        return BodyChange(
            value = to.value - from.value,
            days = ChronoUnit.DAYS.between(from.date, to.date),
            from = from,
            to = to,
        )
    }

    /**
     * Dni z rzędu z pomiarem, licząc wstecz od ostatniego pomiaru.
     *
     * Seria żyje tylko wtedy, gdy ostatni pomiar jest z dziś albo z wczoraj:
     * pomiar sprzed tygodnia to koniec serii, a nie jej dwunasty dzień.
     */
    private fun streak(history: List<BodyPoint>, today: LocalDate): Int {
        val measured = history.map { point -> point.date }.toSet()
        val latest = history.last().date
        val fresh = latest == today || latest == today.minusDays(1)
        if (!fresh) return 0

        var day = latest
        var days = 0
        while (day in measured) {
            days++
            day = day.minusDays(1)
        }
        return days
    }
}
