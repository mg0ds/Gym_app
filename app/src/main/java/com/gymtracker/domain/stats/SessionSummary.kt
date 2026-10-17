package com.gymtracker.domain.stats

import com.gymtracker.domain.model.RestInterval
import com.gymtracker.domain.model.SessionStatus
import com.gymtracker.domain.model.WorkoutSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Podsumowanie jednego zakończonego treningu na liście historii. */
data class SessionSummary(
    val sessionId: Long,
    val date: LocalDate,
    val startedAtEpochMs: Long,
    val name: String,
    /** Czas bez pauz (`null` tylko dla treningu, który jeszcze trwa - tu się nie zdarza). */
    val durationMs: Long?,
    val workingSets: Int,
    val volumeKg: Double,
    val totalReps: Int,
    val bestWeightKg: Double?,
    val restAverageSeconds: Int?,
    /** Ćwiczenia treningu - po nich działa filtr historii. */
    val exerciseIds: Set<Long>,
) {
    /**
     * Czy trening ma zapisane serie. Start i koniec bez ani jednej serii to
     * jeszcze nie trening - taka sesja nie ma czego pokazać poza czasem.
     */
    val hasSets: Boolean get() = workingSets > 0
}

/** Agregat jednego treningu policzony w SQL (patrz `StatsDao`). */
data class SessionAggregate(
    val sessionId: Long,
    val workingSets: Int,
    val volumeKg: Double,
    val totalReps: Int,
    val bestWeightKg: Double?,
)

/**
 * Buduje listę historii z surowych kawałków: sesji, agregatów, przerw i mapy
 * ćwiczeń.
 *
 * Czysta funkcja, bo właśnie tutaj siedzą reguły, które łatwo zepsuć:
 * zakończone sesje (nie „przerwane”), średnia przerwa z tych samych próbek,
 * co na karcie ćwiczenia, i kolejność od najnowszych.
 */
object SessionSummaryCalculator {

    fun calculate(
        sessions: List<WorkoutSession>,
        aggregates: List<SessionAggregate>,
        rests: List<RestInterval>,
        exerciseIdsBySession: Map<Long, Set<Long>>,
        zone: ZoneId,
    ): List<SessionSummary> {
        val aggregateBySession = aggregates.associateBy { aggregate -> aggregate.sessionId }
        val restsBySession = rests.groupBy { interval -> interval.sessionId }

        return sessions
            .filter { session -> session.status == SessionStatus.FINISHED }
            .map { session -> summary(session, aggregateBySession, restsBySession, exerciseIdsBySession, zone) }
            .sortedByDescending { summary -> summary.startedAtEpochMs }
    }

    /**
     * Filtr listy: zakres dat, ćwiczenie i nazwa treningu.
     *
     * `exerciseId` i `name` są opcjonalne (`null` = bez zawężania), a zakres
     * działa na dacie treningu - przyszłe wpisy kalendarza nie są treningami,
     * więc i tak ich tu nie ma.
     */
    fun filter(
        summaries: List<SessionSummary>,
        range: StatsRange,
        today: LocalDate,
        exerciseId: Long? = null,
        name: String? = null,
    ): List<SessionSummary> = summaries.filter { summary ->
        range.contains(summary.date, today) &&
            (exerciseId == null || exerciseId in summary.exerciseIds) &&
            (name == null || name == summary.name)
    }

    private fun summary(
        session: WorkoutSession,
        aggregates: Map<Long, SessionAggregate>,
        restsBySession: Map<Long?, List<RestInterval>>,
        exerciseIdsBySession: Map<Long, Set<Long>>,
        zone: ZoneId,
    ): SessionSummary {
        val aggregate = aggregates[session.id]
        return SessionSummary(
            sessionId = session.id,
            date = Instant.ofEpochMilli(session.startedAtEpochMs).atZone(zone).toLocalDate(),
            startedAtEpochMs = session.startedAtEpochMs,
            name = session.name,
            durationMs = session.durationMs,
            workingSets = aggregate?.workingSets ?: 0,
            volumeKg = aggregate?.volumeKg ?: 0.0,
            totalReps = aggregate?.totalReps ?: 0,
            bestWeightKg = aggregate?.bestWeightKg,
            restAverageSeconds = RestStatsCalculator.averageSeconds(restsBySession[session.id].orEmpty()),
            exerciseIds = exerciseIdsBySession[session.id].orEmpty(),
        )
    }
}
