package com.gymtracker.domain.repository

import com.gymtracker.domain.model.Plan
import com.gymtracker.domain.model.PlanDay
import com.gymtracker.domain.model.PlanDayDraft
import com.gymtracker.domain.model.PlanDetails
import com.gymtracker.domain.model.PlanDraft
import com.gymtracker.domain.model.PlanExerciseDraft
import com.gymtracker.domain.model.PlanExerciseTargets
import kotlinx.coroutines.flow.Flow

/**
 * Plany treningowe: plan → dni → ćwiczenia dni.
 *
 * Kolejność zmienia się przez podanie **całej nowej listy identyfikatorów**
 * ([reorderDays], [reorderExercises]) - to naturalnie pasuje do przeciągania
 * i daje się sprawdzić w testach bez zgadywania indeksów.
 */
interface PlanRepository {

    fun observePlans(): Flow<List<Plan>>

    /** Plan z dniami i ćwiczeniami; `null`, gdy plan zniknął. */
    fun observePlanDetails(planId: Long): Flow<PlanDetails?>

    fun observeDay(dayId: Long): Flow<PlanDay?>

    suspend fun getPlan(planId: Long): Plan?

    suspend fun createPlan(draft: PlanDraft): Long

    suspend fun updatePlan(planId: Long, name: String, description: String?)

    suspend fun deletePlan(planId: Long)

    // --- dni ---

    suspend fun addDay(planId: Long, draft: PlanDayDraft): Long

    suspend fun updateDay(dayId: Long, name: String, dayOfWeek: Int?)

    suspend fun deleteDay(dayId: Long)

    suspend fun reorderDays(planId: Long, orderedDayIds: List<Long>)

    // --- ćwiczenia dnia ---

    /** Dodaje ćwiczenie na końcu dnia i zwraca jego id. */
    suspend fun addExercise(dayId: Long, draft: PlanExerciseDraft): Long

    /**
     * Dodaje alternatywę („albo”) do istniejącej pozycji: oba ćwiczenia trafiają
     * do jednej grupy, [anchorPlanExerciseId] zostaje wyborem domyślnym.
     */
    suspend fun addAlternative(anchorPlanExerciseId: Long, draft: PlanExerciseDraft): Long

    suspend fun updateTargets(planExerciseId: Long, targets: PlanExerciseTargets)

    suspend fun removeExercise(planExerciseId: Long)

    suspend fun reorderExercises(dayId: Long, orderedPlanExerciseIds: List<Long>)

    /** Łączy dwie pozycje w superserię (bez przerwy między nimi). */
    suspend fun linkSuperset(firstPlanExerciseId: Long, secondPlanExerciseId: Long)

    suspend fun unlinkSuperset(planExerciseId: Long)

    /** Ustawia pozycję jako domyślną w grupie alternatyw. */
    suspend fun makeDefaultChoice(planExerciseId: Long)
}
