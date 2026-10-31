package com.nexus.stream.data.torrent

import android.content.Context
import android.system.Os
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
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

private const val BYTES_PER_BLOCK: Long = 512L
private const val REGISTRY_FILE = ".nexus_files"

internal fun diskBytes(file: File): Long =
    runCatching {
        Os.lstat(file.path).st_blocks * BYTES_PER_BLOCK
    }.getOrDefault(0L)

internal fun computeFileSize(
    file: File,
    fileBytes: (File) -> Long,
): Long {
    if (!file.exists()) return 0L
    return if (file.isFile) {
        fileBytes(file)
    } else {
        file.listFiles()?.sumOf { computeFileSize(it, fileBytes) } ?: 0L
    }
}

internal fun listCacheEntries(
    root: File,
    fileBytes: (File) -> Long,
): List<CacheEntry> {
    val files = root.listFiles() ?: return emptyList()
    return files.map { child ->
        CacheEntry(
            name = child.name,
            sizeBytes = computeFileSize(child, fileBytes),
            lastUsedMs = child.lastModified(),
        )
    }
}

private fun isDeletableEmptyParent(
    parent: File?,
    dir: File,
): Boolean {
    if (parent == null || parent == dir) return false
    return parent.isDirectory && parent.listFiles()?.isEmpty() == true
}

internal class DefaultTorrentCacheFiles(
    val root: File,
    private val io: CoroutineDispatcher,
    private val fileBytes: (File) -> Long,
) : TorrentCacheFiles {
    private val fileRegistry = ConcurrentHashMap<String, ConcurrentHashMap<Int, Pair<String, Long>>>()

    override fun registerFile(
        hash: String,
        fileIdx: Int,
        relativePath: String,
        sizeBytes: Long,
    ) {
        val lowerHash = hash.lowercase()
        fileRegistry.computeIfAbsent(lowerHash) { ConcurrentHashMap() }[fileIdx] = Pair(relativePath, sizeBytes)
        runCatching {
            val dir = File(root, lowerHash)
            if (dir.exists() || dir.mkdirs()) {
                val regFile = File(dir, REGISTRY_FILE)
                regFile.appendText("$fileIdx|$relativePath|$sizeBytes\n")
            }
        }
    }

    private fun ensureRegistryLoaded(
        lowerHash: String,
        dir: File,
    ) {
        val regFile = File(dir, REGISTRY_FILE)
        if (regFile.exists()) {
            runCatching {
                regFile.forEachLine { line ->
                    val parts = line.split('|')
                    if (parts.size >= 2) {
                        val idx = parts[0].toIntOrNull()
                        val path = parts[1]
                        val size = parts.getOrNull(2)?.toLongOrNull() ?: 0L
                        if (idx != null) {
                            fileRegistry.computeIfAbsent(lowerHash) { ConcurrentHashMap() }[idx] = Pair(path, size)
                        }
                    }
                }
            }
        }
    }

    override fun fileOf(
        hash: String,
        fileIdx: Int,
    ): File? {
        val lowerHash = hash.lowercase()
        val dir = File(root, lowerHash)
        ensureRegistryLoaded(lowerHash, dir)
        val inMem = fileRegistry[lowerHash]?.get(fileIdx)?.first
        return when {
            inMem != null -> resolveInside(root, lowerHash, inMem)
            fileIdx == 0 && dir.exists() -> dir.listFiles()?.firstOrNull { it.isFile && it.name != REGISTRY_FILE }
            else -> null
        }
    }

    override suspend fun bytesOnDisk(
        hash: String,
        fileIdx: Int,
    ): Long =
        withContext(io) {
            val f = fileOf(hash, fileIdx)
            if (f != null && f.exists() && f.isFile) fileBytes(f) else 0L
        }

    override suspend fun fileSize(
        hash: String,
        fileIdx: Int,
    ): Long =
        withContext(io) {
            val lowerHash = hash.lowercase()
            val recorded = fileRegistry[lowerHash]?.get(fileIdx)?.second
            if (recorded != null && recorded > 0L) return@withContext recorded
            val f = fileOf(hash, fileIdx)
            if (f != null && f.exists() && f.isFile) f.length() else 0L
        }

    override suspend fun deleteFile(
        hash: String,
        fileIdx: Int,
    ) {
        withContext(io) {
            val f = fileOf(hash, fileIdx)
            if (f != null && f.exists()) {
                f.delete()
                val dir = File(root, hash.lowercase())
                var parent = f.parentFile
                while (isDeletableEmptyParent(parent, dir)) {
                    parent?.delete()
                    parent = parent?.parentFile
                }
            }
        }
    }

    override suspend fun deleteFolderIfEmpty(hash: String) {
        withContext(io) {
            val dir = File(root, hash.lowercase())
            if (dir.exists() && dir.isDirectory) {
                val children = dir.listFiles() ?: emptyArray()
                val meaningful = children.filter { it.name != REGISTRY_FILE }
                if (meaningful.isEmpty()) {
                    dir.deleteRecursively()
                    fileRegistry.remove(hash.lowercase())
                }
            }
        }
    }

    override suspend fun deleteFolder(hash: String) {
        withContext(io) {
            val dir = File(root, hash.lowercase())
            if (dir.exists()) {
                dir.deleteRecursively()
            }
            fileRegistry.remove(hash.lowercase())
        }
    }

    override suspend fun listFolders(): Set<String> =
        withContext(io) {
            if (!root.exists()) return@withContext emptySet()
            root
                .listFiles()
                ?.filter { it.isDirectory }
                ?.map { it.name.lowercase() }
                ?.toSet()
                .orEmpty()
        }
}

