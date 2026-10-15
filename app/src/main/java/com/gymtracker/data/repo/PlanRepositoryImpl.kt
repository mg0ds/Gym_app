package com.gymtracker.data.repo

import androidx.room.withTransaction
import com.gymtracker.data.db.AppDatabase
import com.gymtracker.data.db.daos.PlanDao
import com.gymtracker.data.db.entities.PlanDayEntity
import com.gymtracker.data.db.entities.PlanEntity
import com.gymtracker.data.db.entities.PlanExerciseEntity
import com.gymtracker.domain.model.Plan
import com.gymtracker.domain.model.PlanDay
import com.gymtracker.domain.model.PlanDayDraft
import com.gymtracker.domain.model.PlanDetails
import com.gymtracker.domain.model.PlanDraft
import com.gymtracker.domain.model.PlanExerciseDraft
import com.gymtracker.domain.model.PlanExerciseTargets
import com.gymtracker.domain.repository.PlanRepository
import com.gymtracker.domain.time.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Plany treningowe.
 *
 * Wszystkie zmiany idą w transakcji ([withTransaction]), bo prawie każda dotyka
 * kilku wierszy (np. przestawienie kolejności = wiele `UPDATE`). Wyjątkiem nie
 * są tu nawet „pojedyncze” operacje: usunięcie ćwiczenia z grupy „albo” musi
 * poprawić grupę, a zmiana dnia odświeżyć `updated_at` planu.
 */
