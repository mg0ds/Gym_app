package com.gymtracker.data.repo

import com.gymtracker.data.db.rows.ExerciseSetRow
import com.gymtracker.data.db.rows.SessionAggregateRow
import com.gymtracker.domain.stats.ExerciseSetEntry
import com.gymtracker.domain.stats.SessionAggregate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Agregat z SQL → model domenowy listy historii. */
internal fun SessionAggregateRow.toDomain(): SessionAggregate = SessionAggregate(
    sessionId = sessionId,
    workingSets = workingSets,
    volumeKg = volumeKg,
    totalReps = totalReps,
    bestWeightKg = bestWeightKg,
)

/**
 * Wiersz serii → wejście kalkulatora postępu.
 *
 * Datę liczymy **tutaj**, bo tylko warstwa danych zna strefę urządzenia.
 * Kalkulator dostaje gotowy dzień i dzięki temu jest czystą funkcją.
 *
 * Dzień treningu ma pierwszeństwo nad chwilą startu: trening puszczony z wpisu
 * na 27 października liczy się do 27 października, nawet jeśli zaczynał się
 * wcześniej.
 */
internal fun ExerciseSetRow.toEntry(zone: ZoneId): ExerciseSetEntry = ExerciseSetEntry(
    sessionId = sessionId,
    date = sessionDate
        ?.let { value -> LocalDate.parse(value) }
        ?: Instant.ofEpochMilli(sessionStartedAtEpochMs).atZone(zone).toLocalDate(),
    setIndex = setIndex,
    weightKg = weightKg,
    reps = reps,
    durationS = durationS,
    isWarmup = isWarmup,
)
