package com.gymtracker.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.domain.model.ExerciseTracking
import com.gymtracker.domain.repository.ExerciseRepository
import com.gymtracker.domain.repository.StatsRepository
import com.gymtracker.domain.stats.ExerciseStats
import com.gymtracker.domain.stats.StatsRange
import com.gymtracker.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class ExerciseStatsUiState(
    val isLoading: Boolean = true,
    val exerciseName: String? = null,
    /** Co ćwiczenie zapisuje w serii - decyduje o kolumnach tabeli wykonań. */
    val tracking: ExerciseTracking = ExerciseTracking.WEIGHT_REPS,
    val range: StatsRange = StatsRange.MONTH_3,
    val stats: ExerciseStats = ExerciseStats(),
)

/**
 * Statystyki jednego ćwiczenia.
 *
 * Zakres przeliczamy w bazie (`flatMapLatest` na strumieniu zakresu), a nie
 * w pamięci: surowe serie to kilka tysięcy wierszy, więc lepiej przeliczyć je
 * raz na wybór niż trzymać w pamięci wszystkie i filtrować przy każdym
 * odświeżeniu. `StatsRepository` i tak ogranicza zapytanie limitem.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExerciseStatsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: StatsRepository,
    exercises: ExerciseRepository,
) : ViewModel() {

    private val exerciseId: Long = checkNotNull(savedStateHandle[Routes.ARG_EXERCISE_ID])

    private val range = MutableStateFlow(StatsRange.MONTH_3)

    val uiState: StateFlow<ExerciseStatsUiState> = combine(
        exercises.observeExercise(exerciseId),
        range.flatMapLatest { value -> repository.observeExerciseStats(exerciseId, value) },
        range,
    ) { exercise, stats, value ->
        ExerciseStatsUiState(
            isLoading = false,
            exerciseName = exercise?.name,
            tracking = exercise?.tracking ?: ExerciseTracking.WEIGHT_REPS,
            range = value,
            stats = stats,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = ExerciseStatsUiState(),
    )

    fun onRangeSelected(value: StatsRange) {
        range.value = value
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
