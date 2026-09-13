package com.facerem.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface AppCacheDao {
    @Query("SELECT * FROM app_cache WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): AppCacheEntity?

    @Upsert
    suspend fun upsert(entry: AppCacheEntity)

    @Query("DELETE FROM app_cache WHERE `key` = :key")
    suspend fun delete(key: String)
}
