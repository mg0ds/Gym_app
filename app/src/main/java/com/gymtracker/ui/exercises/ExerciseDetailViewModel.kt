package com.gymtracker.ui.exercises

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.domain.model.Exercise
import com.gymtracker.domain.repository.ExerciseRepository
import com.gymtracker.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Stan ekranu szczegółów: ćwiczenie albo informacja, że już go nie ma. */
data class ExerciseDetailUiState(
    val isLoading: Boolean = true,
    val exercise: Exercise? = null,
)

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ExerciseRepository,
) : ViewModel() {

    private val exerciseId: Long = checkNotNull(savedStateHandle[Routes.ARG_EXERCISE_ID])

    val uiState: StateFlow<ExerciseDetailUiState> = repository.observeExercise(exerciseId)
        .map { exercise -> ExerciseDetailUiState(isLoading = false, exercise = exercise) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = ExerciseDetailUiState(),
        )

    fun toggleFavourite() {
        val exercise = uiState.value.exercise ?: return
        viewModelScope.launch {
            repository.setFavourite(exercise.id, isFavourite = !exercise.isFavourite)
        }
    }

    /** Usuwa ćwiczenie i oddaje sterowanie (ekran wraca do listy). */
    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteExercise(exerciseId)
            onDeleted()
        }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
