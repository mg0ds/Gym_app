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

/** Sesja treningowa (w M2: czas i lista ćwiczeń z planu). */
data class WorkoutSession(
    val id: Long,
    val planId: Long?,
    val planDayId: Long?,
    val name: String,
    val startedAtEpochMs: Long,
    val finishedAtEpochMs: Long?,
    val status: SessionStatus,
    val note: String?,
)

/** Ćwiczenie w sesji wraz z celem skopiowanym z planu w momencie startu. */
data class SessionExercise(
    val id: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val muscleGroup: String?,
    val position: Int,
    val sets: Int?,
    val repsMin: Int?,
    val repsMax: Int?,
    val repsUnit: RepsUnit,
    val restSeconds: Int?,
    val supersetGroup: Int?,
    val alternativeGroup: String?,
    val isDefaultChoice: Boolean,
    val note: String?,
)

/** Sesja z ćwiczeniami - do ekranu treningu. */
data class SessionDetails(
    val session: WorkoutSession,
    val exercises: List<SessionExercise>,
)
