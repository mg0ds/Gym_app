package com.gymtracker.data.repo

import com.gymtracker.data.db.entities.PlanDayEntity
import com.gymtracker.data.db.entities.PlanEntity
import com.gymtracker.data.db.entities.SessionEntity
import com.gymtracker.data.db.rows.PlanExerciseRow
import com.gymtracker.data.db.rows.SessionExerciseRow
import com.gymtracker.domain.model.Plan
import com.gymtracker.domain.model.PlanDay
import com.gymtracker.domain.model.PlanExercise
import com.gymtracker.domain.model.RepsUnit
import com.gymtracker.domain.model.SessionExercise
import com.gymtracker.domain.model.SessionStatus
import com.gymtracker.domain.model.WorkoutSession

/** Wiersz bazy → model domenowy. Bez tego UI musiałoby znać kolumny Room. */

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
)

internal fun SessionExerciseRow.toDomain(): SessionExercise = SessionExercise(
    id = entry.id,
    exerciseId = entry.exerciseId,
    exerciseName = exerciseName,
    muscleGroup = exerciseMuscleGroup,
    position = entry.position,
    sets = entry.targetSets,
    repsMin = entry.targetRepsMin,
    repsMax = entry.targetRepsMax,
    repsUnit = RepsUnit.fromWire(entry.targetRepsUnit),
    restSeconds = entry.restSeconds,
    supersetGroup = entry.supersetGroup,
    alternativeGroup = entry.alternativeGroup,
    isDefaultChoice = entry.isDefaultChoice,
    note = entry.note,
)
