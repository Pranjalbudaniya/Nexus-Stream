package com.nexus.stream.data.torrent

import com.nexus.stream.domain.repository.CacheCompletion
import com.nexus.stream.domain.repository.TorrentPrefsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

interface TorrentCacheFiles {
    suspend fun bytesOnDisk(
        hash: String,
        fileIdx: Int,
    ): Long

    suspend fun fileSize(
        hash: String,
        fileIdx: Int,
    ): Long = 0L

    suspend fun deleteFile(
        hash: String,
        fileIdx: Int,
    )

    suspend fun deleteFolderIfEmpty(hash: String)

    suspend fun deleteFolder(hash: String) = deleteFolderIfEmpty(hash)

    suspend fun listFolders(): Set<String>

    fun registerFile(
        hash: String,
        fileIdx: Int,
        relativePath: String,
        sizeBytes: Long = 0L,
    ) {}

    fun fileOf(
        hash: String,
        fileIdx: Int,
    ): java.io.File? = null
}

@Singleton
class CacheRetentionCoordinator(
    private val ledger: CacheLedger,
    private val files: TorrentCacheFiles,
    private val clearCacheOnExitProvider: suspend () -> Boolean,
    private val clock: () -> Long,
) : CacheCompletion {
    @Inject
    constructor(
        ledger: CacheLedger,
        files: TorrentCacheFiles,
        prefs: TorrentPrefsRepository,
    ) : this(
        ledger = ledger,
        files = files,
        clearCacheOnExitProvider = { prefs.clearCacheOnExit.first() },
        clock = { System.currentTimeMillis() },
    )

    private val completedWhileActive = Collections.synchronizedSet(mutableSetOf<String>())

    @Volatile
    var lastReusedBytes: Long = 0L
        private set

    suspend fun recordReusedOnAdd(
        hash: String,
        fileIdx: Int,
    ) {
        try {
            lastReusedBytes = files.bytesOnDisk(hash, fileIdx)
        } catch (_: Exception) {
            // swallowed
        }
    }

    suspend fun onReady(
        key: String?,
        hash: String,
        fileIdx: Int,
        fileSize: Long,
    ) {
        if (key == null || fileSize < 0L) return
        try {
            val previous = ledger.get(key)
            val purge = switchPurgeTarget(previous, hash, fileIdx)
            if (purge != null) {
                files.deleteFile(purge.infoHash, purge.fileIdx)
                files.deleteFolderIfEmpty(purge.infoHash)
                ledger.remove(key)
            }
            ledger.put(
                LedgerEntry(
                    key = key,
                    infoHash = hash.lowercase(),
                    fileIdx = fileIdx,
                    state = LedgerState.ACTIVE,
                    atMs = clock(),
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // swallowed
        }
    }

    suspend fun onStop(
        key: String?,
        hash: String,
        fileIdx: Int,
        fileSize: Long,
        completed: Boolean,
    ): Boolean {
        val clearCacheOnExit = runCatching { clearCacheOnExitProvider() }.getOrDefault(true)
        if (key == null) {
            return !clearCacheOnExit
        }
        return try {
            val wasCompletedWhileActive = completedWhileActive.remove(key)
            val finalCompleted = completed || wasCompletedWhileActive
            val bytes = files.bytesOnDisk(hash, fileIdx)
            val decision = decideOnStop(clearCacheOnExit, bytes, fileSize, finalCompleted)
            when (decision) {
                StopDecision.KEEP -> {
                    ledger.put(
                        LedgerEntry(
                            key = key,
                            infoHash = hash.lowercase(),
                            fileIdx = fileIdx,
                            state = LedgerState.RETAINED,
                            atMs = clock(),
                        ),
                    )
                    true
                }

                StopDecision.DELETE -> {
                    files.deleteFile(hash, fileIdx)
                    files.deleteFolderIfEmpty(hash)
                    ledger.remove(key)
                    val allEntries = ledger.all()
                    if (allEntries.any { it.infoHash.equals(hash, ignoreCase = true) }) {
                        true
                    } else {
                        !clearCacheOnExit
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            !clearCacheOnExit
        }
    }

    override suspend fun onWatched(key: String) {
        try {
            val clearCacheOnExit = clearCacheOnExitProvider()
            val entry = ledger.get(key)
            when (completionAction(entry, clearCacheOnExit)) {
                CompletionAction.DELETE_ON_STOP -> {
                    completedWhileActive.add(key)
                }

                CompletionAction.DELETE_NOW -> {
                    if (entry != null) {
                        files.deleteFile(entry.infoHash, entry.fileIdx)
                        files.deleteFolderIfEmpty(entry.infoHash)
                        ledger.remove(key)
                    }
                }

                CompletionAction.NONE -> {
                    // no-op
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // swallowed
        }
    }

    suspend fun onSessionStart() {
        try {
            val clearCacheOnExit = clearCacheOnExitProvider()
            val allEntries = ledger.all()
            for (entry in allEntries) {
                if (entry.state == LedgerState.ACTIVE) {
                    val bytes = files.bytesOnDisk(entry.infoHash, entry.fileIdx)
                    val nominalSize = files.fileSize(entry.infoHash, entry.fileIdx)
                    val decision = decideOnStop(clearCacheOnExit, bytes, nominalSize, completed = false)
                    if (decision == StopDecision.KEEP) {
                        ledger.put(entry.copy(state = LedgerState.RETAINED, atMs = clock()))
                    } else {
                        files.deleteFile(entry.infoHash, entry.fileIdx)
                        files.deleteFolderIfEmpty(entry.infoHash)
                        ledger.remove(entry.key)
                    }
                }
            }

            val updatedEntries = ledger.all()
            val diskFolders = files.listFolders().map { it.lowercase() }.toSet()
            for (entry in updatedEntries) {
                val hashLower = entry.infoHash.lowercase()
                val folderExists = hashLower in diskFolders
                val fileExists =
                    folderExists &&
                        (
                            files.fileSize(entry.infoHash, entry.fileIdx) > 0L ||
                                files.bytesOnDisk(entry.infoHash, entry.fileIdx) > 0L
                        )
                if (!folderExists || !fileExists) {
                    ledger.remove(entry.key)
                }
            }

            if (clearCacheOnExit) {
                val retainedHashes =
                    ledger
                        .all()
                        .filter { it.state == LedgerState.RETAINED }
                        .map { it.infoHash }
                        .toSet()
                val toWipe = foldersToWipeAtStart(files.listFolders(), retainedHashes)
                for (folder in toWipe) {
                    files.deleteFolder(folder)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // swallowed
        }
    }

    suspend fun clearAll() {
        try {
            val all = ledger.all()
            for (entry in all) {
                ledger.remove(entry.key)
            }
            completedWhileActive.clear()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // swallowed
        }
    }
}
