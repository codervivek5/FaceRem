package com.facerem.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_cache")
data class AppCacheEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAtEpochMillis: Long,
)
