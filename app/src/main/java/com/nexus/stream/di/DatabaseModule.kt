package com.nexus.stream.di

import android.content.Context
import androidx.room.Room
import com.nexus.stream.data.local.db.NexusDatabase
import com.nexus.stream.data.local.db.RoomTransactionRunner
import com.nexus.stream.data.local.db.TransactionRunner
import com.nexus.stream.data.local.db.dao.AddonDao
import com.nexus.stream.data.local.db.dao.LibraryDao
import com.nexus.stream.data.local.db.dao.MetaCacheDao
import com.nexus.stream.data.local.db.dao.ProgressDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val DATABASE_NAME = "nexus.db"

    @Provides
    @Singleton
    fun provideNexusDatabase(
        @ApplicationContext context: Context,
    ): NexusDatabase =
        Room
            .databaseBuilder(
                context,
                NexusDatabase::class.java,
                DATABASE_NAME,
            ).build()

    @Provides
    fun provideAddonDao(database: NexusDatabase): AddonDao = database.addonDao()

    @Provides
    fun provideLibraryDao(database: NexusDatabase): LibraryDao = database.libraryDao()

    @Provides
    fun provideProgressDao(database: NexusDatabase): ProgressDao = database.progressDao()

    @Provides
    fun provideMetaCacheDao(database: NexusDatabase): MetaCacheDao = database.metaCacheDao()

    @Provides
    @Singleton
    fun provideTransactionRunner(database: NexusDatabase): TransactionRunner = RoomTransactionRunner(database)
}
