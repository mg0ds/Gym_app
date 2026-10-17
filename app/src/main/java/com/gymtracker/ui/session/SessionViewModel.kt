package com.gymtracker.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.domain.model.SessionExercise
import com.gymtracker.domain.model.SessionStatus
import com.gymtracker.domain.model.SetLogDraft
import com.gymtracker.domain.model.WorkoutSession
import com.gymtracker.domain.repository.SessionRepository
import com.gymtracker.domain.timer.TrackedWorkout
import com.gymtracker.timer.WorkoutTimer
import com.gymtracker.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Trwający (albo zakończony) trening razem z ćwiczeniami, seriami i zegarem. */
data class SessionUiState(
    val isLoading: Boolean = true,
    val session: WorkoutSession? = null,
    val exercises: List<SessionExercise> = emptyList(),
    /** Zegar pilnowany w tle - `null`, gdy trening już się nie liczy. */
    val tracked: TrackedWorkout? = null,
) {
    val isActive: Boolean get() = session?.status == SessionStatus.ACTIVE
}

/**
 * Ekran treningu: zegar (pauza/wznowienie), notatka i logowanie serii.
 *
 * Zegar nie jest stanem tego ekranu - trening trwa dalej po wyjściu z niego,
 * a nawet po zabiciu aplikacji, więc czas trzyma [WorkoutTimer] na podstawie
 * sesji w bazie. Ekran tylko go pokazuje i przekazuje decyzje użytkownika.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessions: SessionRepository,
    private val timer: WorkoutTimer,
) : ViewModel() {

    private val sessionId: Long = checkNotNull(savedStateHandle[Routes.ARG_SESSION_ID]) {
        "brak identyfikatora sesji w trasie"
    }

    val uiState: StateFlow<SessionUiState> = combine(
        sessions.observeSession(sessionId),
        timer.tracked,
    ) { details, tracked ->
        SessionUiState(
            isLoading = false,
            session = details?.session,
            exercises = details?.exercises.orEmpty(),
            tracked = tracked?.takeIf { it.sessionId == sessionId },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = SessionUiState(),
    )

    fun pause() {
        viewModelScope.launch { timer.pause() }
    }

    fun resume() {
        viewModelScope.launch { timer.resume() }
    }

    fun saveNote(note: String) {
        viewModelScope.launch { sessions.setNote(sessionId, note) }
    }

    fun addSet(sessionExerciseId: Long, draft: SetLogDraft) {
        viewModelScope.launch { sessions.addSet(sessionExerciseId, draft) }
    }

    fun updateSet(setLogId: Long, draft: SetLogDraft) {
        viewModelScope.launch { sessions.updateSet(setLogId, draft) }
    }

    fun deleteSet(setLogId: Long) {
        viewModelScope.launch { sessions.deleteSet(setLogId) }
    }

    fun finishSession(onFinished: () -> Unit) {
        viewModelScope.launch {
            sessions.finishSession(sessionId)
            onFinished()
        }
    }

    fun cancelSession(onFinished: () -> Unit) {
        viewModelScope.launch {
            sessions.cancelSession(sessionId)
            onFinished()
        }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
