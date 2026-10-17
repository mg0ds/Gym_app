package com.gymtracker.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.data.prefs.SettingsDataStore
import com.gymtracker.domain.model.RestInterval
import com.gymtracker.domain.model.SessionExercise
import com.gymtracker.domain.model.SessionStatus
import com.gymtracker.domain.model.SetLogDraft
import com.gymtracker.domain.model.WorkoutSession
import com.gymtracker.domain.repository.RestRepository
import com.gymtracker.domain.repository.SessionRepository
import com.gymtracker.domain.stats.RestStats
import com.gymtracker.domain.timer.ActiveRest
import com.gymtracker.domain.timer.TrackedWorkout
import com.gymtracker.timer.RestTimer
import com.gymtracker.timer.WorkoutTimer
import com.gymtracker.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Trwający (albo zakończony) trening razem z ćwiczeniami, seriami i zegarem. */
data class SessionUiState(
    val isLoading: Boolean = true,
    val session: WorkoutSession? = null,
    val exercises: List<SessionExercise> = emptyList(),
    /** Zegar pilnowany w tle - `null`, gdy trening już się nie liczy. */
    val tracked: TrackedWorkout? = null,
    /** Trwająca przerwa między seriami - `null`, gdy nic nie odlicza. */
    val rest: ActiveRest? = null,
    /** Przerwy tego treningu (także pominięte) - „czas pod sztangą” i podsumowanie. */
    val rests: List<RestInterval> = emptyList(),
    /** Historia przerw per `exerciseId` - „średnia przerwa: 1:45 (12 treningów)”. */
    val restStats: Map<Long, RestStats> = emptyMap(),
    /** Domyślna przerwa z ustawień - ostatni fallback celu przerwy. */
    val defaultRestSeconds: Int = SettingsDataStore.DEFAULT_REST_SECONDS,
) {
    val isActive: Boolean get() = session?.status == SessionStatus.ACTIVE

    /** Ile przerwy w tym treningu - do „pod sztangą”. Trwająca liczy się do teraz. */
    fun restTotalMs(nowEpochMs: Long): Long =
        rests.sumOf { interval -> interval.elapsedMs(nowEpochMs) }
}

/**
 * Ekran treningu: zegar (pauza/wznowienie), przerwy, notatka i logowanie serii.
 *
 * Zegar nie jest stanem tego ekranu - trening trwa dalej po wyjściu z niego,
 * a nawet po zabiciu aplikacji, więc czas trzyma [WorkoutTimer] na podstawie
 * sesji w bazie. Ekran tylko go pokazuje i przekazuje decyzje użytkownika.
 *
 * To samo dotyczy przerwy ([RestTimer]): ekran ją pokazuje i pozwala korygować,
 * ale odliczanie przeżywa wyjście z ekranu, bo siedzi w bazie i w budziku.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessions: SessionRepository,
    private val rests: RestRepository,
    private val timer: WorkoutTimer,
    private val restTimer: RestTimer,
    private val settings: SettingsDataStore,
) : ViewModel() {

    private val sessionId: Long = checkNotNull(savedStateHandle[Routes.ARG_SESSION_ID]) {
        "brak identyfikatora sesji w trasie"
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val restStats: Flow<Map<Long, RestStats>> = sessions.observeSession(sessionId)
        .map { details -> details?.exercises?.map { exercise -> exercise.exerciseId }.orEmpty() }
        .distinctUntilChanged()
        .flatMapLatest { exerciseIds -> rests.observeStats(exerciseIds) }

    val uiState: StateFlow<SessionUiState> = combine(
        sessions.observeSession(sessionId),
        timer.tracked,
        restTimer.active,
        rests.observeSessionRests(sessionId),
        settings.defaultRestSeconds,
    ) { details, tracked, activeRest, sessionRests, defaultRest ->
        SessionUiState(
            isLoading = false,
            session = details?.session,
            exercises = details?.exercises.orEmpty(),
            tracked = tracked?.takeIf { it.sessionId == sessionId },
            rest = activeRest?.takeIf { it.sessionId == sessionId },
            rests = sessionRests,
            defaultRestSeconds = defaultRest,
        )
    }.combine(restStats) { state, stats ->
        state.copy(restStats = stats)
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

    /**
     * Dopisuje serię i od razu startuje przerwę.
     *
     * Cel przerwy bierzemy z sesji (plan → karta ćwiczenia → ustawienie
     * globalne). Przerwę uruchamiamy **po** zapisie serii: nie ma sensu
     * odliczać czasu, którego użytkownik nie zapisał.
     */
    fun addSet(sessionExerciseId: Long, draft: SetLogDraft) {
        viewModelScope.launch {
            restTimer.clearAlert()
            val setLogId = sessions.addSet(sessionExerciseId, draft) ?: return@launch
            val state = uiState.value
            val exercise = state.exercises.firstOrNull { it.id == sessionExerciseId } ?: return@launch
            restTimer.startAfterSet(
                sessionId = sessionId,
                exercise = exercise,
                setLogId = setLogId,
                targetSeconds = exercise.restTargetSeconds(state.defaultRestSeconds),
            )
        }
    }

    fun updateSet(setLogId: Long, draft: SetLogDraft) {
        viewModelScope.launch { sessions.updateSet(setLogId, draft) }
    }

    fun deleteSet(setLogId: Long) {
        viewModelScope.launch { sessions.deleteSet(setLogId) }
    }

    /** Korekta celu przerwy dla tego jednego ćwiczenia (na dzisiejszy trening). */
    fun setExerciseRest(sessionExerciseId: Long, restSeconds: Int?) {
        viewModelScope.launch { sessions.setExerciseRest(sessionExerciseId, restSeconds) }
    }

    fun extendRest() {
        viewModelScope.launch { restTimer.extend() }
    }

    fun shrinkRest() {
        viewModelScope.launch { restTimer.shrink() }
    }

    fun skipRest() {
        viewModelScope.launch { restTimer.skip() }
    }

    /**
     * Licznik pokazał zero - domykamy przerwę od razu, zamiast czekać na alarm
     * (który przy braku zgody na dokładne alarmy może się spóźnić).
     */
    fun restDue() {
        viewModelScope.launch { restTimer.completeIfDue() }
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
