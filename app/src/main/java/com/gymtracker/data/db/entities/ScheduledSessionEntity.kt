package com.gymtracker.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Zaplanowany trening w kalendarzu - **wpis**, nie reguła.
 *
 * Reguła ([RecurrenceEntity]) rozwija się do wpisów w momencie planowania, a
 * kalendarz czyta już tylko tę tabelę. Dzięki temu widok miesiąca to jedno
 * zapytanie po zakresie dat, a zmiana reguły to przeliczenie wpisów, a nie
 * porównywanie zbiorów.
 *
 * [title] jest **kopią** nazwy dnia z momentu planowania (tak jak w sesjach) -
 * wpis w kalendarzu zostaje czytelny także po zmianie nazwy dnia albo
 * usunięciu planu. Gdy plan zniknie, [planId] i [planDayId] stają się `NULL`
 * (`SET NULL`), ale sam wpis zostaje jako ślad w kalendarzu.
 *
 * Statusy ([status]):
 *
 * * `planned` - zaplanowany, jeszcze nie odbębniony,
 * * `done` - odbyty (ręcznie albo po zakończeniu powiązanej sesji),
 * * `skipped` - pominięty.
 *
 * Przeniesienie wpisu na inny dzień to zmiana [date] (nie tworzymy drugiego
 * wiersza ze statusem `moved`).
 *
 * `UNIQUE(date, plan_day_id)` pilnuje, żeby ten sam dzień planu nie trafił dwa
 * razy na tę samą datę (przy rotacji i planowaniu ręcznym to realne). SQLite
 * uznaje `NULL` za różne wartości, więc wpisy bez dnia planu (np. po usunięciu
 * planu) nie kolidują ze sobą.
 */
@Entity(
    tableName = "scheduled_sessions",
    foreignKeys = [
        ForeignKey(
            entity = PlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["plan_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = PlanDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["plan_day_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = RecurrenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["recurrence_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["date", "plan_day_id"], unique = true, name = "idx_sched_uniq"),
        Index(value = ["date", "status"], name = "idx_sched_date_status"),
        Index(value = ["plan_id"], name = "idx_sched_plan"),
        Index(value = ["plan_day_id"], name = "idx_sched_day"),
        Index(value = ["recurrence_id"], name = "idx_sched_recurrence"),
        Index(value = ["session_id"], name = "idx_sched_session"),
    ],
)
data class ScheduledSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Data jako ISO `YYYY-MM-DD` (bez stref - brak pułapek DST). */
    val date: String,
    @ColumnInfo(name = "plan_id")
    val planId: Long? = null,
    @ColumnInfo(name = "plan_day_id")
    val planDayId: Long? = null,
    @ColumnInfo(name = "recurrence_id")
    val recurrenceId: Long? = null,
    val title: String,
    val status: String,
    /** Realizacja: sesja rozpoczęta z tego wpisu (M4 domknie logowanie serii). */
    @ColumnInfo(name = "session_id")
    val sessionId: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAtEpochMs: Long,
) {
    companion object {
        const val STATUS_PLANNED = "planned"
        const val STATUS_DONE = "done"
        const val STATUS_SKIPPED = "skipped"
    }
}
