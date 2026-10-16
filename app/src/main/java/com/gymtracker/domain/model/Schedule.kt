package com.gymtracker.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Status wpisu w kalendarzu.
 *
 * `planned` = zaplanowany, `done` = odbyty, `skipped` = pominięty. Nie ma
 * osobnego statusu „przeniesiony” - przeniesienie to zmiana daty wpisu.
 */
enum class ScheduleStatus(val wire: String) {
    PLANNED("planned"),
    DONE("done"),
    SKIPPED("skipped"),
    ;

    companion object {
        fun fromWire(value: String?): ScheduleStatus =
            entries.firstOrNull { it.wire == value } ?: PLANNED
    }
}

/**
 * Pojedynczy wpis w kalendarzu: „w tym dniu mam trenować [title]”.
 *
 * [title] jest kopią nazwy dnia z momentu planowania, więc wpis zostaje
 * czytelny nawet po zmianie nazwy dnia albo usunięciu planu ([planId] /
 * [planDayId] robią się wtedy `null`).
 */
data class ScheduledWorkout(
    val id: Long,
    val date: LocalDate,
    val title: String,
    val planId: Long?,
    val planDayId: Long?,
    val recurrenceId: Long?,
    val status: ScheduleStatus,
    val sessionId: Long?,
) {
    /** Wpis pochodzi z reguły powtarzania (w kalendarzu oznaczamy go „⟳”). */
    val isFromRecurrence: Boolean get() = recurrenceId != null

    /** Można jeszcze rozpocząć trening albo zmienić status. */
    val isOpen: Boolean get() = status == ScheduleStatus.PLANNED
}

/**
 * Reguła powtarzania: „co [intervalDays] dni, od [startDate] do [endDate],
 * (opcjonalnie) tylko w dni [weekdays]”.
 *
 * [planDayId] = `null` oznacza rotację po dniach planu (A → B → C → A…) -
 * kolejne daty dostają kolejne dni w kolejności `plan_days.position`.
 *
 * [weekdays] puste = bez ograniczeń (każdy dzień tygodnia).
 */
data class Recurrence(
    val id: Long,
    val planId: Long?,
    val planDayId: Long?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val intervalDays: Int,
    val weekdays: Set<DayOfWeek>,
    val createdAtEpochMs: Long,
) {
    val rotatesDays: Boolean get() = planDayId == null
}

/** Nowa (albo zmieniana) reguła - to, co wypełnia formularz planowania. */
data class RecurrenceDraft(
    val planId: Long,
    val planDayId: Long? = null,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val intervalDays: Int = 1,
    val weekdays: Set<DayOfWeek> = emptySet(),
)

/** Data z przypisanym dniem planu - wynik rozwinięcia reguły. */
data class PlannedWorkout(
    val date: LocalDate,
    val planDayId: Long?,
    val title: String,
)
