package com.gymtracker.domain.repository

import com.gymtracker.domain.model.ArchiveImpact
import com.gymtracker.domain.model.Exercise
import com.gymtracker.domain.model.ExerciseDraft
import com.gymtracker.domain.model.ExerciseUsage
import kotlinx.coroutines.flow.Flow

/**
 * Katalog ćwiczeń. Warstwa UI nie wie nic o Room, FTS ani JSON-ach -
 * dostaje gotowe modele domenowe.
 */
interface ExerciseRepository {

    /**
     * Lista ćwiczeń do pokazania: [query] to surowy tekst z wyszukiwarki
     * (obsługujemy polskie znaki i szukanie po prefiksie), [muscleGroup]
     * i [favouritesOnly] to filtry.
     */
    fun observeExercises(
        query: String = "",
        muscleGroup: String? = null,
        favouritesOnly: Boolean = false,
    ): Flow<List<Exercise>>

    fun observeExercise(id: Long): Flow<Exercise?>

    /** Partie mięśniowe obecne w katalogu - do filtrów na liście. */
    fun observeMuscleGroups(): Flow<List<String>>

    fun observeActiveCount(): Flow<Int>

    /**
     * Gdzie ćwiczenie jest używane (plany, historyczne treningi).
     *
     * Obserwujemy na żywo, bo liczba zmienia się także poza tym ekranem: gdy
     * użytkownik w międzyczasie usunie ćwiczenie z planu, ostrzeżenie przy
     * usuwaniu ma zniknąć, a nie straszyć nieaktualnym stanem.
     */
    fun observeUsage(id: Long): Flow<ExerciseUsage>

    suspend fun getExercise(id: Long): Exercise?

    /** Dodaje własne ćwiczenie i zwraca jego id. */
    suspend fun addExercise(draft: ExerciseDraft): Long

    /** Zapisuje zmiany; `null`, gdy ćwiczenie zniknęło w międzyczasie. */
    suspend fun updateExercise(id: Long, draft: ExerciseDraft)

    suspend fun setFavourite(id: Long, isFavourite: Boolean)

    /**
     * Usuwa ćwiczenie z katalogu.
     *
     * W praktyce **archiwizuje** wiersz (`is_archived = 1`): ćwiczenie znika
     * z listy i z wyszukiwania, ale jego pozycje w planach i historia treningów
     * zostają czytelne. Skasowanie wiersza zabrałoby je kaskadą - a trening,
     * którego nie da się już odtworzyć, to nie „porządek w katalogu”.
     */
    suspend fun archiveExercise(id: Long)

    /**
     * Archiwum: ćwiczenia zdjęte z katalogu, ale wciąż obecne w planach
     * i historii (M9). Kolejność jak w katalogu - po nazwie.
     */
    fun observeArchivedExercises(): Flow<List<Exercise>>

    /** Liczba ćwiczeń w archiwum - etykieta wejścia do archiwum. */
    fun observeArchivedCount(): Flow<Int>

    /**
     * Ile zniknie, gdy archiwum zostanie opróżnione.
     *
     * Pokazujemy to **przed** kasowaniem, bo archiwum nie jest koszem na pliki:
     * razem z ćwiczeniem znikają jego pozycje w planach i wpisy w historii
     * treningów - a tego nie da się już odtworzyć.
     */
    fun observeArchiveImpact(): Flow<ArchiveImpact>

    /** Przywraca ćwiczenie do katalogu (odwrotność [archiveExercise]). */
    suspend fun restoreExercise(id: Long)

    /**
     * Kasuje ćwiczenie **na stałe**, razem z pozycjami w planach i wpisami
     * w historii. Tylko z archiwum, po potwierdzeniu pokazującym [observeArchiveImpact].
     */
    suspend fun deleteExercisePermanently(id: Long)

    /**
     * Opróżnia całe archiwum. Zwraca liczbę skasowanych ćwiczeń, żeby ekran mógł
     * powiedzieć, co się stało, a nie tylko zniknąć z listą.
     */
    suspend fun emptyArchive(): Int
}
