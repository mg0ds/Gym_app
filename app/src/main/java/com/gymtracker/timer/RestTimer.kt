package com.gymtracker.timer

import com.gymtracker.domain.model.RestEndReason
import com.gymtracker.domain.model.SessionExercise
import com.gymtracker.domain.repository.RestRepository
import com.gymtracker.domain.time.TimeProvider
import com.gymtracker.domain.timer.ActiveRest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Odliczanie przerwy między seriami: jedno źródło prawdy dla ekranu i usługi.
 *
 * Zasada jest ta sama co przy zegarze treningu (`WorkoutTimer`): **stan żyje
 * w bazie, a timer tylko go odbija**. Start przerwy to od razu wiersz
 * w `rest_intervals`, a koniec to domknięcie tego wiersza - jeśli aplikacja
 * zginie w połowie, po powrocie wystarczy odczytać otwarty wiersz
 * ([restoreIfNeeded]).
 *
 * Kto kończy przerwę? Każdy, kto pierwszy:
 * * alarm systemowy (ekran zgaszony, proces ubity) - [onAlarm],
 * * ekran, gdy widzi zero na liczniku - [completeIfDue],
 * * kolejna seria, „Pomiń” albo koniec treningu.
 *
 * Domknięcie jest idempotentne (pilnuje tego `RestDao.close`), więc wyścig
 * między nimi nie zrobi krzywdy: druga próba po prostu nic nie zmieni.
 */
