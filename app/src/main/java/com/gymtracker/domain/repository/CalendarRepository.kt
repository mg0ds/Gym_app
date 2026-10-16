package com.gymtracker.domain.repository

import com.gymtracker.domain.model.Recurrence
import com.gymtracker.domain.model.RecurrenceDraft
import com.gymtracker.domain.model.ScheduleStatus
import com.gymtracker.domain.model.ScheduledWorkout
import com.gymtracker.domain.schedule.SchedulePreview
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Wynik zapisu reguły: albo ile wpisów powstało, albo co poprawić. */
sealed interface ScheduleSaveResult {
    data class Saved(val count: Int) : ScheduleSaveResult

    data class Invalid(val preview: SchedulePreview) : ScheduleSaveResult
}

/**
 * Kalendarz treningów.
 *
 * Widok czyta **wpisy** ([ScheduledWorkout]) w zakresie dat, a nie reguły -
 * reguły rozwijamy raz, przy planowaniu, i dosypujemy tylko w przód
 * ([refreshHorizon]). Zmiana reguły to przeliczenie: wpisy `planned` znikają,
 * `done` i `skipped` zostają jako historia.
 */
interface CalendarRepository {

    /** Wpisy z zakresu dat włącznie (kalendarz pokazuje cały miesiąc z brzegami). */
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<ScheduledWorkout>>

    fun observeRecurrencesOfPlan(planId: Long): Flow<List<Recurrence>>

    /** Pojedyncza reguła - formularz wypełnia się nią przy zmianie harmonogramu. */
    suspend fun getRecurrence(recurrenceId: Long): Recurrence?

    /**
     * Planowanie przez wybór dni (bez reguły): wpisy na podane daty.
     * [planDayId] = `null` rotuje dni planu, jak w regule. Daty poza oknem
     * planowania (od dziś do horyzontu) są pomijane.
     * Zwraca liczbę dodanych wpisów - istniejące zostają bez zmian.
     */
    suspend fun planDates(planId: Long, planDayId: Long?, dates: List<LocalDate>): Int

    /** Sprawdza regułę i pokazuje, co z niej wyjdzie - bez zapisu. */
    suspend fun previewRecurrence(draft: RecurrenceDraft): SchedulePreview

    /** Tworzy regułę i rozplanowuje wpisy do horyzontu. */
    suspend fun planRecurrence(draft: RecurrenceDraft): ScheduleSaveResult

    /**
     * Zmienia regułę: usuwa jej wpisy `planned` (i samą regułę), a potem tworzy
     * nową. Odbyte i pominięte zostają jako historia.
     */
    suspend fun replaceRecurrence(recurrenceId: Long, draft: RecurrenceDraft): ScheduleSaveResult

    /** Usuwa regułę i jej zaplanowane wpisy; historia zostaje. */
    suspend fun deleteRecurrence(recurrenceId: Long)

    /**
     * Dosypuje wpisy aktywnych reguł do aktualnego horyzontu (20 tygodni).
     * Wołane przy wejściu w kalendarz - horyzont przesuwa się razem z „dziś”.
     */
    suspend fun refreshHorizon(): Int

    suspend fun setStatus(id: Long, status: ScheduleStatus)

    /** Przenosi wpis na inny dzień (wpis zostaje ten sam, zmienia się data). */
    suspend fun move(id: Long, date: LocalDate)

    /** Usuwa wpis z kalendarza (nie rusza treningu, który już się odbył). */
    suspend fun removeEntry(id: Long)
}
