package com.gymtracker.domain.model

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
) {
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
    val restSeconds: Int?,
    val supersetGroup: Int?,
    val alternativeGroup: String?,
    val isDefaultChoice: Boolean,
    val note: String?,
    val logs: List<SetLog> = emptyList(),
)

/** Sesja z ćwiczeniami - do ekranu treningu. */
data class SessionDetails(
    val session: WorkoutSession,
    val exercises: List<SessionExercise>,
)
