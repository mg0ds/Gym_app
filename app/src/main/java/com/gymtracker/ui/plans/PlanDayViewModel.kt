package com.gymtracker.ui.plans

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.domain.model.PlanDay
import com.gymtracker.domain.model.PlanExercise
import com.gymtracker.domain.model.PlanExerciseTargets
import com.gymtracker.domain.repository.PlanRepository
import com.gymtracker.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Ćwiczenie dnia razem z jego alternatywami („X albo Y”). */
data class ExerciseGroup(
    val primary: PlanExercise,
    val alternatives: List<PlanExercise>,
)

/** Dzień planu gotowy do pokazania: ćwiczenia pogrupowane po alternatywach. */
data class PlanDayUiState(
    val isLoading: Boolean = true,
    val day: PlanDay? = null,
    val groups: List<ExerciseGroup> = emptyList(),
)

@HiltViewModel
class PlanDayViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val plans: PlanRepository,
) : ViewModel() {

    val dayId: Long = checkNotNull(savedStateHandle[Routes.ARG_DAY_ID]) {
        "brak identyfikatora dnia w trasie"
    }

    val uiState: StateFlow<PlanDayUiState> = plans.observeDay(dayId)
        .map { day ->
            PlanDayUiState(
                isLoading = false,
                day = day,
                groups = day?.exercises?.toGroups().orEmpty(),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = PlanDayUiState(),
        )

    /**
     * Nowa kolejność po przeciągnięciu. Do bazy idzie cała lista dnia:
     * ćwiczenia podstawowe w nowej kolejności, a alternatywy zaraz po swoim.
     */
    fun moveExercise(fromIndex: Int, toIndex: Int) {
        val moved = uiState.value.groups.moveItem(fromIndex, toIndex)
        val ordered = moved.flatMap { group ->
            listOf(group.primary.id) + group.alternatives.map { alternative -> alternative.id }
        }
        viewModelScope.launch { plans.reorderExercises(dayId, ordered) }
    }

    fun updateTargets(planExerciseId: Long, targets: PlanExerciseTargets) {
        viewModelScope.launch { plans.updateTargets(planExerciseId, targets) }
    }

    fun removeExercise(planExerciseId: Long) {
        viewModelScope.launch { plans.removeExercise(planExerciseId) }
    }

    fun linkSuperset(firstPlanExerciseId: Long, secondPlanExerciseId: Long) {
        viewModelScope.launch { plans.linkSuperset(firstPlanExerciseId, secondPlanExerciseId) }
    }

    fun unlinkSuperset(planExerciseId: Long) {
        viewModelScope.launch { plans.unlinkSuperset(planExerciseId) }
    }

    fun makeDefaultChoice(planExerciseId: Long) {
        viewModelScope.launch { plans.makeDefaultChoice(planExerciseId) }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}

/**
 * Dzieli ćwiczenia dnia na podstawowe i podpięte do nich alternatywy.
 * Alternatywa bez swojego ćwiczenia (stan nie powinien się zdarzyć) i tak
 * trafia na listę - lepiej pokazać ją osobno niż zgubić.
 */
internal fun List<PlanExercise>.toGroups(): List<ExerciseGroup> {
    // Wchodzi zwykle posortowana lista (ORDER BY position), ale sortujemy też
    // tutaj: kolejność na ekranie ma zależeć od pozycji, a nie od tego, kto
    // wywołał funkcję.
    val ordered = sortedBy { exercise -> exercise.position }
    val primaries = ordered.filter { exercise -> !exercise.isAlternative }
    val alternatives = ordered.filter { exercise -> exercise.isAlternative }
    val used = mutableSetOf<Long>()

    val groups = primaries.map { primary ->
        val attached = alternatives
            .filter { alternative -> alternative.alternativeGroup == primary.alternativeGroup }
            .onEach { alternative -> used += alternative.id }
        ExerciseGroup(primary = primary, alternatives = attached)
    }
    // Alternatywa bez swojego ćwiczenia (stan nie powinien się zdarzyć) i tak
    // trafia na listę - lepiej pokazać ją osobno niż zgubić.
    val orphans = alternatives
        .filter { alternative -> alternative.id !in used }
        .map { orphan -> ExerciseGroup(primary = orphan, alternatives = emptyList()) }

    return groups + orphans
}
