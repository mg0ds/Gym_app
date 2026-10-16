package com.gymtracker.domain.repository

import com.gymtracker.domain.model.SessionDetails
import kotlinx.coroutines.flow.Flow

/** Sesje treningowe. W M2: start z dnia planu, podgląd i zakończenie. */
interface SessionRepository {

    /** Trwający trening (o ile jest) - używane przez ekran „Dziś”. */
    fun observeActiveSession(): Flow<SessionDetails?>

    fun observeSession(sessionId: Long): Flow<SessionDetails?>

    /**
     * Rozpoczyna trening z dnia planu: tworzy sesję i kopiuje do niej ćwiczenia
     * dnia (tylko wybory domyślne, bez alternatyw). Zwraca id sesji;
     * gdy dzień nie istnieje - `null`.
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
}
