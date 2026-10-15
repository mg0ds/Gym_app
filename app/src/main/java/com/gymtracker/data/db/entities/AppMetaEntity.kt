package com.gymtracker.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Ogólne pary klucz-wartość dla stanu wewnętrznego aplikacji
 * (np. wersja zaimportowanego katalogu ćwiczeń, znaczniki porządków).
 * Nie trzymamy tu danych treningowych ani ustawień użytkownika.
 */
@Entity(tableName = "app_meta")
data class AppMetaEntity(
    @PrimaryKey
    @ColumnInfo(name = "meta_key")
    val key: String,
    @ColumnInfo(name = "meta_value")
    val value: String,
    @ColumnInfo(name = "updated_at")
    val updatedAtEpochMs: Long,
)
