package com.gymtracker.data.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gymtracker.data.db.entities.RecurrenceEntity
import com.gymtracker.data.db.entities.ScheduledSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Kalendarz: reguły powtarzania i rozplanowane wpisy.
 *
 * Daty są tekstem ISO (`YYYY-MM-DD`), więc porównania i `MAX` działają wprost
 * (kolejność leksykograficzna = kolejność chronologiczna) i nie ma pułapek
 * stref czasowych ani DST.
 *
 * Wpisy wstawiamy z [OnConflictStrategy.IGNORE]: `UNIQUE(date, plan_day_id)`
 * odsiewa duplikaty przy dosypywaniu horyzontu i planowaniu ręcznym.
 */
@Dao
interface CalendarDao {

    // --- wpisy (scheduled_sessions) ---

    @Query(
        """
        SELECT * FROM scheduled_sessions
        WHERE date BETWEEN :from AND :to
        ORDER BY date, id
        """,
    )
    fun observeBetween(from: String, to: String): Flow<List<ScheduledSessionEntity>>

    @Query("SELECT * FROM scheduled_sessions WHERE id = :id")
    suspend fun getScheduled(id: Long): ScheduledSessionEntity?

    @Query("SELECT * FROM scheduled_sessions WHERE date = :date ORDER BY id")
    suspend fun getScheduledOn(date: String): List<ScheduledSessionEntity>

    /**
     * Zaplanowany wpis dla danego dnia planu i daty - używany przy starcie
     * treningu z planu, żeby powiązać sesję z wpisem w kalendarzu.
     *
     * `UNIQUE(date, plan_day_id)` gwarantuje, że taki wpis jest **najwyżej
     * jeden**, więc dopasowanie po dniu planu i dacie nie wymaga zgadywania
     * (odroczenie z M3; patrz PLAN.md 8.1).
     */
    @Query(
        """
        SELECT * FROM scheduled_sessions
        WHERE date = :date AND plan_day_id = :planDayId AND status = 'planned'
        LIMIT 1
        """,
    )
    suspend fun findPlannedFor(date: String, planDayId: Long): ScheduledSessionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertScheduled(entries: List<ScheduledSessionEntity>): List<Long>

    @Query("UPDATE scheduled_sessions SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: String)

    /** Przeniesienie wpisu: zmieniamy datę, nie tworzymy drugiego wiersza. */
    @Query("UPDATE scheduled_sessions SET date = :date WHERE id = :id")
    suspend fun setDate(id: Long, date: String)

    @Query("UPDATE scheduled_sessions SET session_id = :sessionId WHERE id = :id")
    suspend fun linkSession(id: Long, sessionId: Long)

    /** Wpis, który powstał z danego treningu (pokazuje „Otwórz trening”). */
    @Query("SELECT * FROM scheduled_sessions WHERE session_id = :sessionId LIMIT 1")
    suspend fun findBySession(sessionId: Long): ScheduledSessionEntity?

    /**
     * Odczepia wpis od treningu i cofa go do „zaplanowany”: trening należy już
     * do innego dnia, więc wpis nie może dalej udawać, że został odbyty.
     */
    @Query("UPDATE scheduled_sessions SET session_id = NULL, status = 'planned' WHERE session_id = :sessionId")
    suspend fun unlinkSession(sessionId: Long)

    /**
     * Odczepia wpis od treningu, który się **nie odbył** (przerwany), ale nie
     * tyka statusu: wpis zostaje „zaplanowany”, a jeśli użytkownik ręcznie
     * odhaczył dzień, to jego decyzja nie przepada. Bez tego `session_id`
     * wskazywałoby na przerwany trening i wpis wyglądałby na powiązany.
     */
    @Query("UPDATE scheduled_sessions SET session_id = NULL WHERE session_id = :sessionId")
    suspend fun detachSession(sessionId: Long)

    /** Zakończony trening odhacza wpis, z którego powstał. */
    @Query("UPDATE scheduled_sessions SET status = 'done' WHERE session_id = :sessionId")
    suspend fun markDoneForSession(sessionId: Long)

    @Query("DELETE FROM scheduled_sessions WHERE id = :id")
    suspend fun deleteScheduled(id: Long)

    /** Najpóźniejsza data wpisu reguły - od niej dosypujemy horyzont w przód. */
    @Query("SELECT MAX(date) FROM scheduled_sessions WHERE recurrence_id = :recurrenceId")
    suspend fun lastDateOfRecurrence(recurrenceId: Long): String?

    /**
     * Liczba wpisów reguły (także odbyte i pominięte). Rotacja A/B/C liczy się
     * od pierwszego wpisu, więc przy dosypywaniu musimy wiedzieć, na którym
     * dniu planu skończyliśmy.
     */
    @Query("SELECT COUNT(*) FROM scheduled_sessions WHERE recurrence_id = :recurrenceId")
    suspend fun countOfRecurrence(recurrenceId: Long): Int

    /**
     * Usuwa **tylko zaplanowane** wpisy reguły. Odbyte i pominięte zostają -
     * to już historia, a nie plan.
     */
    @Query("DELETE FROM scheduled_sessions WHERE recurrence_id = :recurrenceId AND status = 'planned'")
    suspend fun deletePlannedOfRecurrence(recurrenceId: Long)

    @Query("SELECT COUNT(*) FROM scheduled_sessions")
    suspend fun countScheduled(): Int

    // --- reguły (recurrences) ---

    @Query("SELECT * FROM recurrences ORDER BY start_date, id")
    fun observeRecurrences(): Flow<List<RecurrenceEntity>>

    @Query("SELECT * FROM recurrences WHERE plan_id = :planId ORDER BY start_date, id")
    fun observeRecurrencesOfPlan(planId: Long): Flow<List<RecurrenceEntity>>

    @Query("SELECT * FROM recurrences ORDER BY start_date, id")
    suspend fun getRecurrences(): List<RecurrenceEntity>

    @Query("SELECT * FROM recurrences WHERE id = :id")
    suspend fun getRecurrence(id: Long): RecurrenceEntity?

    /**
     * Reguła o dokładnie takich samych parametrach. Ratuje przed dwiema
     * identycznymi regułami, gdy użytkownik dwa razy zapisze ten sam formularz.
     */
    @Query(
        """
        SELECT * FROM recurrences
        WHERE plan_id = :planId
          AND IFNULL(plan_day_id, 0) = IFNULL(:planDayId, 0)
          AND start_date = :startDate
          AND IFNULL(end_date, '') = IFNULL(:endDate, '')
          AND interval_days = :intervalDays
          AND IFNULL(weekdays_mask, 0) = IFNULL(:mask, 0)
        LIMIT 1
        """,
    )
    suspend fun findIdentical(
        planId: Long,
        planDayId: Long?,
        startDate: String,
        endDate: String?,
        intervalDays: Int,
        mask: Int?,
    ): RecurrenceEntity?

    @Insert
    suspend fun insertRecurrence(recurrence: RecurrenceEntity): Long

    @Query("DELETE FROM recurrences WHERE id = :id")
    suspend fun deleteRecurrence(id: Long)
}
