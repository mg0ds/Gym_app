package com.gymtracker.ui.navigation

/**
 * Trasy nawigacji. Ekrany mają identyfikatory w adresie, bo nawigacja Compose
 * nie przekazuje obiektów - tylko argumenty trasy.
 */
object Routes {

    const val HOME = "home"
    const val EXERCISES = "exercises"
    const val SETTINGS = "settings"
    const val HISTORY = "history"

    /** Nazwa argumentu z identyfikatorem ćwiczenia (typ `Long`). */
    const val ARG_EXERCISE_ID = "exerciseId"

    const val EXERCISE_NEW = "exercise/new"
    const val EXERCISE_DETAIL = "exercise/{$ARG_EXERCISE_ID}"
    const val EXERCISE_EDIT = "exercise/{$ARG_EXERCISE_ID}/edit"
    const val EXERCISE_STATS = "exercise/{$ARG_EXERCISE_ID}/stats"

    /**
     * Archiwum ćwiczeń (M9). Trasa jest dłuższa niż [EXERCISES], a nie krótsza
     * („archive”), żeby widać było, gdzie ten ekran wisi - i żeby nie kolidowała
     * z trasami pojedynczego ćwiczenia (`exercise/...`).
     */
    const val EXERCISE_ARCHIVE = "exercises/archive"

    /** Podgląd załącznika (M7) - zdjęcia, filmu albo linku. */
    const val ARG_MEDIA_ID = "mediaId"
    const val EXERCISE_MEDIA = "exercise/{$ARG_EXERCISE_ID}/media/{$ARG_MEDIA_ID}"

    // --- karta postaci (M8) ---

    /** Wskaźniki, pomiary i wykres zmian. */
    const val BODY = "body"

    /** Lista wskaźników: dodawanie, poprawianie i archiwizacja. */
    const val BODY_METRICS = "body/metrics"

    fun exerciseDetail(exerciseId: Long): String = "exercise/$exerciseId"

    fun exerciseEdit(exerciseId: Long): String = "exercise/$exerciseId/edit"

    /** Historia i statystyki ćwiczenia (M6). */
    fun exerciseStats(exerciseId: Long): String = "exercise/$exerciseId/stats"

    /** Podgląd załącznika ćwiczenia (M7). */
    fun exerciseMedia(exerciseId: Long, mediaId: Long): String =
        "exercise/$exerciseId/media/$mediaId"

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

    /**
     * Wybór ćwiczenia do **trwającego** treningu - dla treningu „od zera”
     * (bez planu) i dla dopisania czegoś w trakcie. Trasa jest pod sesją, a nie
     * pod dniem planu, bo wynik idzie do sesji, nie do planu.
     */
    const val SESSION_EXERCISE_PICKER = "session/{$ARG_SESSION_ID}/pickExercise"

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

    /** Wybór ćwiczenia do trwającego treningu ([SESSION_EXERCISE_PICKER]). */
    fun sessionExercisePicker(sessionId: Long): String = "session/$sessionId/pickExercise"

    /** Trasy najwyższego poziomu - na nich pokazujemy dolny pasek nawigacji. */
    val topLevel: Set<String> = setOf(HOME, EXERCISES, HISTORY, BODY, SETTINGS)
}
