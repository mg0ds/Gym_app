package com.gymtracker.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Reguła powtarzania: „co [intervalDays] dni, od [startDate] do [endDate],
 * (opcjonalnie) tylko w te dni tygodnia”.
 *
 * Reguła jest **przepisem**, nie listą dat: daty rozwijamy do
 * `scheduled_sessions` ([ScheduledSessionEntity]) i to one trafiają do
 * kalendarza. Dzięki temu zapytania kalendarza są trywialne, a zmiana reguły to
 * przeliczenie, a nie porównywanie zbiorów.
 *
 * [planDayId] = `null` przy planie z wieloma dniami oznacza **rotację**
 * (A → B → C → A…): kolejne daty dostają kolejne dni planu w kolejności
 * z `plan_days.position`. Gdy [planDayId] jest ustawione, każda data dostaje
 * ten sam dzień.
 *
 * Usunięcie planu (albo dnia) kasuje regułę kaskadą - reguła bez planu nie ma
 * czego rozgłaszać. Wpisy już rozplanowane zostają, ale tracą wskazanie
 * (`scheduled_sessions.plan_day_id` jest `SET NULL`).
 */
// `@Serializable`: wiersz jedzie także do kopii zapasowej (M9) - kopią jest
// JSON z nazwami pól takimi jak kolumny (`data/backup/BackupModels.kt`).
@Serializable
@Entity(
    tableName = "recurrences",
    foreignKeys = [
        ForeignKey(
            entity = PlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["plan_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PlanDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["plan_day_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["plan_id"], name = "idx_recurrences_plan"),
        Index(value = ["plan_day_id"], name = "idx_recurrences_day"),
    ],
)
data class RecurrenceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "plan_id")
    val planId: Long? = null,
    @ColumnInfo(name = "plan_day_id")
    val planDayId: Long? = null,
    /** Data pierwsza i ostatnia jako ISO `YYYY-MM-DD` (bez stref - brak pułapek DST). */
    @ColumnInfo(name = "start_date")
    val startDate: String,
    @ColumnInfo(name = "end_date")
    val endDate: String? = null,
    /** „co ile dni”; minimum 1 pilnuje walidacja w domenie. */
    @ColumnInfo(name = "interval_days")
    val intervalDays: Int,
    /** Bitmaska dni tygodnia: bit 0 = poniedziałek … bit 6 = niedziela; `null` = każdy. */
    @ColumnInfo(name = "weekdays_mask")
    val weekdaysMask: Int? = null,
    @ColumnInfo(name = "created_at")
    val createdAtEpochMs: Long,
)
