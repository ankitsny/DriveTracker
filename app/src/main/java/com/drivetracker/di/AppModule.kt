package com.drivetracker.di

import android.content.Context
import androidx.room.Room
import com.drivetracker.data.local.DriveDatabase
import com.drivetracker.data.local.DriveSessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DriveDatabase {
        return Room.databaseBuilder(
            context,
            DriveDatabase::class.java,
            DriveDatabase.DATABASE_NAME
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    @Singleton
    fun provideSessionDao(database: DriveDatabase): DriveSessionDao {
        return database.driveSessionDao()
    }
}
