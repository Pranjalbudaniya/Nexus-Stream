package com.nexus.stream.di

import com.nexus.stream.data.stream.LoopbackStreamProxy
import com.nexus.stream.domain.repository.StreamProxy
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface StreamProxyModule {
    @Binds
    @Singleton
    fun bindStreamProxy(impl: LoopbackStreamProxy): StreamProxy
}
