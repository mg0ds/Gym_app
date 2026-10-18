package com.gymtracker.di

import android.content.Context
import androidx.room.Room
import com.gymtracker.data.db.AppDatabase
import com.gymtracker.data.db.DatabaseMigrations
import com.gymtracker.data.db.daos.AppMetaDao
import com.gymtracker.data.db.daos.BodyDao
import com.gymtracker.data.db.daos.CalendarDao
import com.gymtracker.data.db.daos.ExerciseDao
import com.gymtracker.data.db.daos.MediaDao
import com.gymtracker.data.db.daos.PlanDao
import com.gymtracker.data.db.daos.RestDao
import com.gymtracker.data.db.daos.SessionDao
import com.gymtracker.data.db.daos.SetLogDao
import com.gymtracker.data.db.daos.StatsDao
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
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(*DatabaseMigrations.ALL)
            .build()

    @Provides
    fun provideAppMetaDao(database: AppDatabase): AppMetaDao = database.appMetaDao()

    @Provides
    fun provideExerciseDao(database: AppDatabase): ExerciseDao = database.exerciseDao()

    @Provides
    fun providePlanDao(database: AppDatabase): PlanDao = database.planDao()

    @Provides
    fun provideSessionDao(database: AppDatabase): SessionDao = database.sessionDao()

    @Provides
    fun provideSetLogDao(database: AppDatabase): SetLogDao = database.setLogDao()

    @Provides
    fun provideRestDao(database: AppDatabase): RestDao = database.restDao()

    @Provides
    fun provideCalendarDao(database: AppDatabase): CalendarDao = database.calendarDao()

    @Provides
    fun provideStatsDao(database: AppDatabase): StatsDao = database.statsDao()

    @Provides
    fun provideMediaDao(database: AppDatabase): MediaDao = database.mediaDao()

    @Provides
    fun provideBodyDao(database: AppDatabase): BodyDao = database.bodyDao()
}
