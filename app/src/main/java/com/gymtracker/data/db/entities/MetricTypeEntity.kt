package com.gymtracker.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Wskaźnik karty postaci: waga, obwody, procent tłuszczu (M8).
 *
 * Dwie decyzje warte zapisania:
 *
 * 1. **Nazwa jest w bazie, a nie w słowniku tłumaczeń.** Użytkownik może
 *    wskaźnik przemianować, więc nazwa musi być edytowalna. Wbudowane wskaźniki
 *    mają jednak [builtinKey] - dopóki nikt ich nie zmienił, UI pokazuje nazwę
 *    przetłumaczoną, a po pierwszej zmianie nazwy klucz jest czyszczony i wygrywa
 *    wpis użytkownika (tak samo jak `muscle_group` z katalogu ćwiczeń).
 * 2. **Archiwizujemy, nie kasujemy.** Wskaźnik z historią pomiarów nie może
 *    zniknąć razem z nią - inaczej „schudłem 5 kg” przestałoby mieć dowód.
 *
 * `direction` mówi tylko, w którą stronę zmiana jest dobra dla **użytkownika**
 * (masa ciała bywa celem w obie strony), a nie co aplikacja uważa za zdrowe.
 * Wartości pilnuje `CHECK` w migracji - Room nie umie go wygenerować z encji.
 */
// `@Serializable`: wiersz jedzie także do kopii zapasowej (M9) - kopią jest
// JSON z nazwami pól takimi jak kolumny (`data/backup/BackupModels.kt`).
@Serializable
@Entity(tableName = "metric_types")
data class MetricTypeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    /** „kg”, „cm”, „%”; `null`, gdy jednostka jest opisowa (np. „razy w tygodniu”). */
    val unit: String? = null,
    /** Jedna z wartości `MetricDirection` (patrz `domain.model.BodyMetric`). */
    val direction: String,
    /** Opcjonalny cel - linia na wykresie i punkt odniesienia dla „zostało”. */
    @ColumnInfo(name = "target_num")
    val target: Double? = null,
    /** Klucz wbudowanego wskaźnika; `null` dla własnych i przemianowanych. */
    @ColumnInfo(name = "builtin_key")
    val builtinKey: String? = null,
    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,
    val position: Int,
    @ColumnInfo(name = "created_at")
    val createdAtEpochMs: Long,
)
