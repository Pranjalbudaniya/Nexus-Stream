package com.nexus.stream.data.torrent

import android.content.Context
import com.nexus.stream.di.IoDispatcher
import com.nexus.stream.domain.repository.TorrentCache
import com.nexus.stream.domain.repository.TorrentPrefsRepository
import com.nexus.stream.domain.usecase.CacheEntry
import com.nexus.stream.domain.usecase.cacheLimitBytes
import com.nexus.stream.domain.usecase.evictionPlan
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

internal fun computeFileSize(file: File): Long {
    if (!file.exists()) return 0L
    return if (file.isFile) {
        file.length()
    } else {
        file.listFiles()?.sumOf { computeFileSize(it) } ?: 0L
    }
}

internal fun listCacheEntries(root: File): List<CacheEntry> {
    val files = root.listFiles() ?: return emptyList()
    return files.map { child ->
        CacheEntry(
            name = child.name,
            sizeBytes = computeFileSize(child),
            lastUsedMs = child.lastModified(),
        )
    }
}

@Singleton
class TorrentCacheStore internal constructor(
    val root: File,
    private val limitBytes: suspend () -> Long,
    private val keepAfterStopProvider: suspend () -> Boolean,
    private val io: CoroutineDispatcher,
    private val nowMs: () -> Long,
) : TorrentCache {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        prefs: TorrentPrefsRepository,
        @IoDispatcher io: CoroutineDispatcher,
    ) : this(
        root = File(context.cacheDir, DIR_NAME),
        limitBytes = { cacheLimitBytes(prefs.cacheLimitGb.first()) },
        keepAfterStopProvider = { !prefs.clearCacheOnExit.first() },
        io = io,
        nowMs = System::currentTimeMillis,
    )

    private val activeHashes = Collections.synchronizedSet(mutableSetOf<String>())
    private val mutex = Mutex()

    fun markActive(hash: String) {
        activeHashes.add(hash.lowercase())
    }

    fun markInactive(hash: String) {
        activeHashes.remove(hash.lowercase())
    }

    suspend fun keepAfterStop(): Boolean = runCatching { keepAfterStopProvider() }.getOrDefault(false)

    suspend fun touch(hash: String) {
        withContext(io) {
            val child = File(root, hash.lowercase())
            if (child.exists()) {
                child.setLastModified(nowMs())
            }
        }
    }

    suspend fun onSessionStart() {
        withContext(io) {
            mutex.withLock {
                if (!root.exists()) {
                    root.mkdirs()
                }
                if (!keepAfterStop()) {
                    root.listFiles()?.forEach { it.deleteRecursively() }
                } else {
                    trimInternal()
                }
            }
        }
    }

    suspend fun onRemoved(
        hash: String,
        kept: Boolean,
    ) {
        withContext(io) {
            mutex.withLock {
                markInactive(hash)
                if (kept) {
                    val dir = File(root, hash.lowercase())
                    if (!dir.exists() || computeFileSize(dir) == 0L) {
                        dir.deleteRecursively()
                    } else {
                        dir.setLastModified(nowMs())
                        trimInternal()
                    }
                }
            }
        }
    }

    override suspend fun cacheSizeBytes(): Long =
        withContext(io) {
            mutex.withLock {
                if (!root.exists()) return@withContext 0L
                computeFileSize(root)
            }
        }

    override suspend fun clearCache() {
        withContext(io) {
            mutex.withLock {
                if (!root.exists()) return@withLock
                val inUse = activeHashes.toSet()
                root.listFiles()?.forEach { child ->
                    if (child.name.lowercase() !in inUse) {
                        child.deleteRecursively()
                    }
                }
            }
        }
    }

    private suspend fun trimInternal() {
        val entries = listCacheEntries(root)
        val limit = limitBytes()
        val inUse = activeHashes.toSet()
        val plan = evictionPlan(entries, limit, inUse)
        for (name in plan) {
            File(root, name).deleteRecursively()
        }
    }

    companion object {
        const val DIR_NAME = "torrents"
    }
}
