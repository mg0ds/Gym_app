package com.gymtracker.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Dzień planu (np. „Workout A”). Kolejność wyznacza [position] - to ona się
 * zmienia przy przeciąganiu dnia w planie.
 *
 * `UNIQUE(plan_id, position)` pilnuje, żeby dwa dni nie miały tego samego
 * numeru; przestawianie robi się więc w dwóch krokach (najpierw wartości
 * tymczasowe, potem docelowe) - patrz `PlanDao.reorderDays`.
 */
@Entity(
    tableName = "plan_days",
    foreignKeys = [
        ForeignKey(
            entity = PlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["plan_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["plan_id", "position"], unique = true, name = "idx_plan_days_position")],
)
data class PlanDayEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "plan_id")
    val planId: Long,
    val name: String,
    /** 0 = poniedziałek ... 6 = niedziela; `null`, gdy dzień jest „luźny”. */
    @ColumnInfo(name = "day_of_week")
    val dayOfWeek: Int? = null,
    val position: Int,
    val note: String? = null,
)
