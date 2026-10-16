package com.gymtracker.ui.calendar

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.gymtracker.R
import com.gymtracker.domain.model.Recurrence
import com.gymtracker.domain.model.ScheduleStatus
import com.gymtracker.domain.schedule.WEEK_START
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Kalendarz: siatka miesiąca i teksty. Część czysta (daty, siatka, konwersje
 * z Materialowego `DatePicker`) jest testowana bez Androida; część opisowa
 * korzysta z zasobów, więc jest `@Composable`.
 */

/** Komórka siatki miesiąca; [inMonth] = `false` dla brzegów sąsiednich miesięcy. */
data class CalendarCell(val date: LocalDate, val inMonth: Boolean)

/**
 * Siatka miesiąca: całe tygodnie od poniedziałku, z brzegami sąsiednich
 * miesięcy (dopełnione do pełnych tygodni). „Październik 2026” zaczyna się
 * w czwartek, więc pierwsza komórka to poniedziałek 28 września.
 */
fun monthGrid(month: YearMonth): List<CalendarCell> {
    val first = month.atDay(1)
    val leading = (first.dayOfWeek.value - WEEK_START.value + 7) % 7
    val start = first.minusDays(leading.toLong())
    val total = leading + month.lengthOfMonth()
    val cells = ((total + 6) / 7) * 7
    return (0 until cells).map { offset ->
        val date = start.plusDays(offset.toLong())
        CalendarCell(date = date, inMonth = YearMonth.from(date) == month)
    }
}

/** „październik 2026” - nagłówek miesiąca w języku interfejsu. */
fun YearMonth.headerText(locale: Locale): String =
    atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))

/** „1 października” - data bez roku, nagłówek wybranego dnia. */
fun LocalDate.dayMonthText(locale: Locale): String =
    format(DateTimeFormatter.ofPattern("d MMMM", locale))

/** „czwartek” - pełna nazwa dnia tygodnia do nagłówka wybranego dnia. */
fun LocalDate.weekdayText(locale: Locale): String = dayOfWeek.getDisplayName(TextStyle.FULL, locale)

/** „czw., 1 paź.” - zwięzła data w podglądzie i listach. */
fun LocalDate.shortDayText(locale: Locale): String =
    format(DateTimeFormatter.ofPattern("EEE, d MMM", locale))

/** „1.10.2026” - data w podsumowaniach, zawsze z rokiem i bez nazw miesięcy. */
fun LocalDate.isoText(): String = "%d.%02d.%d".format(dayOfMonth, monthValue, year)

/** Skrót dnia tygodnia: „pon.”, „wt.” … */
fun DayOfWeek.shortText(locale: Locale): String = getDisplayName(TextStyle.SHORT, locale)

/**
 * Data z Materialowego `DatePicker`. Wybór przychodzi jako północ w UTC, więc
 * czytamy go w UTC - czytanie w strefie urządzenia przesunęłoby dzień o jeden
 * na zachód od Greenwich.
 */
fun utcMillisToDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** Krótki opis reguły: „co 3 dni, od 1.10.2026 do 31.12.2026, rotacja dni”. */
@Composable
fun recurrenceSummary(recurrence: Recurrence): String {
    val interval = when {
        recurrence.intervalDays <= 1 -> stringResource(R.string.schedule_every_day)
        else -> pluralStringResource(
            R.plurals.schedule_every_days,
            recurrence.intervalDays,
            recurrence.intervalDays,
        )
    }
    val range = recurrence.endDate?.let { end ->
        stringResource(
            R.string.schedule_range,
            recurrence.startDate.isoText(),
            end.isoText(),
        )
    } ?: stringResource(R.string.schedule_range_open, recurrence.startDate.isoText())
    val weekdayLabels = recurrence.weekdays.sorted().map { day -> weekdayShortRes(day) }

    val parts = mutableListOf(interval, range)
    if (recurrence.rotatesDays) parts += stringResource(R.string.schedule_rotates_days)
    if (weekdayLabels.isNotEmpty()) parts += weekdayLabels.joinToString(separator = ", ")
    return parts.joinToString(separator = " • ")
}

/** Krótka etykieta dnia tygodnia w podsumowaniu reguły (bez `Locale` - z zasobów). */
@Composable
private fun weekdayShortRes(day: DayOfWeek): String = stringResource(
    when (day) {
        DayOfWeek.MONDAY -> R.string.weekday_short_monday
        DayOfWeek.TUESDAY -> R.string.weekday_short_tuesday
        DayOfWeek.WEDNESDAY -> R.string.weekday_short_wednesday
        DayOfWeek.THURSDAY -> R.string.weekday_short_thursday
        DayOfWeek.FRIDAY -> R.string.weekday_short_friday
        DayOfWeek.SATURDAY -> R.string.weekday_short_saturday
        DayOfWeek.SUNDAY -> R.string.weekday_short_sunday
    },
)

/** Etykieta statusu wpisu. */
@StringRes
fun statusLabelRes(status: ScheduleStatus): Int = when (status) {
    ScheduleStatus.PLANNED -> R.string.schedule_status_planned
    ScheduleStatus.DONE -> R.string.schedule_status_done
    ScheduleStatus.SKIPPED -> R.string.schedule_status_skipped
}
