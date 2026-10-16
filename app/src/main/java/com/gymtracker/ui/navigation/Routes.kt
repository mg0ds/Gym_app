package com.gymtracker.ui.navigation

/**
 * Trasy nawigacji. Ekrany mają identyfikatory w adresie, bo nawigacja Compose
 * nie przekazuje obiektów - tylko argumenty trasy.
 */
object Routes {

    const val HOME = "home"
    const val EXERCISES = "exercises"
    const val SETTINGS = "settings"

    /** Nazwa argumentu z identyfikatorem ćwiczenia (typ `Long`). */
    const val ARG_EXERCISE_ID = "exerciseId"

    const val EXERCISE_NEW = "exercise/new"
    const val EXERCISE_DETAIL = "exercise/{$ARG_EXERCISE_ID}"
    const val EXERCISE_EDIT = "exercise/{$ARG_EXERCISE_ID}/edit"

    fun exerciseDetail(exerciseId: Long): String = "exercise/$exerciseId"

    fun exerciseEdit(exerciseId: Long): String = "exercise/$exerciseId/edit"

    // --- plany (M2) ---

    const val ARG_PLAN_ID = "planId"
    const val ARG_DAY_ID = "dayId"
    const val ARG_SESSION_ID = "sessionId"

    /** Ćwiczenie, do którego dokładamy alternatywę (0 = zwykłe dodawanie). */
    const val ARG_ANCHOR_ID = "anchorId"

    /** Reguła zmieniana w formularzu harmonogramu (0 = zakładanie nowej). */
    const val ARG_RECURRENCE_ID = "recurrenceId"

    const val PLANS = "plans"
    const val PLAN_DETAIL = "plan/{$ARG_PLAN_ID}"
    const val PLAN_DAY = "planDay/{$ARG_DAY_ID}"
    const val PLAN_EXERCISE_PICKER = "planDay/{$ARG_DAY_ID}/pickExercise?$ARG_ANCHOR_ID={$ARG_ANCHOR_ID}"
    const val PLAN_SCHEDULE = "plan/{$ARG_PLAN_ID}/schedule?$ARG_RECURRENCE_ID={$ARG_RECURRENCE_ID}"
    const val SESSION = "session/{$ARG_SESSION_ID}"

    fun planDetail(planId: Long): String = "plan/$planId"

    /** Nowy harmonogram planu. */
    fun planSchedule(planId: Long): String = "plan/$planId/schedule?$ARG_RECURRENCE_ID=$NO_ID"

    /** Zmiana istniejącej reguły harmonogramu. */
    fun planScheduleEdit(planId: Long, recurrenceId: Long): String =
        "plan/$planId/schedule?$ARG_RECURRENCE_ID=$recurrenceId"

    /** Wartość „brak” dla opcjonalnych argumentów numerycznych trasy. */
    const val NO_ID = 0L

    fun planDay(dayId: Long): String = "planDay/$dayId"

    /** Wybór zwykłego ćwiczenia do dnia. */
    fun exercisePicker(dayId: Long): String = "planDay/$dayId/pickExercise"

    /** Wybór alternatywy dla pozycji [anchorPlanExerciseId]. */
    fun alternativePicker(dayId: Long, anchorPlanExerciseId: Long): String =
        "planDay/$dayId/pickExercise?$ARG_ANCHOR_ID=$anchorPlanExerciseId"

    fun session(sessionId: Long): String = "session/$sessionId"

    /** Trasy najwyższego poziomu - na nich pokazujemy dolny pasek nawigacji. */
    val topLevel: Set<String> = setOf(HOME, EXERCISES, SETTINGS)
}
