package com.gymtracker.data.db.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.gymtracker.data.db.entities.AppMetaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppMetaDao {

    @Query("SELECT meta_value FROM app_meta WHERE meta_key = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun upsert(entry: AppMetaEntity)

    @Query("SELECT * FROM app_meta ORDER BY meta_key")
    fun observeAll(): Flow<List<AppMetaEntity>>

    @Query("DELETE FROM app_meta WHERE meta_key = :key")
    suspend fun delete(key: String)

    @Query("SELECT COUNT(*) FROM app_meta")
    suspend fun count(): Int
}
