package com.gymtracker.ui.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.domain.model.Exercise
import com.gymtracker.domain.repository.ExerciseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Stan listy ćwiczeń: tekst szukania, aktywne filtry i gotowa lista. */
data class ExerciseListUiState(
    val query: String = "",
    val muscleGroup: String? = null,
    val favouritesOnly: Boolean = false,
    val muscleGroups: List<String> = emptyList(),
    val exercises: List<Exercise> = emptyList(),
    /** Ile ćwiczeń czeka w archiwum - etykieta wejścia do archiwum (M9). */
    val archivedCount: Int = 0,
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExerciseListViewModel @Inject constructor(
    private val repository: ExerciseRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val muscleGroup = MutableStateFlow<String?>(null)
    private val favouritesOnly = MutableStateFlow(false)

    private data class Filters(
        val query: String,
        val muscleGroup: String?,
        val favouritesOnly: Boolean,
    )

    private val filters = combine(query, muscleGroup, favouritesOnly) { query, group, favourites ->
        Filters(query = query, muscleGroup = group, favouritesOnly = favourites)
    }

    val uiState: StateFlow<ExerciseListUiState> = combine(
        filters.flatMapLatest { current ->
            repository.observeExercises(
                query = current.query,
                muscleGroup = current.muscleGroup,
                favouritesOnly = current.favouritesOnly,
            )
        },
        filters,
        repository.observeMuscleGroups(),
        repository.observeArchivedCount(),
    ) { exercises, current, groups, archivedCount ->
        ExerciseListUiState(
            query = current.query,
            muscleGroup = current.muscleGroup,
            favouritesOnly = current.favouritesOnly,
            muscleGroups = groups,
            exercises = exercises,
            archivedCount = archivedCount,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = ExerciseListUiState(),
    )

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onMuscleGroupSelected(value: String?) {
        muscleGroup.value = if (muscleGroup.value == value) null else value
    }

    fun onFavouritesToggle() {
        favouritesOnly.value = !favouritesOnly.value
    }

    fun toggleFavourite(exercise: Exercise) {
        viewModelScope.launch {
            repository.setFavourite(exercise.id, isFavourite = !exercise.isFavourite)
        }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
