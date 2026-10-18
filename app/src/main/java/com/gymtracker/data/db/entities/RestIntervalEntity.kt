package com.gymtracker.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Przerwa między seriami - jedno odliczanie, od zalogowania serii do powrotu
 * do pracy.
 *
 * Wiersz jest **źródłem prawdy o przerwach**: `set_logs.rest_after_s` to tylko
 * wygodna kopia dla wiersza serii, a statystyki (średnia, mediana, ostatnio)
 * liczymy z tej tabeli, bo tylko tutaj widać, **dlaczego** przerwa się
 * skończyła. Przerwa pominięta i przerwana nie mówi nic o formie, więc nie
 * wchodzi do średniej - inaczej „nie mam czasu na przerwę” zaniżałoby wynik.
 *
 * [startedAtEpochMs] i [plannedSeconds] wystarczą, żeby odtworzyć odliczanie po
 * restarcie procesu (ten sam wybór co w `sessions`: zegar ścienny, nie
 * monotoniczny - patrz `WorkoutTimerState`).
 *
 * [isAuto] odróżnia przerwę, która ruszyła sama po zalogowaniu serii, od
 * ręcznie uruchomionej - na razie zawsze `true`, ale schemat nie chcemy zmieniać
 * w M6, gdy dojdzie „start przerwy z karty ćwiczenia”.
 */
// `@Serializable`: wiersz jedzie także do kopii zapasowej (M9) - kopią jest
// JSON z nazwami pól takimi jak kolumny (`data/backup/BackupModels.kt`).
@Serializable
@Entity(
    tableName = "rest_intervals",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SetLogEntity::class,
            parentColumns = ["id"],
            childColumns = ["set_log_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["exercise_id", "started_at"], name = "idx_rest_exercise"),
        Index(value = ["session_id"], name = "idx_rest_session"),
        Index(value = ["set_log_id"], name = "idx_rest_set"),
    ],
)
data class RestIntervalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "exercise_id")
    val exerciseId: Long,
    @ColumnInfo(name = "session_id")
    val sessionId: Long? = null,
    @ColumnInfo(name = "set_log_id")
    val setLogId: Long? = null,
    @ColumnInfo(name = "started_at")
    val startedAtEpochMs: Long,
    /** `NULL` = odliczanie trwa (najwyżej jedno naraz). */
    @ColumnInfo(name = "ended_at")
    val endedAtEpochMs: Long? = null,
    /** Cel po korektach („+15 s”), w sekundach. */
    @ColumnInfo(name = "planned_s")
    val plannedSeconds: Int? = null,
    @ColumnInfo(name = "actual_s")
    val actualSeconds: Int? = null,
    /** Jedna z wartości `REASON_*`; `NULL`, dopóki przerwa trwa. */
    @ColumnInfo(name = "ended_reason")
    val endedReason: String? = null,
    @ColumnInfo(name = "is_auto")
    val isAuto: Boolean = false,
) {

    companion object {
        /** Przerwa dobiegła końca (alarm albo powrót do pracy po czasie). */
        const val REASON_COMPLETED = "completed"

        /** To samo, ale z „+15 s” - wydłużona świadomie. */
        const val REASON_EXTENDED = "extended"

        /** Użytkownik uznał, że przerwa nie jest potrzebna. */
        const val REASON_SKIPPED = "skipped"

        /** Przerwana bez decyzji o przerwie (pauza, koniec treningu, nowa przerwa). */
        const val REASON_CANCELLED = "cancelled"
    }
}
