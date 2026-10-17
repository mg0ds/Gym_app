package com.gymtracker.domain.stats

import com.gymtracker.domain.model.RestInterval
import kotlin.math.roundToInt

/**
 * Podsumowanie przerw dla jednego ćwiczenia.
 *
 * [recentAverageSeconds] liczy się z **ostatnich** treningów, a nie z całej
 * historii: „średnia z dwóch lat” zlewa formę sprzed pół roku z dzisiejszą,
 * więc do decyzji o następnej przerwie nadaje się tylko ta pierwsza.
 */
data class RestStats(
    /** Ile przerw weszło do średniej. */
    val samples: Int = 0,
    /** Z ilu treningów te przerwy pochodzą. */
    val sessions: Int = 0,
    val averageSeconds: Int? = null,
    val medianSeconds: Int? = null,
    val minSeconds: Int? = null,
    val maxSeconds: Int? = null,
    /** Najnowsza przerwa („ostatnio: 2:05”). */
    val lastSeconds: Int? = null,
    val recentSessions: Int = 0,
    val recentAverageSeconds: Int? = null,
) {
    val hasSamples: Boolean get() = samples > 0
}

/**
 * Czysta arytmetyka statystyk przerw - jedno miejsce odpowiada na pytanie
 * „co wchodzi do średniej”.
 *
 * Odsiewamy trzaski: przerwy krótsze niż [MIN_SAMPLE_SECONDS] (np. „pomiń” po
 * 3 sekundach) i dłuższe niż [MAX_SAMPLE_SECONDS] (przerwa na telefon,
 * zapomniany timer) zafałszowałyby średnią bardziej niż jej brak.
 */
object RestStatsCalculator {

    const val MIN_SAMPLE_SECONDS = 10
    const val MAX_SAMPLE_SECONDS = 900
    const val RECENT_SESSION_LIMIT = 5

    fun calculate(intervals: List<RestInterval>): RestStats {
        val samples = intervals
            .filter { interval ->
                interval.countsToStats &&
                    interval.actualSeconds != null &&
                    interval.actualSeconds in MIN_SAMPLE_SECONDS..MAX_SAMPLE_SECONDS
            }
        if (samples.isEmpty()) return RestStats()

        val values = samples.mapNotNull { interval -> interval.actualSeconds }.sorted()
        val middle = values.size / 2
        val median = if (values.size % 2 == 1) {
            values[middle]
        } else {
            ((values[middle - 1] + values[middle]) / 2.0).roundToInt()
        }

        // Ostatnie treningi: grupy po sesji, najnowsze na początku. Przerwy bez
        // sesji (nie powinno ich być) nie udają treningu.
        val recent = samples
            .filter { interval -> interval.sessionId != null }
            .groupBy { interval -> interval.sessionId }
            .values
            .sortedByDescending { rows -> rows.maxOf { interval -> interval.startedAtEpochMs } }
            .take(RECENT_SESSION_LIMIT)
        val recentValues = recent.flatten().mapNotNull { interval -> interval.actualSeconds }

        return RestStats(
            samples = values.size,
            sessions = samples.mapNotNull { interval -> interval.sessionId }.distinct().size,
            averageSeconds = values.average().roundToInt(),
            medianSeconds = median,
            minSeconds = values.first(),
            maxSeconds = values.last(),
            lastSeconds = samples.maxByOrNull { interval -> interval.startedAtEpochMs }?.actualSeconds,
            recentSessions = recent.size,
            recentAverageSeconds = recentValues.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
        )
    }
}