@Singleton
class RestTimer @Inject constructor(
    private val rests: RestRepository,
    private val timeProvider: TimeProvider,
    private val alarms: RestAlarmScheduler,
    private val notifier: RestNotifier,
) {

    private val _active = MutableStateFlow<ActiveRest?>(null)

    /** Trwająca przerwa (albo `null`, gdy nic nie odlicza). */
    val active: StateFlow<ActiveRest?> = _active.asStateFlow()

    /**
     * Startuje przerwę po zalogowanej serii.
     *
     * Poprzednia przerwa (gdyby jakaś trwała) kończy się jako odbyta: skoro
     * użytkownik zalogował kolejną serię, to znaczy, że wrócił do pracy.
     */
    suspend fun startAfterSet(
        sessionId: Long?,
        exercise: SessionExercise,
        setLogId: Long,
        targetSeconds: Int,
    ) {
        closeActive(resumedWorkReason())
        val seconds = targetSeconds.coerceAtLeast(ActiveRest.MIN_PLANNED_SECONDS)
        val interval = rests.start(
            exerciseId = exercise.exerciseId,
            sessionId = sessionId,
            setLogId = setLogId,
            plannedSeconds = seconds,
        )
        val rest = ActiveRest(
            intervalId = interval.id,
            sessionId = sessionId,
            exerciseId = exercise.exerciseId,
            exerciseName = exercise.exerciseName,
            plannedSeconds = seconds,
            startedAtEpochMs = interval.startedAtEpochMs,
        )
        _active.value = rest
        alarms.schedule(rest.intervalId, rest.endsAtEpochMs)
    }

    /** „+15 s”: przesuwa cel (i koniec) oraz budzik. */
    suspend fun extend(seconds: Int = ActiveRest.STEP_SECONDS) {
        val current = _active.value ?: return
        val extended = current.extendedBy(seconds)
        if (extended == current) return
        _active.value = extended
        rests.setPlannedSeconds(extended.intervalId, extended.plannedSeconds)
        alarms.schedule(extended.intervalId, extended.endsAtEpochMs)
    }

    /** „-15 s”: skraca odliczanie, ale nie poniżej sensownego minimum. */
    suspend fun shrink(seconds: Int = ActiveRest.STEP_SECONDS) {
        val current = _active.value ?: return
        val shrunk = current.shrunkBy(seconds)
        if (shrunk == current) return
        _active.value = shrunk
        rests.setPlannedSeconds(shrunk.intervalId, shrunk.plannedSeconds)
        alarms.schedule(shrunk.intervalId, shrunk.endsAtEpochMs)
    }

    /** „Pomiń”: użytkownik uznał, że przerwa nie jest potrzebna. */
    suspend fun skip() = closeActive(RestEndReason.SKIPPED)

    /**
     * Koniec przerwy, gdy widzi go ekran (licznik pokazał zero).
     *
     * Bez tego czekalibyśmy na alarm nawet z otwartą aplikacją - a alarm bywa
     * niedokładny, gdy system odmówił „Alarmów i przypomnień”. Kończy przerwę
     * **z alarmem**, tak samo jak budzik: sensem przerwy jest usłyszeć jej koniec,
     * a nie zobaczyć znikający pasek. Podwójnego sygnału nie będzie, bo obie
     * drogi domykają ten sam wiersz i anulują ten sam budzik - wygra ta pierwsza.
     */
    suspend fun completeIfDue(nowEpochMs: Long = timeProvider.nowEpochMs()) {
        val current = _active.value ?: return
        if (!current.isOver(nowEpochMs)) return
        closeActive(resumedWorkReason(current), alert = true)
    }

    /**
     * Alarm systemowy: domyka przerwę i pokazuje powiadomienie.
     *
     * Wołane także wtedy, gdy proces wstał od zera (nie ma [active]) - dlatego
     * ćwiczenie bierzemy z bazy, a nie z pamięci.
     */
    suspend fun onAlarm(intervalId: Long) {
        val current = _active.value?.takeIf { rest -> rest.intervalId == intervalId }
        if (current != null) {
            _active.value = null
            alarms.cancel()
        }

        val interval = rests.get(intervalId) ?: return
        if (!interval.isRunning) return

        val planned = interval.plannedSeconds ?: 0
        val reason = if (current?.extended == true) {
            RestEndReason.EXTENDED
        } else {
            RestEndReason.COMPLETED
        }
        val closed = rests.close(intervalId, reason, planned)
        if (!closed) return

        val name = current?.exerciseName ?: rests.exerciseName(interval.exerciseId).orEmpty()
        notifier.showRestFinished(name)
    }

    /**
     * Przerwa przestaje mieć sens: pauza treningu, jego koniec albo przerwanie.
     *
     * Powód to `cancelled`, więc taka przerwa nie wejdzie do średniej - nie
     * była przerwą, tylko jej pozostałością.
     */
    suspend fun cancel() = closeActive(RestEndReason.CANCELLED)

    /** Ukrywa alarm końca przerwy (np. gdy użytkownik zalogował serię). */
    fun clearAlert() {
        notifier.clear()
    }

    /**
     * Odtwarza przerwę po restarcie procesu.
     *
     * Gdy przerwa już minęła (proces wstał po czasie, a alarm przepadł razem
     * z ubiciem aplikacji), domykamy ją jako odbytą - bez alarmowania
     * użytkownika, bo dzwonienie po fakcie jest gorsze niż milczenie.
     */
    suspend fun restoreIfNeeded() {
        if (_active.value != null) return
        val open = rests.getOpen() ?: return

        val planned = open.plannedSeconds ?: 0
        val now = timeProvider.nowEpochMs()
        val rest = ActiveRest(
            intervalId = open.id,
            sessionId = open.sessionId,
            exerciseId = open.exerciseId,
            exerciseName = rests.exerciseName(open.exerciseId).orEmpty(),
            plannedSeconds = planned,
            startedAtEpochMs = open.startedAtEpochMs,
        )
        if (planned < ActiveRest.MIN_PLANNED_SECONDS || rest.isOver(now)) {
            rests.close(open.id, RestEndReason.COMPLETED, planned)
            notifier.clear()
            return
        }

        _active.value = rest
        alarms.schedule(rest.intervalId, rest.endsAtEpochMs)
    }

    /**
     * Domyka pilnowaną przerwę jako skończoną (użytkownik wrócił do pracy).
     *
     * [alert] mówi, czy koniec przerwy ma się odezwać. Wołamy z nim tylko wtedy,
     * gdy przerwa dobiegła końca („Pomiń” i przerwanie treningu mają milczeć).
     */
    private suspend fun closeActive(reason: RestEndReason, alert: Boolean = false) {
        val current = _active.value ?: return
        _active.value = null
        alarms.cancel()
        if (!alert) notifier.clear()
        val closed = rests.close(current.intervalId, reason, current.plannedSeconds)
        if (alert && closed) notifier.showRestFinished(current.exerciseName)
    }

    private fun resumedWorkReason(): RestEndReason = resumedWorkReason(_active.value)

    private fun resumedWorkReason(rest: ActiveRest?): RestEndReason =
        if (rest?.extended == true) RestEndReason.EXTENDED else RestEndReason.COMPLETED
}
