package com.gymtracker.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

/**
 * Indeks wyszukiwania dla [ExerciseEntity] (tabela wirtualna FTS4).
 *
 * `contentEntity` oznacza tabelę zewnętrznej treści: Room sam zakłada triggery
 * synchronizujące indeks przy każdym zapisie do `exercises`, więc nie musimy
 * pamiętać o ręcznej aktualizacji.
 *
 * Tokenizer `unicode61` składa wielkie litery i **usuwa znaki diakrytyczne**,
 * dzięki czemu „sztanga” i „sztangą” to dla wyszukiwania ten sam wyraz
 * (dotyczy też polskich znaków: ą, ć, ę, ł, ń, ó, ś, ź, ż).
 *
 * Tabela jest tylko do odczytu z punktu widzenia aplikacji - zapisujemy
 * wyłącznie do `exercises`.
 */
@Fts4(
    contentEntity = ExerciseEntity::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
)
@Entity(tableName = "exercises_fts")
data class ExerciseFtsEntity(
    val name: String,
    @ColumnInfo(name = "muscle_group")
    val muscleGroup: String?,
    val equipment: String?,
)
