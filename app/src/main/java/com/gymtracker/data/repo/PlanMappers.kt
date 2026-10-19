package com.gymtracker.data.repo

import com.gymtracker.data.db.entities.PlanDayEntity
import com.gymtracker.data.db.entities.PlanEntity
import com.gymtracker.data.db.entities.RestIntervalEntity
import com.gymtracker.data.db.entities.SessionEntity
import com.gymtracker.data.db.entities.SetLogEntity
import com.gymtracker.data.db.rows.PlanDayOptionRow
import com.gymtracker.data.db.rows.PlanExerciseRow
import com.gymtracker.data.db.rows.SessionExerciseRow
import com.gymtracker.domain.model.DayWorkout
import com.gymtracker.domain.model.Plan
import com.gymtracker.domain.model.PlanDay
import com.gymtracker.domain.model.PlanDayOption
import com.gymtracker.domain.model.PlanExercise
import com.gymtracker.domain.model.RepsUnit
import com.gymtracker.domain.model.RestEndReason
import com.gymtracker.domain.model.RestInterval
import com.gymtracker.domain.model.SessionExercise
import com.gymtracker.domain.model.SessionStatus
import com.gymtracker.domain.model.SetLog
import com.gymtracker.domain.model.SetLogDraft
import com.gymtracker.domain.model.WorkoutSession
import java.time.LocalDate
import java.time.ZoneId

/** Wiersz bazy → model domenowy (plany, sesje, zalogowane serie). */

internal fun PlanEntity.toDomain(): Plan = Plan(
    id = id,
    externalId = externalId,
    name = name,
    description = description,
    sourceUrl = sourceUrl,
    author = author,
    isActive = isActive,
    isArchived = isArchived,
)

internal fun PlanDayOptionRow.toDomain(): PlanDayOption = PlanDayOption(
    id = dayId,
    planName = planName,
    dayName = dayName,
    exerciseCount = exerciseCount,
)

internal fun PlanDayEntity.toDomain(exercises: List<PlanExercise>): PlanDay = PlanDay(
    id = id,
    planId = planId,
    name = name,
    dayOfWeek = dayOfWeek,
    position = position,
    note = note,
    exercises = exercises,
)

internal fun PlanExerciseRow.toDomain(): PlanExercise = PlanExercise(
    id = entry.id,
    exerciseId = entry.exerciseId,
    exerciseName = exerciseName,
    muscleGroup = exerciseMuscleGroup,
    equipment = exerciseEquipment,
    position = entry.position,
    sets = entry.targetSets,
    repsMin = entry.targetRepsMin,
    repsMax = entry.targetRepsMax,
    repsUnit = RepsUnit.fromWire(entry.targetRepsUnit),
    weightKg = entry.targetWeightKg,
    restSeconds = entry.restSeconds,
    supersetGroup = entry.supersetGroup,
    alternativeGroup = entry.alternativeGroup,
    isDefaultChoice = entry.isDefaultChoice,
    note = entry.note,
)

internal fun SessionEntity.toDomain(): WorkoutSession = WorkoutSession(
    id = id,
    planId = planId,
    planDayId = planDayId,
    name = name,
    startedAtEpochMs = startedAtEpochMs,
    finishedAtEpochMs = finishedAtEpochMs,
    status = SessionStatus.fromWire(status),
    note = note,
    pausedTotalMs = pausedTotalMs,
    pausedAtEpochMs = pausedAtEpochMs,
    sessionDate = sessionDate?.let { value -> LocalDate.parse(value) },
)

/** Trening widziany z kalendarza: dzień liczony w strefie urządzenia. */
internal fun SessionEntity.toDayWorkout(zone: ZoneId): DayWorkout = DayWorkout(
    sessionId = id,
    date = toDomain().dateIn(zone),
    name = name,
    status = SessionStatus.fromWire(status),
)

internal fun SessionExerciseRow.toDomain(logs: List<SetLog> = emptyList()): SessionExercise =
    SessionExercise(
        id = entry.id,
        exerciseId = entry.exerciseId,
        exerciseName = exerciseName,
        muscleGroup = exerciseMuscleGroup,
        position = entry.position,
        targetSets = entry.targetSets,
        repsMin = entry.targetRepsMin,
        repsMax = entry.targetRepsMax,
        repsUnit = RepsUnit.fromWire(entry.targetRepsUnit),
        restSeconds = entry.restSeconds,
        exerciseDefaultRestSeconds = exerciseDefaultRestSeconds,
        supersetGroup = entry.supersetGroup,
        alternativeGroup = entry.alternativeGroup,
        isDefaultChoice = entry.isDefaultChoice,
        note = entry.note,
        logs = logs,
    )

internal fun SetLogEntity.toDomain(): SetLog = SetLog(
    id = id,
    sessionExerciseId = sessionExerciseId,
    setIndex = setIndex,
    weightKg = weightKg,
    reps = reps,
    durationS = durationS,
    isWarmup = isWarmup,
    isFailure = isFailure,
    completedAtEpochMs = completedAtEpochMs,
    restAfterS = restAfterS,
    restTargetS = restTargetS,
)

internal fun SetLogDraft.toEntity(
    sessionExerciseId: Long,
    setIndex: Int,
    completedAtEpochMs: Long,
): SetLogEntity = SetLogEntity(
    sessionExerciseId = sessionExerciseId,
    setIndex = setIndex,
    weightKg = weightKg,
    reps = reps,
    durationS = durationS,
    isWarmup = isWarmup,
    isFailure = isFailure,
    completedAtEpochMs = completedAtEpochMs,
)

/** Serie pogrupowane po ćwiczeniu treningu - klucz to `session_exercise_id`. */
internal fun List<SetLogEntity>.toDomainByExercise(): Map<Long, List<SetLog>> =
    groupBy { entity -> entity.sessionExerciseId }
        .mapValues { (_, sets) -> sets.map { set -> set.toDomain() } }

internal fun RestIntervalEntity.toDomain(): RestInterval = RestInterval(
    id = id,
    exerciseId = exerciseId,
    sessionId = sessionId,
    setLogId = setLogId,
    startedAtEpochMs = startedAtEpochMs,
    endedAtEpochMs = endedAtEpochMs,
    plannedSeconds = plannedSeconds,
    actualSeconds = actualSeconds,
    reason = RestEndReason.fromWire(endedReason),
    isAuto = isAuto,
)
