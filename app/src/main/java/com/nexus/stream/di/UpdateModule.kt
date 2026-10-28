package com.nexus.stream.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.nexus.stream.data.update.ApkDownloaderImpl
import com.nexus.stream.data.update.ApkFileStoreImpl
import com.nexus.stream.data.update.ApkInstallerImpl
import com.nexus.stream.data.update.DefaultUpdateManager
import com.nexus.stream.data.update.GitHubReleaseSource
import com.nexus.stream.data.update.NetworkInfoImpl
import com.nexus.stream.data.update.PackageArchiveReaderImpl
import com.nexus.stream.data.update.StorageInfoImpl
import com.nexus.stream.data.update.UpdateDataStore
import com.nexus.stream.data.update.UpdatePrefsRepositoryImpl
import com.nexus.stream.domain.repository.ApkDownloader
import com.nexus.stream.domain.repository.ApkFileStore
import com.nexus.stream.domain.repository.ApkInstaller
import com.nexus.stream.domain.repository.Clock
import com.nexus.stream.domain.repository.NetworkInfo
import com.nexus.stream.domain.repository.PackageArchiveReader
import com.nexus.stream.domain.repository.ReleaseSource
import com.nexus.stream.domain.repository.StorageInfo
import com.nexus.stream.domain.repository.UpdateManager
import com.nexus.stream.domain.repository.UpdatePrefsRepository
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
interface UpdateBindingModule {
    @Binds
    @Singleton
    fun bindReleaseSource(impl: GitHubReleaseSource): ReleaseSource

    @Binds
    @Singleton
    fun bindApkDownloader(impl: ApkDownloaderImpl): ApkDownloader

    @Binds
    @Singleton
    fun bindApkFileStore(impl: ApkFileStoreImpl): ApkFileStore

    @Binds
    @Singleton
    fun bindPackageArchiveReader(impl: PackageArchiveReaderImpl): PackageArchiveReader

    @Binds
    @Singleton
    fun bindApkInstaller(impl: ApkInstallerImpl): ApkInstaller

    @Binds
    @Singleton
    fun bindNetworkInfo(impl: NetworkInfoImpl): NetworkInfo

    @Binds
    @Singleton
    fun bindStorageInfo(impl: StorageInfoImpl): StorageInfo

    @Binds
    @Singleton
    fun bindUpdatePrefsRepository(impl: UpdatePrefsRepositoryImpl): UpdatePrefsRepository

    @Binds
    @Singleton
    fun bindUpdateManager(impl: DefaultUpdateManager): UpdateManager
}

@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {
    @Provides
    @Singleton
    @UpdateDataStore
    fun provideUpdateDataStore(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = CoroutineScope(ioDispatcher + SupervisorJob()),
            produceFile = { context.preferencesDataStoreFile("update_prefs") },
        )

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock { System.currentTimeMillis() }
}
