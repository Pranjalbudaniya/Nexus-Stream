package com.nexus.stream.di

import com.nexus.stream.data.torrent.AndroidServiceLauncher
import com.nexus.stream.data.torrent.CacheLedger
import com.nexus.stream.data.torrent.CacheLedgerImpl
import com.nexus.stream.data.torrent.CacheRetentionCoordinator
import com.nexus.stream.data.torrent.ConnectivityNetworkStatusProvider
import com.nexus.stream.data.torrent.GatedTorrentEngine
import com.nexus.stream.data.torrent.JLibTorrentBackend
import com.nexus.stream.data.torrent.NetworkStatusProvider
import com.nexus.stream.data.torrent.RefCountedKeepAlive
import com.nexus.stream.data.torrent.ServiceLauncher
import com.nexus.stream.data.torrent.TorrentBackend
import com.nexus.stream.data.torrent.TorrentCacheFiles
import com.nexus.stream.data.torrent.TorrentCacheStore
import com.nexus.stream.data.torrent.TorrentFileLocator
import com.nexus.stream.data.torrent.TorrentKeepAlive
import com.nexus.stream.data.torrent.TorrentSessionControl
import com.nexus.stream.domain.repository.CacheCompletion
import com.nexus.stream.domain.repository.TorrentCache
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
    fun bindCacheLedger(impl: CacheLedgerImpl): CacheLedger

    @Binds
    @Singleton
    fun bindCacheCompletion(impl: CacheRetentionCoordinator): CacheCompletion

    @Binds
    @Singleton
    fun bindTorrentCacheFiles(impl: TorrentCacheStore): TorrentCacheFiles

    @Binds
    @Singleton
    fun bindTorrentCache(impl: TorrentCacheStore): TorrentCache

    @Binds
    @Singleton
    fun bindTorrentBackend(impl: JLibTorrentBackend): TorrentBackend

    @Binds
    @Singleton
    fun bindTorrentFileLocator(impl: JLibTorrentBackend): TorrentFileLocator

    @Binds
    @Singleton
    fun bindTorrentEngine(impl: GatedTorrentEngine): TorrentEngine

    @Binds
    @Singleton
    fun bindTorrentSessionControl(impl: GatedTorrentEngine): TorrentSessionControl

    @Binds
    @Singleton
    fun bindTorrentKeepAlive(impl: RefCountedKeepAlive): TorrentKeepAlive

    @Binds
    @Singleton
    fun bindServiceLauncher(impl: AndroidServiceLauncher): ServiceLauncher

    @Binds
    @Singleton
    fun bindNetworkStatusProvider(impl: ConnectivityNetworkStatusProvider): NetworkStatusProvider
}
