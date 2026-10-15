package com.gymtracker.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.domain.model.SessionExercise
import com.gymtracker.domain.model.SessionStatus
import com.gymtracker.domain.model.WorkoutSession
import com.gymtracker.domain.repository.SessionRepository
import com.gymtracker.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Trwający (albo zakończony) trening razem z listą zaplanowanych ćwiczeń. */
data class SessionUiState(
    val isLoading: Boolean = true,
    val session: WorkoutSession? = null,
    val exercises: List<SessionExercise> = emptyList(),
) {
    val isActive: Boolean get() = session?.status == SessionStatus.ACTIVE
}

/**
 * Ekran treningu w M2: lista ćwiczeń z planu i czas trwania. Logowanie serii
 * i timer w tle dochodzą w M4 - tutaj liczy się to, że trening **został
 * rozpoczęty** i da się go zakończyć.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessions: SessionRepository,
) : ViewModel() {

    private val sessionId: Long = checkNotNull(savedStateHandle[Routes.ARG_SESSION_ID]) {
        "brak identyfikatora sesji w trasie"
    }

    val uiState: StateFlow<SessionUiState> = sessions.observeSession(sessionId)
        .map { details ->
            SessionUiState(
                isLoading = false,
                session = details?.session,
                exercises = details?.exercises.orEmpty(),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = SessionUiState(),
        )

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
