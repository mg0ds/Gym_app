package com.gymtracker.di

import com.gymtracker.data.repo.CalendarRepositoryImpl
import com.gymtracker.data.repo.ExerciseRepositoryImpl
import com.gymtracker.data.repo.PlanRepositoryImpl
import com.gymtracker.data.repo.RestRepositoryImpl
import com.gymtracker.data.repo.SessionRepositoryImpl
import com.gymtracker.data.seed.AssetSeedSource
import com.gymtracker.data.seed.SeedSource
import com.gymtracker.domain.repository.CalendarRepository
import com.gymtracker.domain.repository.ExerciseRepository
import com.gymtracker.domain.repository.PlanRepository
import com.gymtracker.domain.repository.RestRepository
import com.gymtracker.domain.repository.SessionRepository
import com.gymtracker.domain.time.SystemTimeProvider
import com.gymtracker.domain.time.TimeProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindExerciseRepository(impl: ExerciseRepositoryImpl): ExerciseRepository

    @Binds
    @Singleton
    abstract fun bindPlanRepository(impl: PlanRepositoryImpl): PlanRepository

    @Binds
    @Singleton
    abstract fun bindSessionRepository(impl: SessionRepositoryImpl): SessionRepository

    @Binds
    @Singleton
    abstract fun bindRestRepository(impl: RestRepositoryImpl): RestRepository

    @Binds
    @Singleton
    abstract fun bindCalendarRepository(impl: CalendarRepositoryImpl): CalendarRepository

    @Binds
    @Singleton
    abstract fun bindSeedSource(impl: AssetSeedSource): SeedSource

    @Binds
    @Singleton
    abstract fun bindTimeProvider(impl: SystemTimeProvider): TimeProvider
}
