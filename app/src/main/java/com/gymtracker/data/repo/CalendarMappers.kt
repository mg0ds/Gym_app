package com.gymtracker.data.repo

import com.gymtracker.data.db.entities.RecurrenceEntity
import com.gymtracker.data.db.entities.ScheduledSessionEntity
import com.gymtracker.domain.model.Recurrence
import com.gymtracker.domain.model.RecurrenceDraft
import com.gymtracker.domain.model.ScheduleStatus
import com.gymtracker.domain.model.ScheduledWorkout
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Mapowanie kalendarza domena <-> baza.
 *
 * Daty trzymamy jako ISO `YYYY-MM-DD`: sortują się tak samo jak leksykograficznie,
 * więc zapytania po zakresie i `MAX(date)` działają bez konwersji, a dzień nie ma
 * godziny, więc zmiana czasu go nie przesuwa.
 */
internal fun String.toLocalDate(): LocalDate = LocalDate.parse(this)

internal fun LocalDate.toWire(): String = toString()

internal fun ScheduledSessionEntity.toDomain(): ScheduledWorkout = ScheduledWorkout(
    id = id,
    date = date.toLocalDate(),
    title = title,
    planId = planId,
    planDayId = planDayId,
    recurrenceId = recurrenceId,
    status = ScheduleStatus.fromWire(status),
    sessionId = sessionId,
)

internal fun RecurrenceEntity.toDomain(): Recurrence = Recurrence(
    id = id,
    planId = planId,
    planDayId = planDayId,
    startDate = startDate.toLocalDate(),
    endDate = endDate?.toLocalDate(),
    intervalDays = intervalDays,
    weekdays = weekdaysMask.toWeekdays(),
    createdAtEpochMs = createdAtEpochMs,
)

internal fun Recurrence.toDraft(planId: Long): RecurrenceDraft = RecurrenceDraft(
    planId = planId,
    planDayId = planDayId,
    startDate = startDate,
    endDate = endDate,
    intervalDays = intervalDays,
    weekdays = weekdays,
)

internal fun RecurrenceDraft.toEntity(nowEpochMs: Long): RecurrenceEntity = RecurrenceEntity(
    planId = planId,
    planDayId = planDayId,
    startDate = startDate.toWire(),
    endDate = endDate?.toWire(),
    intervalDays = intervalDays,
    weekdaysMask = weekdays.toMask(),
    createdAtEpochMs = nowEpochMs,
)

/**
 * Dni tygodnia jako bitmaska (bit 0 = poniedziałek … bit 6 = niedziela).
 * Pusty zbiór = brak ograniczeń, więc zapisujemy `null`, a nie zero.
 */
internal fun Set<DayOfWeek>.toMask(): Int? {
    if (isEmpty()) return null
    return fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }
}

internal fun Int?.toWeekdays(): Set<DayOfWeek> {
    if (this == null || this == 0) return emptySet()
    return DayOfWeek.values().filter { day -> this and (1 shl (day.value - 1)) != 0 }.toSet()
}