@Singleton
class TorrentCacheStore internal constructor(
    val root: File,
    private val limitBytes: suspend () -> Long,
    private val keepAfterStopProvider: suspend () -> Boolean,
    private val io: CoroutineDispatcher,
    private val nowMs: () -> Long,
    private val fileBytes: (File) -> Long,
) : TorrentCache,
    TorrentCacheFiles by DefaultTorrentCacheFiles(root, io, fileBytes) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        prefs: TorrentPrefsRepository,
        @IoDispatcher io: CoroutineDispatcher,
        coordinatorProvider: Provider<CacheRetentionCoordinator>,
    ) : this(
        root = File(context.cacheDir, DIR_NAME),
        limitBytes = { cacheLimitBytes(prefs.cacheLimitGb.first()) },
        keepAfterStopProvider = { !prefs.clearCacheOnExit.first() },
        io = io,
        nowMs = System::currentTimeMillis,
        fileBytes = ::diskBytes,
    ) {
        this.coordinatorProvider = { coordinatorProvider.get() }
    }

    internal var coordinatorProvider: (() -> CacheRetentionCoordinator?)? = null

    private val activeHashes = Collections.synchronizedSet(mutableSetOf<String>())
    private val mutex = Mutex()

    @Volatile
    var lastReusedBytes: Long = 0L

    fun markActive(hash: String) {
        activeHashes.add(hash.lowercase())
    }

    fun markInactive(hash: String) {
        activeHashes.remove(hash.lowercase())
    }

    fun isActive(hash: String): Boolean = activeHashes.contains(hash.lowercase())

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
                val coordinator = coordinatorProvider?.invoke()
                if (coordinator != null) {
                    coordinator.onSessionStart()
                    trimInternal()
                } else {
                    if (!keepAfterStop()) {
                        root.listFiles()?.forEach { it.deleteRecursively() }
                    } else {
                        trimInternal()
                    }
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
                    if (!dir.exists() || computeFileSize(dir, fileBytes) == 0L) {
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
                computeFileSize(root, fileBytes)
            }
        }

    override suspend fun clearCache() {
        withContext(io) {
            mutex.withLock {
                coordinatorProvider?.invoke()?.clearAll()
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
        val entries = listCacheEntries(root, fileBytes)
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
