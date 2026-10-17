package com.gymtracker.ui.exercises

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.domain.model.Exercise
import com.gymtracker.domain.model.ExerciseMedia
import com.gymtracker.domain.model.ExerciseUsage
import com.gymtracker.domain.repository.ExerciseRepository
import com.gymtracker.domain.repository.LinkMediaResult
import com.gymtracker.domain.repository.MediaRejection
import com.gymtracker.domain.repository.MediaRepository
import com.gymtracker.domain.repository.StatsRepository
import com.gymtracker.domain.stats.ExerciseStats
import com.gymtracker.domain.stats.StatsRange
import com.gymtracker.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Stan ekranu szczegółów: ćwiczenie albo informacja, że już go nie ma. */
data class ExerciseDetailUiState(
    val isLoading: Boolean = true,
    val exercise: Exercise? = null,
    /** Dlaczego nie udało się podpiąć pliku z galerii (`null` = nie było próby). */
    val mediaRejection: MediaRejection? = null,
)

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ExerciseRepository,
    private val statsRepository: StatsRepository,
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private val exerciseId: Long = checkNotNull(savedStateHandle[Routes.ARG_EXERCISE_ID])

    /**
     * Odmowę podpięcia pliku trzymamy obok ćwiczenia, a nie w środku: ćwiczenie
     * przychodzi z bazy i każda jego zmiana tworzy nowy stan, więc komunikat
     * musiałby być przepisywany za każdym razem. Osobny strumień mówi wprost,
     * że to informacja „na chwilę”, a nie część danych ćwiczenia.
     */
    private val mediaRejection = MutableStateFlow<MediaRejection?>(null)

    val uiState: StateFlow<ExerciseDetailUiState> = combine(
        repository.observeExercise(exerciseId),
        mediaRejection,
    ) { exercise, rejection ->
        ExerciseDetailUiState(isLoading = false, exercise = exercise, mediaRejection = rejection)
    }.stateIn(
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

    /** Zdjęcia i filmy z galerii, w kolejności dodania. */
    val media: StateFlow<List<ExerciseMedia>> = mediaRepository.observeMedia(exerciseId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = emptyList(),
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

    /**
     * Podpina plik wybrany w galerii. Odmowy nie pokazujemy jako błędu aplikacji -
     * to zwykła informacja („ten plik jest nieczytelny / to nie zdjęcie”).
     */
    fun linkMedia(uri: String) {
        viewModelScope.launch {
            val result = mediaRepository.link(exerciseId, uri)
            if (result is LinkMediaResult.Rejected) {
                mediaRejection.value = result.reason
            }
        }
    }

    fun dismissMediaRejection() {
        mediaRejection.value = null
    }

    fun deleteMedia(mediaId: Long) {
        viewModelScope.launch { mediaRepository.delete(mediaId) }
    }

    /** Plik własnej miniatury do wczytania w siatce; `null` = pokazujemy ikonę. */
    fun thumbnailFile(media: ExerciseMedia): File? = mediaRepository.thumbnailFile(media)

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
