package com.nexus.stream.di

import com.nexus.stream.data.AppInfoProviderImpl
import com.nexus.stream.domain.repository.AppInfoProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface AppInfoModule {
    @Binds
    @Singleton
    fun bindAppInfoProvider(impl: AppInfoProviderImpl): AppInfoProvider
}
