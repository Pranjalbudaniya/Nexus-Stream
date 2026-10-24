package com.nexus.stream.di

import com.nexus.stream.data.repository.SourceMemoryRepositoryImpl
import com.nexus.stream.domain.repository.SourceMemoryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface SourceMemoryModule {
    @Binds
    @Singleton
    fun bindSourceMemoryRepository(impl: SourceMemoryRepositoryImpl): SourceMemoryRepository
}
