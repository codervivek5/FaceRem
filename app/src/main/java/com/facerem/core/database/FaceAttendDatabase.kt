package com.facerem.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [AppCacheEntity::class], version = 1, exportSchema = true)
abstract class FaceAttendDatabase : RoomDatabase() {
    abstract fun appCacheDao(): AppCacheDao
}
