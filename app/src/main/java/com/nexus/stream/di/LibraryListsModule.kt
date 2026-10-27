package com.nexus.stream.di

import com.nexus.stream.data.repository.HistoryRepositoryImpl
import com.nexus.stream.data.repository.WatchlistRepositoryImpl
import com.nexus.stream.domain.repository.HistoryRepository
import com.nexus.stream.domain.repository.WatchlistRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface LibraryListsModule {
    @Binds
    @Singleton
    fun bindWatchlistRepository(impl: WatchlistRepositoryImpl): WatchlistRepository

    @Binds
    @Singleton
    fun bindHistoryRepository(impl: HistoryRepositoryImpl): HistoryRepository

    companion object {
        @Provides
        @Singleton
        fun provideClock(): java.time.Clock = java.time.Clock.systemDefaultZone()

        @Provides
        @Singleton
        fun provideZoneId(): java.time.ZoneId = java.time.ZoneId.systemDefault()
    }
}
