package com.gymtracker.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Pomiar wskaźnika z karty postaci (M8).
 *
 * `UNIQUE(metric_type_id, date)` to nie ozdobnik: pomiar mierzy stan dnia, więc
 * drugi wpis z tą samą datą byłby pytaniem „który jest prawdziwy?”. Repozytorium
 * nadpisuje wtedy istniejący wiersz, a użytkownik dostaje edycję zamiast
 * duplikatu.
 *
 * [date] jest tekstem `YYYY-MM-DD` (jak w kalendarzu), a [measuredAtEpochMs]
 * pełnym czasem - po nim sortuje się historia, gdy tego samego dnia poprawiamy
 * wartość.
 *
 * `CASCADE` po wskaźniku: pomiar bez wskaźnika nie znaczy nic. Użytkownik i tak
 * nie kasuje wskaźników (archiwizuje), więc kaskada sprząta po testach
 * i ewentualnych operacjach porządkowych.
 */
// `@Serializable`: wiersz jedzie także do kopii zapasowej (M9) - kopią jest
// JSON z nazwami pól takimi jak kolumny (`data/backup/BackupModels.kt`).
@Serializable
@Entity(
    tableName = "body_entries",
    foreignKeys = [
        ForeignKey(
            entity = MetricTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["metric_type_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    // Jednego indeksu wystarczy za dwoje: jest unikalny (pilnuje „jeden pomiar
    // na dzień”) i obsługuje też zwykłe zapytania „pomiary tego wskaźnika”,
    // bo liczy się w nim od lewej kolumny. Drugi, nieunikalny indeks na tych
    // samych kolumnach nic by nie dodał poza miejscem na dysku.
    indices = [
        Index(value = ["metric_type_id", "date"], name = "idx_body_uniq", unique = true),
    ],
)
data class BodyEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "metric_type_id")
    val metricTypeId: Long,
    /** Dzień pomiaru w zapisie `YYYY-MM-DD`. */
    val date: String,
    @ColumnInfo(name = "measured_at")
    val measuredAtEpochMs: Long,
    /** Wartość w jednostce wskaźnika; karta postaci pokazuje wykresy, więc liczbowa. */
    @ColumnInfo(name = "value_num")
    val value: Double,
    val note: String? = null,
)
