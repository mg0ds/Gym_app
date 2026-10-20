package com.gymtracker.domain.model

import com.gymtracker.domain.schedule.ScheduleLimits
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Stan sesji: trwa, zakończona, przerwana. */
enum class SessionStatus(val wire: String) {
    ACTIVE("active"),
    FINISHED("finished"),
    CANCELLED("cancelled"),
    ;

    companion object {
        fun fromWire(value: String?): SessionStatus =
            entries.firstOrNull { it.wire == value } ?: ACTIVE
    }
}

/**
 * Sesja treningowa: czas, lista ćwiczeń i notatka.
 *
 * Pauzy ([pausedTotalMs], [pausedAtEpochMs]) są częścią sesji, nie tylko stanem
 * ekranu - patrz `SessionEntity`. `pausedAtEpochMs != null` znaczy „zegar stoi”.
 *
 * [sessionDate] to dzień, któremu trening jest przypisany (wpis z kalendarza,
 * z którego go rozpoczęto). `null` znaczy „dzień, w którym trening się zaczął” -
 * dlatego pytania o datę idą przez [dateIn], a nie przez samo pole.
 */
data class WorkoutSession(
    val id: Long,
    val planId: Long?,
    val planDayId: Long?,
    val name: String,
    val startedAtEpochMs: Long,
    val finishedAtEpochMs: Long?,
    val status: SessionStatus,
    val note: String?,
    val pausedTotalMs: Long = 0L,
    val pausedAtEpochMs: Long? = null,
    val sessionDate: LocalDate? = null,
) {
    /** Dzień treningu: przypisany w kalendarzu, a gdy go brak - dzień startu. */
    fun dateIn(zone: ZoneId): LocalDate =
        sessionDate ?: Instant.ofEpochMilli(startedAtEpochMs).atZone(zone).toLocalDate()

    /**
     * Okno, w którym wolno poprawić dzień treningu („zmień dzień” w historii):
     * od doby przed dniem startu do końca horyzontu planowania.
     *
     * Dobę przed start zostawiamy, bo trening zaczęty po północy należy do
     * dnia, który się właśnie skończył. Do przodu nie wypuszczamy dalej niż
     * horyzont: wpis w kalendarzu idzie za treningiem, a kalendarz dalej niż
     * 20 tygodni nie planuje.
     */
    fun dayEditRange(zone: ZoneId, today: LocalDate): ClosedRange<LocalDate> {
        val day = dateIn(zone)
        val startedDay = Instant.ofEpochMilli(startedAtEpochMs).atZone(zone).toLocalDate()
        return minOf(day, startedDay).minusDays(1)..maxOf(day, ScheduleLimits.horizonEnd(today))
    }

    /**
     * Czas treningu bez pauz; `null`, gdy trening jeszcze trwa.
     *
     * Dla trwającego treningu zegar prowadzi `WorkoutTimer` (ten sam wzór, ale
     * z „teraz” i z otwartą pauzą) - dlatego tutaj liczymy tylko zamknięte
     * treningi, żeby nie było dwóch odpowiedzi na to samo pytanie.
     */
    val durationMs: Long?
        get() = finishedAtEpochMs?.let { finished ->
            (finished - startedAtEpochMs - pausedTotalMs).coerceAtLeast(0L)
        }
}

/**
 * Trening widziany z kalendarza: dzień, nazwa i status.
 *
 * Kalendarz czyta wpisy **i** treningi, bo trening może istnieć bez wpisu
 * („Nowy trening” z dnia bez planu). Bez tego dzień wyglądałby na pusty, choć
 * historia ma pod nim trening.
 */
data class DayWorkout(
    val sessionId: Long,
    val date: LocalDate,
    val name: String,
    val status: SessionStatus,
) {
    /** Trening się odbył - trwający jest dopiero „w toku”. */
    val isFinished: Boolean get() = status == SessionStatus.FINISHED

    /** Znacznik dnia w siatce: odbyty to „odbyty”, trwający - jak zaplanowany. */
    val mark: ScheduleStatus
        get() = if (isFinished) ScheduleStatus.DONE else ScheduleStatus.PLANNED
}

/**
 * Ćwiczenie w sesji wraz z celem skopiowanym z planu w momencie startu
 * oraz zalogowanymi seriami ([logs]).
 */
data class SessionExercise(
    val id: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val muscleGroup: String?,
    val position: Int,
    val targetSets: Int?,
    val repsMin: Int?,
    val repsMax: Int?,
    val repsUnit: RepsUnit,
    /**
     * Sposób zapisu serii skopiowany z katalogu w momencie startu. Zmiana typu
     * ćwiczenia w katalogu nie przepisuje historii - tak samo jak cel z planu.
     */
    val tracking: ExerciseTracking = ExerciseTracking.WEIGHT_REPS,
    /** Cel przerwy z planu (to on ma pierwszeństwo). */
    val restSeconds: Int?,
    /**
     * Domyślna przerwa z karty ćwiczenia - druga w kolejności, a przed
     * ustawieniem globalnym. Katalog nie jest kopiowany do sesji, więc to
     * jedyny ślad jego preferencji na ekranie treningu.
     */
    val exerciseDefaultRestSeconds: Int? = null,
    val supersetGroup: Int?,
    val alternativeGroup: String?,
    val isDefaultChoice: Boolean,
    val note: String?,
    val logs: List<SetLog> = emptyList(),
) {

    /**
     * Cel przerwy po tej serii: plan → karta ćwiczenia → ustawienie globalne.
     *
     * Zero i wartości ujemne traktujemy jak brak celu - inaczej „przerwa 0 s”
     * z planu wyglądałaby jak przerwa, a nie jak jej brak.
     */
    fun restTargetSeconds(globalDefaultSeconds: Int): Int =
        listOfNotNull(restSeconds, exerciseDefaultRestSeconds)
            .firstOrNull { seconds -> seconds > 0 }
            ?: globalDefaultSeconds
}

/** Sesja z ćwiczeniami - do ekranu treningu. */
data class SessionDetails(
    val session: WorkoutSession,
    val exercises: List<SessionExercise>,
)
