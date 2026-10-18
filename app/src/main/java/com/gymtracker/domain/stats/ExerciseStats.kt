package com.gymtracker.domain.stats

import com.gymtracker.domain.model.RestInterval
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Zakres czasu wykresów i listy historii.
 *
 * Trzymamy go jako liczbę dni w tył od dziś, a nie jako gotowe daty: punkt
 * odniesienia wchodzi dopiero przy liczeniu, więc zakres testuje się bez zegara,
 * a wybór użytkownika nie starzeje się po północy.
 *
 * „3M” to 90 dni w tył, a nie trzy miesiące kalendarzowe - na wykresie postępu
 * liczy się odległość od dziś, nie granica miesiąca.
 */
enum class StatsRange(val daysBack: Long?) {
    MONTH_1(30),
    MONTH_3(90),
    MONTH_6(180),
    YEAR_1(365),
    ALL(null);

    /** Pierwszy dzień zakresu (`null` = bez dolnej granicy). */
    fun start(today: LocalDate): LocalDate? = daysBack?.let { days -> today.minusDays(days) }

    /**
     * Czy dzień wpada w zakres. Zakres patrzy w tył, ale **dni przyszłe
     * wchodzą**: trening da się przypisać do dnia z kalendarza, który jeszcze
     * nie nadszedł (start z wpisu), a wtedy nie może zniknąć z historii.
     */
    fun contains(day: LocalDate, today: LocalDate): Boolean {
        val start = start(today) ?: return true
        return !day.isBefore(start)
    }
}

/**
 * Szacowany ciężar maksymalny na jedno powtórzenie (wzór Epleya).
 *
 * Wzór jest liniowy, więc przy dużych liczbach powtórzeń przestaje cokolwiek
 * znaczyć - dlatego powyżej [RELIABLE_MAX_REPS] nie udajemy dokładności,
 * tylko znaczymy wynik jako przybliżenie (`~`).
 */
object OneRepMax {

    const val RELIABLE_MAX_REPS = 12

    /** `1RM = ciężar × (1 + powtórzenia / 30)`; `null`, gdy nie ma z czego liczyć. */
    fun estimate(weightKg: Double?, reps: Int?): Double? {
        if (weightKg == null || reps == null) return null
        if (weightKg <= 0.0 || reps <= 0) return null
        return weightKg * (1.0 + reps / 30.0)
    }

    fun isReliable(reps: Int?): Boolean = reps != null && reps <= RELIABLE_MAX_REPS
}

/**
 * Jedna seria w historii ćwiczenia - **wejście** kalkulatora.
 *
 * To nie jest encja: kalkulator nie wie nic o Room, sesjach ani strefach
 * czasowych. Data jest już policzona przez repozytorium (jedna strefa, jedno
 * miejsce), dzięki czemu cała arytmetyka jest czysta i testowalna bez bazy.
 */
data class ExerciseSetEntry(
    val sessionId: Long,
    val date: LocalDate,
    val setIndex: Int,
    val weightKg: Double?,
    val reps: Int?,
    val isWarmup: Boolean,
)

/** Jedna sesja na wykresie postępu (dzień, w którym ćwiczenie było robione). */
data class ExerciseStatsPoint(
    val date: LocalDate,
    /** Najcięższa seria robocza dnia (`null` = ćwiczenie bez ciężaru). */
    val topSetKg: Double? = null,
    val topSetReps: Int? = null,
    val estimatedOneRepMaxKg: Double? = null,
    /** `true`, gdy 1RM policzono z serii dłuższej niż [OneRepMax.RELIABLE_MAX_REPS]. */
    val approximateOneRepMax: Boolean = false,
    val volumeKg: Double = 0.0,
    val totalReps: Int = 0,
    /** Serie robocze (bez rozgrzewkowych). */
    val sets: Int = 0,
    /** Średnia przerwa po tym ćwiczeniu w tym dniu (`null` = brak próbek). */
    val restAverageSeconds: Int? = null,
)

/** Najcięższa seria albo najlepszy 1RM - z datą, żeby rekord miał kontekst. */
data class ExerciseSetRecord(
    val weightKg: Double,
    val reps: Int?,
    val date: LocalDate,
    val estimatedOneRepMaxKg: Double? = null,
    val approximate: Boolean = false,
)

/** Największa objętość jednego dnia treningowego. */
data class ExerciseVolumeRecord(
    val volumeKg: Double,
    val date: LocalDate,
    val sets: Int,
)

data class ExerciseRecords(
    val heaviestSet: ExerciseSetRecord? = null,
    val bestOneRepMax: ExerciseSetRecord? = null,
    val bestVolume: ExerciseVolumeRecord? = null,
)

/**
 * Historia i statystyki jednego ćwiczenia.
 *
 * [points] to **wybrany zakres** (to, co widać na wykresie), a [records] i
 * [rest] - cała historia: rekord nie przestaje być rekordem dlatego, że
 * patrzymy na ostatni miesiąc. Rozdzielenie jest celowe i widać je w UI.
 */
data class ExerciseStats(
    val points: List<ExerciseStatsPoint> = emptyList(),
    val records: ExerciseRecords = ExerciseRecords(),
    val rest: RestStats = RestStats(),
    /** Treningi w zakresie (nie w całej historii). */
    val sessions: Int = 0,
    val sets: Int = 0,
    val volumeKg: Double = 0.0,
    val totalReps: Int = 0,
) {
    val hasData: Boolean get() = points.isNotEmpty()

    /**
     * Czy w zakresie jest cokolwiek do narysowania na wykresie ciężaru.
     * Ćwiczenie na czas ma punkty (bo trening się odbył), ale nie ma ciężaru -
     * i wtedy zamiast płaskiej linii na zerze pokazujemy wyjaśnienie.
     */
    val hasWeightData: Boolean get() = points.any { point -> point.topSetKg != null }
}

