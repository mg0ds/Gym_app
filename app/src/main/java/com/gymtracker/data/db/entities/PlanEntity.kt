package com.gymtracker.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Plan treningowy (wbudowany z seeda albo ułożony przez użytkownika).
 *
 * Klucz naturalny to [externalId] (slug z danych startowych) - dzięki niemu
 * ponowny import nie tworzy duplikatów i nie nadpisuje zmian użytkownika.
 * Plany dodane ręcznie mają [externalId] = null.
 *
 * Dni ([PlanDayEntity]) i ćwiczenia dni ([PlanExerciseEntity]) leżą w osobnych
 * tabelach; usunięcie planu kasuje je kaskadowo, więc nie zostają sieroty.
 */
@Entity(
    tableName = "plans",
    indices = [Index(value = ["external_id"], unique = true, name = "idx_plans_ext")],
)
data class PlanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "external_id")
    val externalId: String? = null,
    val name: String,
    val description: String? = null,
    @ColumnInfo(name = "source_url")
    val sourceUrl: String? = null,
    val author: String? = null,
    /** Plan „w użyciu” - podpowiedź przy planowaniu treningów (M3). */
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = false,
    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAtEpochMs: Long,
)
