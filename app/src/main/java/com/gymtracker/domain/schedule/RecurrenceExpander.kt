package com.gymtracker.domain.schedule

import com.gymtracker.domain.model.PlannedWorkout
import com.gymtracker.domain.model.RecurrenceDraft
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Reguły horyzontu planowania - wspólne dla domeny i formularza. */
object ScheduleLimits {

    /**
     * Jak daleko w przód wolno planować. Dwadzieścia tygodni (≈ 4,5 miesiąca)
     * to dość, by objąć mezocykl treningowy, i dość mało, by plan nie zamienił
     * się w bezterminową listę, której nikt nie pilnuje.
     */
    const val MAX_WEEKS_AHEAD: Int = 20

    const val MAX_HORIZON_DAYS: Long = MAX_WEEKS_AHEAD * 7L

    /** Górna granica daty w kalendarzu (również dla „data do” i przenoszenia). */
    fun horizonEnd(today: LocalDate): LocalDate = today.plusWeeks(MAX_WEEKS_AHEAD.toLong())
}

/** Powód odrzucenia reguły - UI tłumaczy te wartości na komunikaty. */
enum class ScheduleError {
    /** Data od wypada dalej niż horyzont (20 tygodni). */
    START_BEYOND_HORIZON,

    /** Data do jest przed datą od. */
    END_BEFORE_START,

    /** Data do wypada dalej niż horyzont (20 tygodni). */
    END_BEYOND_HORIZON,

    /** „Co ile dni” musi być co najmniej 1. */
    INTERVAL_TOO_SMALL,

    /** Plan nie ma dni - nie ma czego planować. */
    NO_PLAN_DAY,
}

/** Wynik walidacji formularza widziany przez UI. */
data class SchedulePreview(
    val errors: List<ScheduleError>,
    val workouts: List<PlannedWorkout>,
) {
    val isValid: Boolean get() = errors.isEmpty()
}

/**
 * Rozwijanie reguły w daty. Wszystko jest **czystą funkcją** przyjmującą „dziś”
 * jako parametr - dzięki temu reguły horyzontu i rotacji testuje się bez zegara
 * i bez bazy.
 *
 * Daty liczymy jako [LocalDate] (bez godzin), więc przejście na czas zimowy nie
 * gubi ani nie dubluje dnia.
 */
object RecurrenceExpander {

    /**
     * Ostatni dzień, na który reguła może wygenerować wpisy: koniec „daty do”
     * albo horyzont 20 tygodni - cokolwiek wypada wcześniej.
     */
    fun horizon(today: LocalDate, endDate: LocalDate?): LocalDate {
        val limit = ScheduleLimits.horizonEnd(today)
        return if (endDate == null || endDate.isAfter(limit)) limit else endDate
    }

    /**
     * Daty reguły od [RecurrenceDraft.startDate] do horyzontu.
     *
     * Dzień wchodzi do wyniku, gdy od daty startu minęła wielokrotność
     * [RecurrenceDraft.intervalDays] **oraz** jego dzień tygodnia mieści się w
     * [RecurrenceDraft.weekdays] (pusty zbiór = każdy dzień).
     *
     * Daty przed „dziś” pomijamy: nie planujemy tego, co już się stało. Reguła
     * może jednak zaczynać się wcześniej - tak wygląda edycja harmonogramu
     * założonego w poprzednich tygodniach i wtedy rytm „co N dni” liczy się
     * dalej od pierwotnego startu, a nie od dnia edycji.
     */
    fun dates(draft: RecurrenceDraft, today: LocalDate): List<LocalDate> {
        val end = horizon(today, draft.endDate)
        val step = draft.intervalDays.coerceAtLeast(1)
        val first = if (draft.startDate.isAfter(today)) draft.startDate else today
        if (first.isAfter(end)) return emptyList()

        val result = mutableListOf<LocalDate>()
        var date = first
        var offset = ChronoUnit.DAYS.between(draft.startDate, first)
        while (!date.isAfter(end)) {
            val onInterval = offset % step == 0L
            val allowedDay = draft.weekdays.isEmpty() || date.dayOfWeek in draft.weekdays
            if (onInterval && allowedDay) result += date
            date = date.plusDays(1)
            offset++
        }
        return result
    }

    /**
     * Przypisuje daty do dni planu: pierwsza data dostaje pierwszy dzień,
     * druga drugi i tak dalej, a po wyczerpaniu listy wracamy do początku
     * (rotacja A/B/C). Jednoelementowa lista = zawsze ten sam dzień.
     *
     * [startIndex] pozwala kontynuować rotację po dosypaniu horyzontu - wtedy
     * pierwsza nowa data dostaje dzień następny po ostatnim już rozplanowanym.
     */
    fun assignDays(
        dates: List<LocalDate>,
        days: List<PlannedDayName>,
        startIndex: Int = 0,
    ): List<PlannedWorkout> {
        if (days.isEmpty()) return emptyList()
        return dates.mapIndexed { index, date ->
            val day = days[(startIndex + index).mod(days.size)]
            PlannedWorkout(date = date, planDayId = day.id, title = day.name)
        }
    }

    /**
     * Sprawdza regułę zanim cokolwiek zapiszemy. [days] to dni, którymi można
     * rotować (dla pojedynczego dnia - lista jednoelementowa).
     *
     * Daty z przeszłości nie są błędem: [dates] po prostu ich nie tworzy.
     */
    fun validate(
        draft: RecurrenceDraft,
        today: LocalDate,
        days: List<PlannedDayName>,
    ): List<ScheduleError> = buildList {
        val limit = ScheduleLimits.horizonEnd(today)
        if (draft.startDate.isAfter(limit)) add(ScheduleError.START_BEYOND_HORIZON)
        draft.endDate?.let { end ->
            if (end.isBefore(draft.startDate)) add(ScheduleError.END_BEFORE_START)
            else if (end.isAfter(limit)) add(ScheduleError.END_BEYOND_HORIZON)
        }
        if (draft.intervalDays < 1) add(ScheduleError.INTERVAL_TOO_SMALL)
        if (days.isEmpty()) add(ScheduleError.NO_PLAN_DAY)
    }
}

/** Nazwa i identyfikator dnia planu - [RecurrenceExpander] nie potrzebuje więcej. */
data class PlannedDayName(val id: Long, val name: String)

/** Pierwszy dzień tygodnia w ujęciu kalendarza: poniedziałek. */
val WEEK_START: DayOfWeek = DayOfWeek.MONDAY
