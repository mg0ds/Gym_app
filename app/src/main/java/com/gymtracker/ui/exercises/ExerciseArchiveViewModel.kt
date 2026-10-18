package com.gymtracker.ui.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.domain.model.ArchiveImpact
import com.gymtracker.domain.model.Exercise
import com.gymtracker.domain.repository.ExerciseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Co się właśnie stało w archiwum. Trzymamy tu **nazwę i liczbę**, a nie gotowe
 * zdanie: teksty żyją w `strings.xml`, a stan z zapisanym zdaniem zostałby
 * w jednym języku na zawsze (§4.2 pkt 49).
 */
sealed interface ArchiveStatus {
    data class Restored(val name: String) : ArchiveStatus

    data class Deleted(val name: String) : ArchiveStatus

    data class Emptied(val count: Int) : ArchiveStatus
}

/** Stan ekranu archiwum: lista, cena opróżnienia i otwarte pytania. */
data class ExerciseArchiveUiState(
    val exercises: List<Exercise> = emptyList(),
    val impact: ArchiveImpact = ArchiveImpact(),
    /** Ćwiczenie, o którego trwałe skasowanie pyta dialog. */
    val pendingDelete: Exercise? = null,
    /** Czy pokazujemy pytanie „opróżnić archiwum?”. */
    val pendingEmpty: Boolean = false,
    val status: ArchiveStatus? = null,
    val isLoading: Boolean = true,
)

/**
 * Archiwum ćwiczeń (M9).
 *
 * Ekran jest mały, ale wszystko w nim jest nieodwracalne, więc stan trzyma dwie
 * rzeczy naraz: listę do pokazania i **cenę** opróżnienia archiwum. Bez tej
 * drugiej użytkownik kasowałby „puste” ćwiczenia i tracił razem z nimi treningi.
 */
@HiltViewModel
class ExerciseArchiveViewModel @Inject constructor(
    private val repository: ExerciseRepository,
) : ViewModel() {

    private val pendingDelete = MutableStateFlow<Exercise?>(null)
    private val pendingEmpty = MutableStateFlow(false)
    private val status = MutableStateFlow<ArchiveStatus?>(null)

    val uiState: StateFlow<ExerciseArchiveUiState> = combine(
        repository.observeArchivedExercises(),
        repository.observeArchiveImpact(),
        pendingDelete,
        pendingEmpty,
        status,
    ) { exercises, impact, delete, empty, status ->
        ExerciseArchiveUiState(
            exercises = exercises,
            impact = impact,
            pendingDelete = delete,
            pendingEmpty = empty,
            status = status,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = ExerciseArchiveUiState(),
    )

    fun onRestore(exercise: Exercise) {
        viewModelScope.launch {
            repository.restoreExercise(exercise.id)
            status.value = ArchiveStatus.Restored(exercise.name)
        }
    }

    fun onDeleteRequested(exercise: Exercise) {
        pendingDelete.value = exercise
    }

    fun onDeleteCancelled() {
        pendingDelete.value = null
    }

    fun onDeleteConfirmed() {
        val exercise = pendingDelete.value ?: return
        pendingDelete.value = null
        viewModelScope.launch {
            repository.deleteExercisePermanently(exercise.id)
            status.value = ArchiveStatus.Deleted(exercise.name)
        }
    }

    fun onEmptyRequested() {
        pendingEmpty.value = true
    }

    fun onEmptyCancelled() {
        pendingEmpty.value = false
    }

    fun onEmptyConfirmed() {
        pendingEmpty.value = false
        viewModelScope.launch {
            // Liczbę bierzemy z wyniku kasowania, a nie z listy: lista to stan
            // sprzed chwili, a komunikat ma mówić, ile naprawdę zniknęło.
            status.value = ArchiveStatus.Emptied(repository.emptyArchive())
        }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
