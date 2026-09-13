package com.facerem.core.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FaceAttendDatabase =
        Room.databaseBuilder(context, FaceAttendDatabase::class.java, "faceattend.db").build()

    @Provides
    fun provideAppCacheDao(database: FaceAttendDatabase): AppCacheDao = database.appCacheDao()
}
