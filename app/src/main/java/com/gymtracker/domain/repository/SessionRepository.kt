package com.gymtracker.domain.repository

import com.gymtracker.domain.model.SessionDetails
import com.gymtracker.domain.model.SetLogDraft
import com.gymtracker.domain.model.WorkoutSession
import com.gymtracker.domain.timer.WorkoutTimerState
import kotlinx.coroutines.flow.Flow

/**
 * Sesje treningowe: start, logowanie serii, pauza, koniec.
 *
 * Sesja jest **jedynym** trwałym śladem treningu, więc wszystko, co się dzieje
 * w trakcie (serie, notatka, pauza), zapisujemy od razu - aplikacja może zginąć
 * w każdej chwili, a trening ma zostać.
 */
interface SessionRepository {

    /** Trwający trening (o ile jest) razem z ćwiczeniami i seriami. */
    fun observeActiveSession(): Flow<SessionDetails?>

    /**
     * Sam nagłówek trwającego treningu - tania wersja dla usługi timera, która
     * nie potrzebuje ćwiczeń i nie powinna ich budzić przy każdym tyknięciu.
     */
    fun observeActiveSessionHead(): Flow<WorkoutSession?>

    /** Jednorazowy odczyt trwającego treningu - do odtworzenia zegara. */
    suspend fun getActiveSession(): WorkoutSession?

    fun observeSession(sessionId: Long): Flow<SessionDetails?>

    /**
     * Rozpoczyna trening z dnia planu: tworzy sesję i kopiuje do niej ćwiczenia
     * dnia (tylko wybory domyślne, bez alternatyw). Zwraca id sesji;
     * gdy dzień nie istnieje - `null`.
     *
     * Gdy na dziś jest zaplanowany wpis w kalendarzu dla tego dnia planu, sesja
     * wiąże się z nim w tej samej transakcji - zakończony trening odhaczy wpis
     * (M4 domyka odroczenie z M3).
     */
    suspend fun startFromPlanDay(planDayId: Long): Long?

    /**
     * Rozpoczyna trening z wpisu w kalendarzu i wiąże wpis z sesją. Gdy plan
     * (albo dzień) już nie istnieje, powstaje sesja bez ćwiczeń, ale z nazwą
     * wpisu - zaplanowany dzień nie może stać się nierozpoczynalny.
     */
    suspend fun startFromSchedule(scheduledId: Long): Long?

    suspend fun finishSession(sessionId: Long)

    /** Przerywa trening bez zapisywania go jako odbytego. */
    suspend fun cancelSession(sessionId: Long)

    /** Notatka do treningu; pusta wartość czyści notatkę. */
    suspend fun setNote(sessionId: Long, note: String?)

    /**
     * Zapisuje stan zegara treningu (suma pauz i otwarta pauza).
     *
     * Pauza jest zapisywana **od razu**, nie dopiero przy końcu treningu -
     * inaczej zabicie aplikacji w pauzie doliczyłoby ją do czasu treningu.
     */
    suspend fun persistTimer(sessionId: Long, state: WorkoutTimerState)

    /**
     * Dopisuje serię do ćwiczenia treningu. Zwraca id serii albo `null`, gdy
     * seria nie ma sensu dla jednostki celu (np. brak powtórzeń przy `5 × 5`) -
     * walidację robimy tutaj, bo tylko tutaj znamy jednostkę.
     */
    suspend fun addSet(sessionExerciseId: Long, draft: SetLogDraft): Long?

    /** Zmienia zapisaną serię (ciężar, powtórzenia, znacznik rozgrzewki). */
    suspend fun updateSet(setLogId: Long, draft: SetLogDraft): Boolean

    /**
     * Usuwa serię i przenumerowuje pozostałe, żeby numeracja została ciągła
     * („seria 1, 2, 3”, a nie „1, 3”).
     */
    suspend fun deleteSet(setLogId: Long)
}
