package com.gymtracker.ui.exercises

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.domain.model.Exercise
import com.gymtracker.domain.model.ExerciseUsage
import com.gymtracker.domain.repository.ExerciseRepository
import com.gymtracker.domain.repository.StatsRepository
import com.gymtracker.domain.stats.ExerciseStats
import com.gymtracker.domain.stats.StatsRange
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
    private val statsRepository: StatsRepository,
) : ViewModel() {

    private val exerciseId: Long = checkNotNull(savedStateHandle[Routes.ARG_EXERCISE_ID])

    val uiState: StateFlow<ExerciseDetailUiState> = repository.observeExercise(exerciseId)
        .map { exercise -> ExerciseDetailUiState(isLoading = false, exercise = exercise) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = ExerciseDetailUiState(),
        )

    /**
     * Gdzie ćwiczenie jest używane. Osobny strumień, a nie część [uiState], bo
     * potrzebne tylko przy usuwaniu - a liczymy je zapytaniem do dwóch tabel,
     * więc nie ma sensu przeliczać go przy każdym odświeżeniu katalogu.
     */
    val usage: StateFlow<ExerciseUsage> = repository.observeUsage(exerciseId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = ExerciseUsage(),
        )

    /**
     * Skrót historii: rekord, ostatnie wykonanie i średnia przerwa z ostatnich
     * trzech miesięcy. Pełne wykresy i tabela mają osobny ekran - tutaj ma się
     * zmieścić to, co odpowiada na pytanie „co ostatnio robiłem z tym ciężarem”.
     */
    val stats: StateFlow<ExerciseStats> = statsRepository
        .observeExerciseStats(exerciseId, StatsRange.MONTH_3)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = ExerciseStats(),
        )

    fun toggleFavourite() {
        val exercise = uiState.value.exercise ?: return
        viewModelScope.launch {
            repository.setFavourite(exercise.id, isFavourite = !exercise.isFavourite)
        }
    }

    /** Usuwa ćwiczenie z katalogu i oddaje sterowanie (ekran wraca do listy). */
    fun archive(onArchived: () -> Unit) {
        viewModelScope.launch {
            repository.archiveExercise(exerciseId)
            onArchived()
        }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
