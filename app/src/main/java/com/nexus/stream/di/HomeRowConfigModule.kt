package com.nexus.stream.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.nexus.stream.data.repository.HomeRowConfigRepositoryImpl
import com.nexus.stream.data.repository.HomeRowsDataStore
import com.nexus.stream.domain.repository.HomeRowConfigRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface HomeRowConfigModule {
    @Binds
    @Singleton
    fun bindHomeRowConfigRepository(impl: HomeRowConfigRepositoryImpl): HomeRowConfigRepository

    companion object {
        @Provides
        @Singleton
        @HomeRowsDataStore
        fun provideHomeRowsDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
                produceFile = { context.preferencesDataStoreFile("home_rows") },
            )
    }
}
