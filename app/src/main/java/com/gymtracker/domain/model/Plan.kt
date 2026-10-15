package com.gymtracker.domain.model

/** Jednostka celu: powtórzenia, sekundy (serie na czas, np. deska) albo metry. */
enum class RepsUnit(val wire: String) {
    REPS("reps"),
    SECONDS("seconds"),
    METERS("meters"),
    ;

    companion object {
        fun fromWire(value: String?): RepsUnit = entries.firstOrNull { it.wire == value } ?: REPS
    }
}

/**
 * Ćwiczenie w dniu planu razem z celem na trening.
 *
 * [alternativeGroup] wypełnia się tylko dla pozycji „X albo Y” - wtedy jedna
 * z nich jest [isDefaultChoice].
 */
data class PlanExercise(
    val id: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val muscleGroup: String?,
    val equipment: String?,
    val position: Int,
    val sets: Int?,
    val repsMin: Int?,
    val repsMax: Int?,
    val repsUnit: RepsUnit,
    val weightKg: Double?,
    val restSeconds: Int?,
    val supersetGroup: Int?,
    val alternativeGroup: String?,
    val isDefaultChoice: Boolean,
    val note: String?,
) {
    /** Pozycja alternatywna (nie ta domyślna w grupie „albo”). */
    val isAlternative: Boolean get() = alternativeGroup != null && !isDefaultChoice
}

/** Dzień planu z ćwiczeniami w kolejności (posortowane po `position`). */
data class PlanDay(
    val id: Long,
    val planId: Long,
    val name: String,
    /** 0 = poniedziałek ... 6 = niedziela; `null` = brak przypisania. */
    val dayOfWeek: Int?,
    val position: Int,
    val note: String?,
    val exercises: List<PlanExercise>,
)

/** Nagłówek planu (bez dni) - do listy planów. */
data class Plan(
    val id: Long,
    val externalId: String?,
    val name: String,
    val description: String?,
    val sourceUrl: String?,
    val author: String?,
    val isActive: Boolean,
    val isArchived: Boolean,
)

/** Plan z dniami - do ekranu planu. */
data class PlanDetails(
    val plan: Plan,
    val days: List<PlanDay>,
) {
    val exerciseCount: Int get() = days.sumOf { day -> day.exercises.count { !it.isAlternative } }
}

/** Nowy plan od użytkownika. */
data class PlanDraft(
    val name: String,
    val description: String? = null,
)

/** Nowy dzień planu. */
data class PlanDayDraft(
    val name: String,
    val dayOfWeek: Int? = null,
    val note: String? = null,
)

/** Dodawane ćwiczenie dnia (albo alternatywa - wtedy przez `addAlternative`). */
data class PlanExerciseDraft(
    val exerciseId: Long,
    val sets: Int? = null,
    val repsMin: Int? = null,
    val repsMax: Int? = null,
    val repsUnit: RepsUnit = RepsUnit.REPS,
    val weightKg: Double? = null,
    val restSeconds: Int? = null,
    val note: String? = null,
)

/** Cel ćwiczenia do zmiany z ekranu dnia. */
data class PlanExerciseTargets(
    val sets: Int?,
    val repsMin: Int?,
    val repsMax: Int?,
    val repsUnit: RepsUnit,
    val weightKg: Double?,
    val restSeconds: Int?,
    val note: String?,
)
