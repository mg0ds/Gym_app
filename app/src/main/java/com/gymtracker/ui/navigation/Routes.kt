package com.gymtracker.ui.navigation

/**
 * Trasy nawigacji. Ekrany katalogu ćwiczeń mają podane identyfikatory,
 * bo nawigacja Compose nie przekazuje obiektów - tylko argumenty trasy.
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

    /** Trasy najwyższego poziomu - na nich pokazujemy dolny pasek nawigacji. */
    val topLevel: Set<String> = setOf(HOME, EXERCISES, SETTINGS)
}
