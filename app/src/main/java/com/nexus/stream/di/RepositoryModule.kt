package com.nexus.stream.di

import com.nexus.stream.data.local.prefs.SettingsRepositoryImpl
import com.nexus.stream.data.repository.AddonRepositoryImpl
import com.nexus.stream.domain.repository.AddonRepository
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
}
