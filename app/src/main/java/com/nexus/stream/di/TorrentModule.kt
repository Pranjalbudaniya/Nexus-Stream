package com.nexus.stream.di

import com.nexus.stream.data.torrent.JLibTorrentBackend
import com.nexus.stream.data.torrent.ServingTorrentEngine
import com.nexus.stream.data.torrent.TorrentBackend
import com.nexus.stream.data.torrent.TorrentFileLocator
import com.nexus.stream.domain.repository.TorrentEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface TorrentModule {
    @Binds
    @Singleton
    fun bindTorrentBackend(impl: JLibTorrentBackend): TorrentBackend

    @Binds
    @Singleton
    fun bindTorrentFileLocator(impl: JLibTorrentBackend): TorrentFileLocator

    @Binds
    @Singleton
    fun bindTorrentEngine(impl: ServingTorrentEngine): TorrentEngine
}
