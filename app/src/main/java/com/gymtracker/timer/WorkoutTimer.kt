package com.gymtracker.timer

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.gymtracker.domain.model.SessionStatus
import com.gymtracker.domain.model.WorkoutSession
import com.gymtracker.domain.repository.SessionRepository
import com.gymtracker.domain.time.TimeProvider
import com.gymtracker.domain.timer.TrackedWorkout
import com.gymtracker.domain.timer.WorkoutTimerState
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Zegar trwającego treningu: jedno źródło prawdy dla ekranu i usługi w tle.
 *
 * Zasada: **stan żyje w bazie, a timer jest tylko jego bieżącym odbiciem**.
 * Dlatego `started_at`, suma pauz i otwarta pauza trafiają do `sessions`
 * natychmiast po zmianie - dzięki temu zabicie aplikacji (albo zgaszenie
 * ekranu i odcięcie usługi przez system) nie gubi treningu: po powrocie
 * wystarczy odczytać sesję i zegar wstaje tam, gdzie był.
 *
 * Klasa jest `@Singleton`, bo w jednym procesie ma istnieć dokładnie jeden
 * zegar: ekran [com.gymtracker.ui.session.SessionScreen], usługa
 * [WorkoutTimerService] i obserwator w [com.gymtracker.MainActivity] patrzą na
 * ten sam [tracked].
 */
@Singleton
class WorkoutTimer(
    private val context: Context,
    private val sessions: SessionRepository,
    private val timeProvider: TimeProvider,
    private val restTimer: RestTimer,
) {

    private val _tracked = MutableStateFlow<TrackedWorkout?>(null)

    /** Pilnowany trening (albo `null`, gdy nic nie trwa). */
    val tracked: StateFlow<TrackedWorkout?> = _tracked.asStateFlow()

    /**
     * Podpina zegar pod aktywną sesję i pilnuje tego w tle.
     *
     * Wołane raz, przy starcie ekranu głównego: nowy trening, trening
     * odtworzony po restarcie aplikacji i powrót z tła obsługuje ten sam
     * strumień - nie trzeba pamiętać o „włączeniu timera” w miejscu startu
     * treningu ani martwić się, że któreś z nich o tym zapomni.
     */
    fun attach(scope: CoroutineScope) {
        scope.launch {
            sessions.observeActiveSessionHead().collect { session ->
                if (session == null) {
                    // Sesja zniknęła (skończona, przerwana) - przerwa nie ma
                    // czego dotyczyć, a jej otwarty wiersz nie może zostać.
                    restTimer.cancel()
                    stop()
                } else {
                    track(session)
                    // Ta sama sesja = jedyne miejsce, które pilnuje treningu,
                    // więc też jedyne, które ma odtworzyć przerwę po restarcie.
                    restTimer.restoreIfNeeded()
                }
            }
        }
    }

    /**
     * Ustawia zegar na [session].
     *
     * Gdy to ta sama sesja, którą już pilnujemy, zmieniamy tylko nazwę -
     * inaczej przepisalibyśmy pauzę trzymaną w pamięci stanem z bazy (a ten
     * jest o tyle starszy, o ile trwa pauza).
     */
    suspend fun track(session: WorkoutSession) {
        val current = _tracked.value
        if (current?.sessionId == session.id) {
            if (current.name != session.name) _tracked.value = current.copy(name = session.name)
            return
        }
        if (session.status != SessionStatus.ACTIVE) {
            stop()
            return
        }

        _tracked.value = TrackedWorkout(
            sessionId = session.id,
            name = session.name,
            timer = WorkoutTimerState(
                startedAtEpochMs = session.startedAtEpochMs,
                pausedTotalMs = session.pausedTotalMs,
                pausedAtEpochMs = session.pausedAtEpochMs,
            ),
        )
        startService()
    }

    /** Odtwarza zegar z bazy - po starcie usługi „z zimna” (proces wstał od nowa). */
    suspend fun restoreIfNeeded() {
        if (_tracked.value != null) return
        sessions.getActiveSession()?.let { session -> track(session) }
    }

    /** Wstrzymuje zegar i zapisuje pauzę od razu (patrz opis klasy). */
    suspend fun pause() {
        val current = _tracked.value ?: return
        // Trening w pauzie nie ma przerwy między seriami - przerwa jest częścią
        // pracy, a nie przerwy od pracy.
        restTimer.cancel()
        val paused = current.copy(timer = current.timer.paused(timeProvider.nowEpochMs()))
        _tracked.value = paused
        sessions.persistTimer(paused.sessionId, paused.timer)
    }

    /** Wznawia zegar, doliczając pauzę do sumy. */
    suspend fun resume() {
        val current = _tracked.value ?: return
        val resumed = current.copy(timer = current.timer.resumed(timeProvider.nowEpochMs()))
        _tracked.value = resumed
        sessions.persistTimer(resumed.sessionId, resumed.timer)
    }

    /** Kończy trening (jak „Zakończ trening” w aplikacji) i gasi usługę. */
    suspend fun finish() {
        val sessionId = _tracked.value?.sessionId ?: return
        restTimer.cancel()
        sessions.finishSession(sessionId)
        stop()
    }

    /**
     * Ponawia notyfikację treningu.
     *
     * Potrzebne tylko w jednym momencie: gdy trening już trwa, ale użytkownik
     * dopiero co zgodził się na powiadomienia. Wcześniej usługa nie mogła
     * notyfikacji pokazać, a bez zmiany stanu nie miałaby powodu jej odświeżyć.
     */
    fun refreshNotification() {
        if (_tracked.value == null) return
        ContextCompat.startForegroundService(
            context,
            Intent(context, WorkoutTimerService::class.java)
                .setAction(WorkoutTimerService.ACTION_REFRESH),
        )
    }

    /** Zatrzymuje pilnowanie i gasi usługę. Sesji w bazie nie rusza. */
    fun stop() {
        _tracked.value = null
        context.stopService(Intent(context, WorkoutTimerService::class.java))
    }

    private fun startService() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, WorkoutTimerService::class.java),
        )
    }
}
