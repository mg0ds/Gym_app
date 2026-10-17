package com.gymtracker.domain.timer

/**
 * Trwająca przerwa między seriami.
 *
 * Model jest **odtwarzalny z bazy**: `started_at` + cel wystarczą, żeby po
 * restarcie procesu odliczanie wstało dokładnie tam, gdzie było. Dlatego
 * końca przerwy nie zapamiętujemy jako osobnej liczby - liczymy go z początku
 * i celu. Dzięki temu „+15 s” nie potrzebuje własnego pola: przesuwa cel, a
 * koniec idzie za nim.
 *
 * Czas liczymy z zegara ściennego, tak jak trening (patrz [WorkoutTimerState]).
 */
data class ActiveRest(
    val intervalId: Long,
    val sessionId: Long?,
    val exerciseId: Long,
    val exerciseName: String,
    /** Cel po korektach („+15 s”, „-15 s”), w sekundach. */
    val plannedSeconds: Int,
    val startedAtEpochMs: Long,
    /**
     * Czy użytkownik wydłużał przerwę. Rozróżniamy to w powodu zakończenia
     * (`extended` vs `completed`); statystyki liczą oba tak samo.
     */
    val extended: Boolean = false,
) {

    val endsAtEpochMs: Long get() = startedAtEpochMs + plannedSeconds * 1_000L

    /** Ile przerwy jeszcze zostało; nigdy poniżej zera. */
    fun remainingMs(nowEpochMs: Long): Long = (endsAtEpochMs - nowEpochMs).coerceAtLeast(0L)

    /** Czy cel już minął - przerwa czeka tylko na zamknięcie. */
    fun isOver(nowEpochMs: Long): Boolean = nowEpochMs >= endsAtEpochMs

    /** „+15 s” (albo dowolny krok): wydłuża cel i przesuwa koniec. */
    fun extendedBy(seconds: Int): ActiveRest =
        if (seconds <= 0) {
            this
        } else {
            copy(plannedSeconds = plannedSeconds + seconds, extended = true)
        }

    /**
     * Skrócenie przerwy. Cel nie schodzi poniżej [MIN_PLANNED_SECONDS] -
     * przerwa „zerowa” nie jest przerwą, a użytkownik i tak może ją pominąć.
     */
    fun shrunkBy(seconds: Int): ActiveRest =
        if (seconds <= 0) {
            this
        } else {
            copy(plannedSeconds = (plannedSeconds - seconds).coerceAtLeast(MIN_PLANNED_SECONDS))
        }

    companion object {
        /** Cel krótszy niż to nie ma sensu - odliczanie kończyłoby się od razu. */
        const val MIN_PLANNED_SECONDS = 5

        /** Krok przycisków „-15 s” / „+15 s”. */
        const val STEP_SECONDS = 15
    }
}
