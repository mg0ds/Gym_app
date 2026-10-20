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
 * * **następna seria** - dopiero ona zna prawdziwy koniec przerwy (z czasem
 *   wykonania kolejnej serii) i to ona domyka wiersz,
 * * „Pomiń” albo koniec treningu,
 * * alarm systemowy lub ekran przy zerze - ale one tylko **powiadamiają**
 *   i chowają kartę; wiersz zostaje otwarty dla następnej serii.
 *
 * Wyjątkiem jest alarm po restarcie procesu, gdy nie ma już czego pilnować:
 * wtedy przerwa domyka się w zaplanowanym czasie, bo inaczej zostałaby
 * otwarta na zawsze.
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
     * Przerwa, o której końcu już powiadomiliśmy (alarm albo ekran).
     *
     * Po powiadomieniu [active] znika, ale wiersz w bazie **zostaje otwarty**:
     * dopiero następna seria wie, ile przerwa naprawdę trwała. Ten
     * identyfikator chroni przed podwójnym dźwiękiem, gdy alarm systemowy
     * przyjdzie chwilę po tym, jak ekran sam zauważył zero.
     */
    private var alertedIntervalId: Long? = null

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
        closeStaleOpen()
        alertedIntervalId = null
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
     * Powiadamiamy i chowamy kartę przerwy, ale **nie domykamy wiersza**:
     * użytkownik właśnie zaczyna serię, a jej koniec jest prawdziwym końcem
     * przerwy. Wiersz domknie następna seria (albo koniec treningu).
     */
    suspend fun completeIfDue(nowEpochMs: Long = timeProvider.nowEpochMs()) {
        val current = _active.value ?: return
        if (!current.isOver(nowEpochMs)) return
        alert(current)
    }

    /**
     * Alarm systemowy: pokazuje powiadomienie i chowa kartę przerwy.
     *
     * Wołane także wtedy, gdy proces wstał od zera (nie ma [active]). Wtedy nie
     * znamy przyszłej serii, więc domykamy wiersz w zaplanowanym czasie - lepszy
     * taki pomiar niż zostawienie przerwy otwartej na zawsze.
     */
    suspend fun onAlarm(intervalId: Long) {
        if (alertedIntervalId == intervalId) return

        val current = _active.value?.takeIf { rest -> rest.intervalId == intervalId }
        if (current != null) {
            alert(current)
            return
        }

        val interval = rests.get(intervalId) ?: return
        if (!interval.isRunning) return

        val planned = interval.plannedSeconds ?: 0
        alertedIntervalId = intervalId
        val reason = RestEndReason.COMPLETED
        val closed = rests.close(
            intervalId = intervalId,
            reason = reason,
            plannedSeconds = planned,
            endedAtEpochMs = interval.startedAtEpochMs + planned * 1_000L,
        )
        if (!closed) return

        val name = rests.exerciseName(interval.exerciseId).orEmpty()
        notifier.showRestFinished(name)
    }

    /**
     * Przerwa przestaje mieć sens: pauza treningu, jego koniec albo przerwanie.
     *
     * Powód to `cancelled`, więc taka przerwa nie wejdzie do średniej - nie
     * była przerwą, tylko jej pozostałością.
     */
    suspend fun cancel() {
        closeActive(RestEndReason.CANCELLED)
        closeStaleOpen(RestEndReason.CANCELLED)
    }

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
            rests.close(
                intervalId = open.id,
                reason = RestEndReason.COMPLETED,
                plannedSeconds = planned,
                endedAtEpochMs = open.startedAtEpochMs + planned * 1_000L,
            )
            notifier.clear()
            return
        }

        _active.value = rest
        alarms.schedule(rest.intervalId, rest.endsAtEpochMs)
    }

    /**
     * Koniec zaplanowanej przerwy: chowamy kartę i powiadamiamy, ale zostawiamy
     * wiersz otwarty. Następna seria go domknie i dopiero wtedy znamy prawdziwy
     * czas przerwy - z wykonaniem kolejnej serii włącznie.
     */
    private suspend fun alert(rest: ActiveRest) {
        if (alertedIntervalId == rest.intervalId) return
        alertedIntervalId = rest.intervalId
        _active.value = null
        alarms.cancel()
        notifier.showRestFinished(rest.exerciseName)
    }

    /**
     * Domyka pilnowaną przerwę jako skończoną (użytkownik wrócił do pracy).
     */
    private suspend fun closeActive(reason: RestEndReason) {
        val current = _active.value ?: return
        _active.value = null
        alarms.cancel()
        notifier.clear()
        rests.close(current.intervalId, reason, current.plannedSeconds)
    }

    /**
     * Domyka wiersz, którego nie pilnuje już [active] - przerwę po powiadomieniu
     * (czekała na następną serię) albo osieroconą po restarcie.
     */
    private suspend fun closeStaleOpen(reason: RestEndReason = RestEndReason.COMPLETED) {
        val open = rests.getOpen() ?: return
        rests.close(open.id, reason, open.plannedSeconds ?: 0)
    }

    private fun resumedWorkReason(): RestEndReason =
        if (_active.value?.extended == true) RestEndReason.EXTENDED else RestEndReason.COMPLETED
}
