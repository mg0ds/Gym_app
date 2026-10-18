package com.gymtracker.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Ślad po imporcie danych z pliku (M9).
 *
 * Wersje obu seedów z APK dalej trzymamy w `app_meta` - tam liczy się tylko
 * „która wersja jest w bazie”, a nie historia. Ta tabela odpowiada na inne
 * pytanie: **skąd** przyszły dane i **co z tego wyszło**. Import pliku
 * z pamięci urządzenia jest jednorazowy i bez śladu byłby nieodróżnialny od
 * danych wpisanych ręcznie, a plik odrzucony (brak `schema_version`, pusty
 * katalog) wracałby za każdym razem jako nowe, niezrozumiałe „nie udało się”.
 *
 * [statsJson] trzyma podsumowanie tego, co się stało - nie ma osobnej kolumny
 * na „status”, bo słownik wyników zmienia się razem z importem, a nie z bazą;
 * zapisujemy tam też powód odrzucenia, żeby dało się go pokazać bez reimportu.
 */
// `@Serializable`: wiersz jedzie także do kopii zapasowej (M9) - kopią jest
// JSON z nazwami pól takimi jak kolumny (`data/backup/BackupModels.kt`).
@Serializable
@Entity(
    tableName = "seed_imports",
    indices = [Index(value = ["seed_id", "checksum"], name = "idx_seed_imports_key")],
)
data class SeedImportEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Nazwa pliku (albo `assets:seed/exercises.json` dla danych z APK). */
    @ColumnInfo(name = "seed_id")
    val seedId: String,
    /** `generated_at` z pliku, o ile plik je deklaruje. */
    val version: String? = null,
    /** SHA-256 treści pliku - pozwala rozpoznać „ten sam plik jeszcze raz”. */
    val checksum: String? = null,
    @ColumnInfo(name = "imported_at")
    val importedAtEpochMs: Long,
    /** Podsumowanie importu albo powód odrzucenia (patrz [SeedImportStats]). */
    @ColumnInfo(name = "stats_json")
    val statsJson: String? = null,
)
