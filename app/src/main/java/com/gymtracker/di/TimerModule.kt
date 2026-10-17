package com.gymtracker.di

import android.content.Context
import com.gymtracker.domain.repository.SessionRepository
import com.gymtracker.domain.time.TimeProvider
import com.gymtracker.timer.RestTimer
import com.gymtracker.timer.WorkoutTimer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Zegar treningu potrzebuje kontekstu (start usługi i notyfikacja), dlatego
 * powstaje w module - tak samo jak baza i DataStore.
 */
@Module
@InstallIn(SingletonComponent::class)
object TimerModule {

    @Provides
    @Singleton
    fun provideWorkoutTimer(
        @ApplicationContext context: Context,
        sessions: SessionRepository,
        timeProvider: TimeProvider,
        restTimer: RestTimer,
    ): WorkoutTimer = WorkoutTimer(context, sessions, timeProvider, restTimer)
}
