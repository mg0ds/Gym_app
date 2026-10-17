package com.gymtracker.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.domain.repository.ExerciseRepository
import com.gymtracker.domain.repository.StatsRepository
import com.gymtracker.domain.stats.SessionSummary
import com.gymtracker.domain.stats.SessionSummaryCalculator
import com.gymtracker.domain.stats.StatsRange
import com.gymtracker.domain.time.TimeProvider
import com.gymtracker.domain.time.today
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Ćwiczenie, które ma coś w historii - do filtra listy. */
data class HistoryExerciseOption(val id: Long, val name: String)

data class HistoryUiState(
    val isLoading: Boolean = true,
    val range: StatsRange = StatsRange.MONTH_3,
    val exerciseId: Long? = null,
    val name: String? = null,
    /** Nazwy treningów obecne w historii („który dzień planu”). */
    val names: List<String> = emptyList(),
    /** Ćwiczenia obecne w historii - pełny katalog tu nie pomaga. */
    val exercises: List<HistoryExerciseOption> = emptyList(),
    val sessions: List<SessionSummary> = emptyList(),
) {
    val isEmpty: Boolean get() = sessions.isEmpty()

    val hasFilter: Boolean get() = exerciseId != null || name != null
}

/**
 * Historia treningów: lista zakończonych sesji z filtrami.
 *
 * Filtrowanie robimy w pamięci (`SessionSummaryCalculator.filter`), a nie
 * zapytaniem: lista jest krótka, a dzięki temu zmiana filtra jest natychmiastowa
 * i nie odświeża bazy. Wykresy i rekordy mają osobny ekran - tutaj chodzi tylko
 * o „co robiłem i kiedy”.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    repository: StatsRepository,
    exercises: ExerciseRepository,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val range = MutableStateFlow(StatsRange.MONTH_3)
    private val exerciseFilter = MutableStateFlow<Long?>(null)
    private val nameFilter = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HistoryUiState> = combine(
        repository.observeSessionSummaries(),
        exercises.observeExercises(),
        range,
        exerciseFilter,
        nameFilter,
    ) { summaries, catalog, range, exerciseId, name ->
        val presentExercises = summaries
            .flatMap { summary -> summary.exerciseIds }
            .toSet()
        HistoryUiState(
            isLoading = false,
            range = range,
            exerciseId = exerciseId,
            name = name,
            names = summaries.map { summary -> summary.name }.distinct().sorted(),
            exercises = catalog
                .filter { exercise -> exercise.id in presentExercises }
                .map { exercise -> HistoryExerciseOption(exercise.id, exercise.name) }
                .sortedBy { option -> option.name },
            sessions = SessionSummaryCalculator.filter(
                summaries = summaries,
                range = range,
                today = timeProvider.today(),
                exerciseId = exerciseId,
                name = name,
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = HistoryUiState(),
    )

    fun onRangeSelected(value: StatsRange) {
        range.value = value
    }

    fun onExerciseSelected(exerciseId: Long?) {
        exerciseFilter.value = exerciseId
    }

    fun onNameSelected(name: String?) {
        nameFilter.value = name
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
