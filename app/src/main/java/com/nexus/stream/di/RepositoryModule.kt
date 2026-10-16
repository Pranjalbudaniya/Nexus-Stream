package com.nexus.stream.di

import com.nexus.stream.data.local.prefs.AppStateRepositoryImpl
import com.nexus.stream.data.local.prefs.SettingsRepositoryImpl
import com.nexus.stream.data.repository.AddonRepositoryImpl
import com.nexus.stream.data.repository.LibraryRepositoryImpl
import com.nexus.stream.data.repository.MetaRepositoryImpl
import com.nexus.stream.data.repository.ProgressRepositoryImpl
import com.nexus.stream.domain.repository.AddonRepository
import com.nexus.stream.domain.repository.AppStateRepository
import com.nexus.stream.domain.repository.LibraryRepository
import com.nexus.stream.domain.repository.MetaRepository
import com.nexus.stream.domain.repository.ProgressRepository
import com.nexus.stream.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {
    @Binds
    @Singleton
    fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    fun bindAddonRepository(impl: AddonRepositoryImpl): AddonRepository

    @Binds
    @Singleton
    fun bindAppStateRepository(impl: AppStateRepositoryImpl): AppStateRepository

    @Binds
    @Singleton
    fun bindLibraryRepository(impl: LibraryRepositoryImpl): LibraryRepository

    @Binds
    @Singleton
    fun bindProgressRepository(impl: ProgressRepositoryImpl): ProgressRepository

    @Binds
    @Singleton
    fun bindMetaRepository(impl: MetaRepositoryImpl): MetaRepository
}