@Singleton
class PlanRepositoryImpl @Inject constructor(
    private val planDao: PlanDao,
    private val database: AppDatabase,
    private val timeProvider: TimeProvider,
) : PlanRepository {

    override fun observePlans(): Flow<List<Plan>> =
        planDao.observePlans().map { plans -> plans.map(PlanEntity::toDomain) }

    override fun observePlanDetails(planId: Long): Flow<PlanDetails?> = combine(
        planDao.observePlan(planId),
        planDao.observeDays(planId),
        planDao.observePlanExercises(planId),
    ) { plan, days, rows ->
        if (plan == null) return@combine null
        val byDay = rows.groupBy { row -> row.entry.planDayId }
        PlanDetails(
            plan = plan.toDomain(),
            days = days.map { day ->
                day.toDomain(byDay[day.id].orEmpty().map { row -> row.toDomain() })
            },
        )
    }

    override fun observeDay(dayId: Long): Flow<PlanDay?> = combine(
        planDao.observeDay(dayId),
        planDao.observeDayExercises(dayId),
    ) { day, rows -> day?.toDomain(rows.map { row -> row.toDomain() }) }

    override suspend fun getPlan(planId: Long): Plan? = planDao.getPlan(planId)?.toDomain()

    override suspend fun createPlan(draft: PlanDraft): Long {
        val now = timeProvider.nowEpochMs()
        return planDao.insertPlan(
            PlanEntity(
                name = draft.name.trim(),
                description = draft.description.cleanText(),
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            ),
        )
    }

    override suspend fun updatePlan(planId: Long, name: String, description: String?) =
        database.withTransaction {
            val plan = planDao.getPlan(planId) ?: return@withTransaction
            planDao.updatePlan(
                plan.copy(
                    name = name.trim(),
                    description = description.cleanText(),
                    updatedAtEpochMs = timeProvider.nowEpochMs(),
                ),
            )
        }

    override suspend fun deletePlan(planId: Long) {
        planDao.deletePlan(planId)
    }

    override suspend fun addDay(planId: Long, draft: PlanDayDraft): Long = database.withTransaction {
        val position = planDao.maxDayPosition(planId) + 1
        val id = planDao.insertDay(
            PlanDayEntity(
                planId = planId,
                name = draft.name.trim(),
                dayOfWeek = draft.dayOfWeek,
                position = position,
                note = draft.note.cleanText(),
            ),
        )
        touchPlan(planId)
        id
    }

    override suspend fun updateDay(dayId: Long, name: String, dayOfWeek: Int?) =
        database.withTransaction {
            val day = planDao.getDay(dayId) ?: return@withTransaction
            planDao.updateDay(day.copy(name = name.trim(), dayOfWeek = dayOfWeek))
            touchPlan(day.planId)
        }

    override suspend fun deleteDay(dayId: Long) = database.withTransaction {
        val day = planDao.getDay(dayId) ?: return@withTransaction
        planDao.deleteDay(dayId)
        renumberDays(day.planId)
        touchPlan(day.planId)
    }

    override suspend fun reorderDays(planId: Long, orderedDayIds: List<Long>) =
        database.withTransaction {
            val current = planDao.getDays(planId).map { day -> day.id }
            // Rozjechana lista = nie ruszamy niczego (bezpieczniej niż zgadywać).
            if (!current.sameSet(orderedDayIds)) return@withTransaction
            // `plan_days` ma UNIQUE(plan_id, position): najpierw wartości
            // tymczasowe (ujemne), potem docelowe.
            orderedDayIds.forEachIndexed { index, id -> planDao.setDayPosition(id, -(index + 1)) }
            orderedDayIds.forEachIndexed { index, id -> planDao.setDayPosition(id, index + 1) }
            touchPlan(planId)
        }

    override suspend fun addExercise(dayId: Long, draft: PlanExerciseDraft): Long =
        database.withTransaction {
            val day = checkNotNull(planDao.getDay(dayId)) { "brak dnia planu $dayId" }
            val id = planDao.insertDayExercise(
                draft.toEntity(dayId, position = planDao.maxExercisePosition(dayId) + 1),
            )
            renumberExercises(dayId)
            touchPlan(day.planId)
            id
        }

    override suspend fun addAlternative(anchorPlanExerciseId: Long, draft: PlanExerciseDraft): Long =
        database.withTransaction {
            val anchor = checkNotNull(planDao.getDayExercise(anchorPlanExerciseId)) {
                "brak ćwiczenia planu $anchorPlanExerciseId"
            }
            val group = anchor.alternativeGroup
                ?: "${anchor.exerciseId}-or-${draft.exerciseId}"
            if (anchor.alternativeGroup == null) {
                planDao.updateDayExercise(
                    anchor.copy(alternativeGroup = group, isDefaultChoice = true),
                )
            }
            val id = planDao.insertDayExercise(
                draft.toEntity(
                    anchor.planDayId,
                    position = planDao.maxExercisePosition(anchor.planDayId) + 1,
                ).copy(
                    targetSets = draft.sets ?: anchor.targetSets,
                    targetRepsMin = draft.repsMin ?: anchor.targetRepsMin,
                    targetRepsMax = draft.repsMax ?: anchor.targetRepsMax,
                    restSeconds = draft.restSeconds ?: anchor.restSeconds,
                    alternativeGroup = group,
                    isDefaultChoice = false,
                ),
            )
            renumberExercises(anchor.planDayId)
            touchPlanOfDay(anchor.planDayId)
            id
        }

    override suspend fun updateTargets(planExerciseId: Long, targets: PlanExerciseTargets) =
        database.withTransaction {
            val row = planDao.getDayExercise(planExerciseId) ?: return@withTransaction
            planDao.updateDayExercise(
                row.copy(
                    targetSets = targets.sets,
                    targetRepsMin = targets.repsMin,
                    targetRepsMax = targets.repsMax,
                    targetRepsUnit = targets.repsUnit.wire,
                    targetWeightKg = targets.weightKg,
                    restSeconds = targets.restSeconds,
                    note = targets.note.cleanText(),
                ),
            )
            touchPlanOfDay(row.planDayId)
        }

    override suspend fun removeExercise(planExerciseId: Long) = database.withTransaction {
        val row = planDao.getDayExercise(planExerciseId) ?: return@withTransaction
        planDao.deleteDayExercise(planExerciseId)
        row.alternativeGroup?.let { group -> repairAlternativeGroup(row.planDayId, group) }
        renumberExercises(row.planDayId)
        touchPlanOfDay(row.planDayId)
    }

    override suspend fun reorderExercises(dayId: Long, orderedPlanExerciseIds: List<Long>) =
        database.withTransaction {
            val current = planDao.getDayExercises(dayId).map { row -> row.entry.id }
            if (!current.sameSet(orderedPlanExerciseIds)) return@withTransaction
            orderedPlanExerciseIds.forEachIndexed { index, id ->
                planDao.setExercisePosition(id, index + 1)
            }
            touchPlanOfDay(dayId)
        }

    override suspend fun linkSuperset(firstPlanExerciseId: Long, secondPlanExerciseId: Long) =
        database.withTransaction {
            val first = checkNotNull(planDao.getDayExercise(firstPlanExerciseId))
            val second = checkNotNull(planDao.getDayExercise(secondPlanExerciseId))
            require(first.planDayId == second.planDayId) { "superseria tylko w jednym dniu" }
            val group = nextSupersetGroup(first.planDayId)
            planDao.updateDayExercise(first.copy(supersetGroup = group))
            planDao.updateDayExercise(second.copy(supersetGroup = group))
            touchPlanOfDay(first.planDayId)
        }

    override suspend fun unlinkSuperset(planExerciseId: Long) = database.withTransaction {
        val row = planDao.getDayExercise(planExerciseId) ?: return@withTransaction
        val group = row.supersetGroup ?: return@withTransaction
        planDao.updateDayExercise(row.copy(supersetGroup = null))
        // Samotne ćwiczenie to już nie superseria - czyścimy ostatniego członka.
        val remaining = planDao.getDayExercises(row.planDayId)
            .filter { it.entry.supersetGroup == group }
        if (remaining.size == 1) {
            planDao.updateDayExercise(remaining.first().entry.copy(supersetGroup = null))
        }
        touchPlanOfDay(row.planDayId)
    }

    override suspend fun makeDefaultChoice(planExerciseId: Long) = database.withTransaction {
        val row = planDao.getDayExercise(planExerciseId) ?: return@withTransaction
        val group = row.alternativeGroup ?: return@withTransaction
        planDao.getDayExercises(row.planDayId)
            .filter { it.entry.alternativeGroup == group }
            .forEach { candidate ->
                planDao.updateDayExercise(
                    candidate.entry.copy(isDefaultChoice = candidate.entry.id == planExerciseId),
                )
            }
        touchPlanOfDay(row.planDayId)
    }

    // --- pomocnicze (wszystkie wołane w transakcji) ---

    /**
     * Usuwa ćwiczenie z grupy „albo”: gdy zostaje jedno, przestaje być wyborem,
     * a gdy zniknął domyślny - domyślnym zostaje pierwszy z pozostałych.
     */
    private suspend fun repairAlternativeGroup(dayId: Long, group: String) {
        val left = planDao.getDayExercises(dayId).filter { it.entry.alternativeGroup == group }
        when {
            left.isEmpty() -> Unit
            left.size == 1 -> planDao.updateDayExercise(
                left.first().entry.copy(alternativeGroup = null, isDefaultChoice = true),
            )
            left.none { it.entry.isDefaultChoice } -> planDao.updateDayExercise(
                left.first().entry.copy(isDefaultChoice = true),
            )
        }
    }

    private suspend fun nextSupersetGroup(dayId: Long): Int {
        val max = planDao.getDayExercises(dayId).mapNotNull { it.entry.supersetGroup }.maxOrNull()
        return (max ?: 0) + 1
    }

    /** Numeruje pozycje dnia gęsto (1, 2, 3...) w obecnej kolejności. */
    private suspend fun renumberExercises(dayId: Long) {
        planDao.getDayExercises(dayId).forEachIndexed { index, row ->
            planDao.setExercisePosition(row.entry.id, index + 1)
        }
    }

    private suspend fun renumberDays(planId: Long) {
        planDao.getDays(planId).forEachIndexed { index, day ->
            planDao.setDayPosition(day.id, -(index + 1))
        }
        planDao.getDays(planId).forEachIndexed { index, day ->
            planDao.setDayPosition(day.id, index + 1)
        }
    }

    private suspend fun touchPlanOfDay(dayId: Long) {
        planDao.getDay(dayId)?.let { day -> touchPlan(day.planId) }
    }

    private suspend fun touchPlan(planId: Long) {
        val plan = planDao.getPlan(planId) ?: return
        planDao.updatePlan(plan.copy(updatedAtEpochMs = timeProvider.nowEpochMs()))
    }
}

/** Wersja startowa wiersza; pozycje są potem numerowane gęsto przez `renumberExercises`. */
private fun PlanExerciseDraft.toEntity(dayId: Long, position: Int): PlanExerciseEntity =
    PlanExerciseEntity(
        planDayId = dayId,
        exerciseId = exerciseId,
        position = position,
        targetSets = sets,
        targetRepsMin = repsMin,
        targetRepsMax = repsMax,
        targetRepsUnit = repsUnit.wire,
        targetWeightKg = weightKg,
        restSeconds = restSeconds,
        note = note.cleanText(),
    )

private fun String?.cleanText(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun List<Long>.sameSet(other: List<Long>): Boolean =
    size == other.size && toSet() == other.toSet()
