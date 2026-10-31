package com.nexus.stream.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.nexus.stream.data.download.DefaultDownloadManager
import com.nexus.stream.data.download.DownloadClockImpl
import com.nexus.stream.data.download.DownloadDataStore
import com.nexus.stream.data.download.DownloadFileStoreImpl
import com.nexus.stream.data.download.DownloadNetworkImpl
import com.nexus.stream.data.download.DownloadNotifierImpl
import com.nexus.stream.data.download.DownloadPrefsRepositoryImpl
import com.nexus.stream.data.download.DownloadRepositoryImpl
import com.nexus.stream.data.download.DownloadServiceControllerImpl
import com.nexus.stream.data.download.HttpDownloaderImpl
import com.nexus.stream.data.download.MediaStoreGalleryExporter
import com.nexus.stream.data.download.TorrentDownloaderImpl
import com.nexus.stream.data.torrent.JLibTorrentBackend
import com.nexus.stream.data.torrent.TorrentDownloadBackend
import com.nexus.stream.domain.repository.DownloadClock
import com.nexus.stream.domain.repository.DownloadFileStore
import com.nexus.stream.domain.repository.DownloadManager
import com.nexus.stream.domain.repository.DownloadNetwork
import com.nexus.stream.domain.repository.DownloadNotifier
import com.nexus.stream.domain.repository.DownloadPlatformComponents
import com.nexus.stream.domain.repository.DownloadPrefsRepository
import com.nexus.stream.domain.repository.DownloadRepository
import com.nexus.stream.domain.repository.DownloadServiceController
import com.nexus.stream.domain.repository.GalleryExporter
import com.nexus.stream.domain.repository.HttpDownloader
import com.nexus.stream.domain.repository.StorageInfo
import com.nexus.stream.domain.repository.TorrentDownloader
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface DownloadBindingModule {
    @Binds
    @Singleton
    fun bindDownloadRepository(impl: DownloadRepositoryImpl): DownloadRepository

    @Binds
    @Singleton
    fun bindDownloadPrefsRepository(impl: DownloadPrefsRepositoryImpl): DownloadPrefsRepository

    @Binds
    @Singleton
    fun bindHttpDownloader(impl: HttpDownloaderImpl): HttpDownloader

    @Binds
    @Singleton
    fun bindTorrentDownloader(impl: TorrentDownloaderImpl): TorrentDownloader

    @Binds
    @Singleton
    fun bindTorrentDownloadBackend(impl: JLibTorrentBackend): TorrentDownloadBackend

    @Binds
    @Singleton
    fun bindDownloadFileStore(impl: DownloadFileStoreImpl): DownloadFileStore

    @Binds
    @Singleton
    fun bindDownloadNetwork(impl: DownloadNetworkImpl): DownloadNetwork

    @Binds
    @Singleton
    fun bindDownloadNotifier(impl: DownloadNotifierImpl): DownloadNotifier

    @Binds
    @Singleton
    fun bindDownloadServiceController(impl: DownloadServiceControllerImpl): DownloadServiceController

    @Binds
    @Singleton
    fun bindDownloadClock(impl: DownloadClockImpl): DownloadClock

    @Binds
    @Singleton
    fun bindDownloadManager(impl: DefaultDownloadManager): DownloadManager
}

@Module
@InstallIn(SingletonComponent::class)
interface GalleryBindingModule {
    @Binds
    @Singleton
    fun bindGalleryExporter(impl: MediaStoreGalleryExporter): GalleryExporter
}

@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {
    @Provides
    @Singleton
    @DownloadDataStore
    fun provideDownloadDataStore(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = CoroutineScope(ioDispatcher + SupervisorJob()),
            produceFile = { context.preferencesDataStoreFile("download_prefs") },
        )
}
