package com.gymtracker.ui.plans

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.domain.model.Exercise
import com.gymtracker.domain.model.PlanExerciseDraft
import com.gymtracker.domain.model.RepsUnit
import com.gymtracker.domain.repository.ExerciseRepository
import com.gymtracker.domain.repository.PlanRepository
import com.gymtracker.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Wybór ćwiczenia z katalogu do dnia planu (albo jako alternatywy). */
data class ExercisePickerUiState(
    val query: String = "",
    val muscleGroup: String? = null,
    val favouritesOnly: Boolean = false,
    val muscleGroups: List<String> = emptyList(),
    val exercises: List<Exercise> = emptyList(),
    val isLoading: Boolean = true,
    /** Czy wybieramy alternatywę do istniejącej pozycji, czy zwykłe ćwiczenie. */
    val isAlternative: Boolean = false,
)

/**
 * Ekran wyboru ćwiczenia. Dodaje je od razu do dnia planu (albo jako alternatywę
 * wskazanej pozycji) i wraca - dzięki temu nie trzeba przekazywać wyniku z
 * powrotem przez nawigację.
 */
@HiltViewModel
class ExercisePickerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val exercises: ExerciseRepository,
    private val plans: PlanRepository,
) : ViewModel() {

    private val dayId: Long = checkNotNull(savedStateHandle[Routes.ARG_DAY_ID]) {
        "brak identyfikatora dnia w trasie"
    }

    /** `null` = zwykłe ćwiczenie; id = alternatywa do tej pozycji. */
    private val anchorPlanExerciseId: Long? =
        savedStateHandle.get<Long>(Routes.ARG_ANCHOR_ID)?.takeIf { id -> id > 0L }

    private val filters = MutableStateFlow(ExerciseFilters())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ExercisePickerUiState> = combine(
        filters,
        exercises.observeMuscleGroups(),
    ) { current, groups ->
        current to groups
    }.flatMapLatest { (current, groups) ->
        exercises.observeExercises(
            query = current.query,
            muscleGroup = current.muscleGroup,
            favouritesOnly = current.favouritesOnly,
        ).map { found ->
            ExercisePickerUiState(
                query = current.query,
                muscleGroup = current.muscleGroup,
                favouritesOnly = current.favouritesOnly,
                muscleGroups = groups,
                exercises = found,
                isLoading = false,
                isAlternative = anchorPlanExerciseId != null,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = ExercisePickerUiState(isAlternative = anchorPlanExerciseId != null),
    )

    fun onQueryChange(query: String) {
        filters.update { current -> current.copy(query = query) }
    }

    fun onMuscleGroupSelected(muscleGroup: String?) {
        filters.update { current -> current.copy(muscleGroup = muscleGroup) }
    }

    fun onFavouritesToggle() {
        filters.update { current -> current.copy(favouritesOnly = !current.favouritesOnly) }
    }

    /**
     * Dodaje wybrane ćwiczenie z domyślnym celem z katalogu (jeśli go ma) i wraca.
     */
    fun pick(exercise: Exercise, onDone: () -> Unit) {
        val draft = PlanExerciseDraft(
            exerciseId = exercise.id,
            sets = exercise.defaultSets ?: DEFAULT_SETS,
            repsMin = exercise.defaultRepsMin,
            repsMax = exercise.defaultRepsMax,
            repsUnit = RepsUnit.REPS,
            restSeconds = exercise.defaultRestS,
        )
        viewModelScope.launch {
            if (anchorPlanExerciseId != null) {
                plans.addAlternative(anchorPlanExerciseId, draft)
            } else {
                plans.addExercise(dayId, draft)
            }
            onDone()
        }
    }

    private data class ExerciseFilters(
        val query: String = "",
        val muscleGroup: String? = null,
        val favouritesOnly: Boolean = false,
    )

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

        /** Gdy katalog nie zna domyślnej liczby serii, proponujemy 3. */
        const val DEFAULT_SETS = 3
    }
}
