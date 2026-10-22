package com.nexus.stream.di

import com.nexus.stream.data.player.AndroidAudioFocusController
import com.nexus.stream.data.player.AndroidSubtitleFileImporter
import com.nexus.stream.data.player.AudioFocusController
import com.nexus.stream.data.player.LibVlcPlayerFactory
import com.nexus.stream.data.player.PlayerControllerFactory
import com.nexus.stream.data.player.SubtitleFileImporter
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

    @Binds
    @Singleton
    fun bindAudioFocusController(impl: AndroidAudioFocusController): AudioFocusController

    @Binds
    @Singleton
    fun bindSubtitleFileImporter(impl: AndroidSubtitleFileImporter): SubtitleFileImporter
}
