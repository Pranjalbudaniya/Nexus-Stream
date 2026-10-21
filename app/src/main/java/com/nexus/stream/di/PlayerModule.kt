package com.nexus.stream.di

import com.nexus.stream.data.player.LibVlcPlayerFactory
import com.nexus.stream.data.player.PlayerControllerFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface PlayerModule {
    @Binds
    @Singleton
    fun bindPlayerControllerFactory(impl: LibVlcPlayerFactory): PlayerControllerFactory
}
