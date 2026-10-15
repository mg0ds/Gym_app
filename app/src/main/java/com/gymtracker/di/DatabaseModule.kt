package com.gymtracker.di

import android.content.Context
import androidx.room.Room
import com.gymtracker.data.db.AppDatabase
import com.gymtracker.data.db.DatabaseMigrations
import com.gymtracker.data.db.daos.AppMetaDao
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
}
