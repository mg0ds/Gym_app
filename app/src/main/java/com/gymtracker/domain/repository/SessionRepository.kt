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

    suspend fun finishSession(sessionId: Long)

    /** Przerywa trening bez zapisywania go jako odbytego. */
    suspend fun cancelSession(sessionId: Long)
}
