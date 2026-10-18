package com.gymtracker.data.db.rows

import androidx.room.ColumnInfo

/**
 * Dzień planu razem z nazwą planu i liczbą ćwiczeń - wiersz listy wyboru
 * treningu w kalendarzu (JOIN planów, podzapytanie na liczbę pozycji).
 */
data class PlanDayOptionRow(
    @ColumnInfo(name = "day_id")
    val dayId: Long,
    @ColumnInfo(name = "day_name")
    val dayName: String,
    @ColumnInfo(name = "plan_name")
    val planName: String,
    @ColumnInfo(name = "exercise_count")
    val exerciseCount: Int,
)