/**
 * Czysta arytmetyka postępu ćwiczenia: co wchodzi na wykres, a co jest rekordem.
 *
 * Serie rozgrzewkowe odpadają wszędzie - one mierzą rozgrzewkę, nie formę.
 * Ćwiczenia na czas (`weightKg == null`) nie mają punktów na wykresie ciężaru,
 * ale nadal liczą się jako trening (patrz [sessions]).
 *
 * Dzień, a nie sesja, jest jednostką wykresu: dwa treningi tego samego dnia
 * sumują się w jeden punkt - inaczej „postęp” skakałby w pionie bez powodu.
 */
object ExerciseStatsCalculator {

    fun calculate(
        entries: List<ExerciseSetEntry>,
        rests: List<RestInterval>,
        range: StatsRange,
        today: LocalDate,
    ): ExerciseStats {
        val working = entries.filter { entry -> !entry.isWarmup }
        val allPoints = points(working, rests)
        val points = allPoints
            .filter { point -> range.contains(point.date, today) }
            .sortedBy { point -> point.date }

        return ExerciseStats(
            points = points,
            records = records(working, allPoints),
            rest = RestStatsCalculator.calculate(rests),
            sessions = points.size,
            sets = points.sumOf { point -> point.sets },
            volumeKg = points.sumOf { point -> point.volumeKg },
            totalReps = points.sumOf { point -> point.totalReps },
        )
    }

    private fun points(
        working: List<ExerciseSetEntry>,
        rests: List<RestInterval>,
    ): List<ExerciseStatsPoint> = working
        .groupBy { entry -> entry.date }
        .map { (date, rows) -> point(date, rows, rests) }
        .sortedBy { point -> point.date }

    private fun point(
        date: LocalDate,
        rows: List<ExerciseSetEntry>,
        rests: List<RestInterval>,
    ): ExerciseStatsPoint {
        val topSet = rows.withWeight().maxByOrNull { entry -> entry.weightKg ?: 0.0 }
        val best = rows.bestOneRepMax()
        val sessionIds = rows.map { entry -> entry.sessionId }.toSet()
        val dayRests = rests
            .filter { interval -> interval.sessionId in sessionIds && RestStatsCalculator.isSample(interval) }
            .mapNotNull { interval -> interval.actualSeconds }

        return ExerciseStatsPoint(
            date = date,
            topSetKg = topSet?.weightKg,
            topSetReps = topSet?.reps,
            estimatedOneRepMaxKg = best?.value,
            approximateOneRepMax = best != null && !OneRepMax.isReliable(best.reps),
            volumeKg = rows.sumOf { entry -> volume(entry) },
            totalReps = rows.sumOf { entry -> entry.reps ?: 0 },
            sets = rows.size,
            restAverageSeconds = dayRests.takeIf { values -> values.isNotEmpty() }?.average()?.roundToInt(),
        )
    }

    /**
     * Rekordy liczymy z całej historii, a nie z wybranego zakresu: rekord nie
     * przestaje być rekordem dlatego, że patrzymy na ostatni miesiąc.
     *
     * Rekord ciężaru i 1RM szukamy w **seriach** (żeby pokazać „100 kg × 5”,
     * a nie tylko liczbę), a rekord objętości w dniach - objętość jest sumą
     * dnia i tylko tam ma sens.
     */
    private fun records(
        working: List<ExerciseSetEntry>,
        points: List<ExerciseStatsPoint>,
    ): ExerciseRecords {
        val heaviest = working
            .filter { entry -> entry.weightKg != null }
            .maxByOrNull { entry -> entry.weightKg ?: 0.0 }
        val best = working
            .mapNotNull { entry ->
                OneRepMax.estimate(entry.weightKg, entry.reps)?.let { value -> entry to value }
            }
            .maxByOrNull { (_, value) -> value }
        val volume = points.maxByOrNull { point -> point.volumeKg }

        return ExerciseRecords(
            heaviestSet = heaviest?.let { entry ->
                ExerciseSetRecord(
                    weightKg = entry.weightKg ?: 0.0,
                    reps = entry.reps,
                    date = entry.date,
                    estimatedOneRepMaxKg = OneRepMax.estimate(entry.weightKg, entry.reps),
                    approximate = !OneRepMax.isReliable(entry.reps),
                )
            },
            bestOneRepMax = best?.let { (entry, value) ->
                ExerciseSetRecord(
                    weightKg = entry.weightKg ?: 0.0,
                    reps = entry.reps,
                    date = entry.date,
                    estimatedOneRepMaxKg = value,
                    approximate = !OneRepMax.isReliable(entry.reps),
                )
            },
            bestVolume = volume
                ?.takeIf { point -> point.volumeKg > 0.0 }
                ?.let { point -> ExerciseVolumeRecord(point.volumeKg, point.date, point.sets) },
        )
    }

    private fun List<ExerciseSetEntry>.withWeight(): List<ExerciseSetEntry> =
        filter { entry -> entry.weightKg != null }

    private fun List<ExerciseSetEntry>.bestOneRepMax(): BestSet? = this
        .mapNotNull { entry ->
            OneRepMax.estimate(entry.weightKg, entry.reps)?.let { value -> BestSet(value, entry.reps) }
        }
        .maxByOrNull { best -> best.value }

    private fun volume(entry: ExerciseSetEntry): Double {
        val weight = entry.weightKg ?: return 0.0
        val reps = entry.reps ?: return 0.0
        return weight * reps
    }

    private data class BestSet(val value: Double, val reps: Int?)
}
