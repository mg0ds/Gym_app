package com.gymtracker.di

import com.gymtracker.data.media.AndroidMediaLinker
import com.gymtracker.domain.media.MediaLinker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Systemowa strona załączników (M7): czytanie metadanych pliku z galerii
 * i robienie własnych miniatur. Interfejs jest w domenie, implementacja
 * (`ContentResolver`, `MediaMetadataRetriever`) w warstwie danych.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MediaModule {

    @Binds
    @Singleton
    abstract fun bindMediaLinker(impl: AndroidMediaLinker): MediaLinker
}
