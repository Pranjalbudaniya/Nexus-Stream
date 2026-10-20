package com.nexus.stream.data.torrent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile

class TorrentCacheStoreTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private var fakeClockMs: Long = 10_000_000L
    private var limitBytesVal: Long = 2L * 1024L * 1024L * 1024L
    private var keepAfterStopVal: Boolean = true

    private fun createStore(rootName: String = "torrents"): TorrentCacheStore {
        val root = File(tmpFolder.root, rootName)
        return TorrentCacheStore(
            root = root,
            limitBytes = { limitBytesVal },
            keepAfterStopProvider = { keepAfterStopVal },
            io = Dispatchers.Default,
            nowMs = { fakeClockMs },
        )
    }

    private fun createSparseFile(
        dir: File,
        name: String,
        sizeBytes: Long,
        lastModifiedMs: Long? = null,
    ): File {
        dir.mkdirs()
        val file = File(dir, name)
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(sizeBytes)
        }
        if (lastModifiedMs != null) {
            file.setLastModified(lastModifiedMs)
        }
        return file
    }

    private fun createTorrentDir(
        root: File,
        hash: String,
        sizeBytes: Long,
        lastModifiedMs: Long,
    ): File {
        val dir = File(root, hash.lowercase())
        dir.mkdirs()
        createSparseFile(dir, "content.mkv", sizeBytes, lastModifiedMs)
        dir.setLastModified(lastModifiedMs)
        return dir
    }

    @Test
    fun CS1_cacheSizeBytes_sumsNestedFilesAcrossEntries() =
        runBlocking {
            val store = createStore()
            store.root.mkdirs()

            val dir1 = File(store.root, "entry1").apply { mkdirs() }
            createSparseFile(dir1, "f1.bin", 100L)
            val subDir = File(dir1, "sub").apply { mkdirs() }
            createSparseFile(subDir, "f2.bin", 200L)

            val dir2 = File(store.root, "entry2").apply { mkdirs() }
            createSparseFile(dir2, "f3.bin", 300L)

            assertEquals(600L, store.cacheSizeBytes())
        }

    @Test
    fun CS2_keepOn_onSessionStartOverLimit_oldestDirsDeletedUntilFits() =
        runBlocking {
            limitBytesVal = 500L
            keepAfterStopVal = true
            val store = createStore()
            store.root.mkdirs()

            val dir1 = createTorrentDir(store.root, "hash1", 300L, 1000L)
            val dir2 = createTorrentDir(store.root, "hash2", 300L, 2000L)
            val dir3 = createTorrentDir(store.root, "hash3", 300L, 3000L)

            // Total 900 > 500. Oldest hash1 (1000) and hash2 (2000) should be evicted.
            store.onSessionStart()

            assertFalse(dir1.exists())
            assertFalse(dir2.exists())
            assertTrue(dir3.exists())
        }

    @Test
    fun CS3_markActiveDir_neverDeletedByTrimOrClearCache() =
        runBlocking {
            limitBytesVal = 100L
            keepAfterStopVal = true
            val store = createStore()
            store.root.mkdirs()

            val activeDir = createTorrentDir(store.root, "active_hash", 500L, 1000L)
            val idleDir = createTorrentDir(store.root, "idle_hash", 300L, 2000L)

            store.markActive("active_hash")

            // onSessionStart triggers trim
            store.onSessionStart()
            assertTrue(activeDir.exists())
            assertFalse(idleDir.exists())

            // clearCache also leaves activeDir
            store.clearCache()
            assertTrue(activeDir.exists())
        }

    @Test
    fun CS4_keepOff_onSessionStart_everyChildDeleted() =
        runBlocking {
            keepAfterStopVal = false
            val store = createStore()
            store.root.mkdirs()

            val dir1 = createTorrentDir(store.root, "hash1", 200L, 1000L)
            val dir2 = createTorrentDir(store.root, "hash2", 300L, 2000L)

            store.onSessionStart()

            assertFalse(dir1.exists())
            assertFalse(dir2.exists())
            assertTrue(store.root.exists())
        }

    @Test
    fun CS5_keepOn_onSessionStartWithinLimit_nothingDeleted() =
        runBlocking {
            limitBytesVal = 1000L
            keepAfterStopVal = true
            val store = createStore()
            store.root.mkdirs()

            val dir1 = createTorrentDir(store.root, "hash1", 200L, 1000L)
            val dir2 = createTorrentDir(store.root, "hash2", 300L, 2000L)

            store.onSessionStart()

            assertTrue(dir1.exists())
            assertTrue(dir2.exists())
        }

    @Test
    fun CS6_onRemovedKeptTrue_lastModifiedUpdatedAndTrims_zeroByteOrMissingDirDeleted() =
        runBlocking {
            fakeClockMs = 55_000_000L
            limitBytesVal = 1000L
            keepAfterStopVal = true
            val store = createStore()
            store.root.mkdirs()

            // Case A: 0-byte directory
            val emptyDir = File(store.root, "empty_hash").apply { mkdirs() }
            store.onRemoved("empty_hash", kept = true)
            assertFalse(emptyDir.exists())

            // Case B: normal directory with content
            val dataDir = createTorrentDir(store.root, "data_hash", 400L, 1000L)
            store.onRemoved("data_hash", kept = true)
            assertTrue(dataDir.exists())
            assertEquals(fakeClockMs, dataDir.lastModified())
        }

    @Test
    fun CS7_onRemovedKeptFalse_hashBecomesInactiveStoreDeletesNothing() =
        runBlocking {
            keepAfterStopVal = false
            val store = createStore()
            store.root.mkdirs()

            val dir = createTorrentDir(store.root, "hash_kept_false", 300L, 1000L)
            store.markActive("hash_kept_false")

            store.onRemoved("hash_kept_false", kept = false)
            // Store does not delete the dir (backend handles it)
            assertTrue(dir.exists())

            // But it became inactive, so clearCache will delete it
            store.clearCache()
            assertFalse(dir.exists())
        }

    @Test
    fun CS8_clearCache_onlyInactiveEntriesGoneSizeEqualsActive() =
        runBlocking {
            val store = createStore()
            store.root.mkdirs()

            val activeDir = createTorrentDir(store.root, "act", 250L, 1000L)
            val inactDir = createTorrentDir(store.root, "inact", 400L, 2000L)
            store.markActive("act")

            store.clearCache()

            assertTrue(activeDir.exists())
            assertFalse(inactDir.exists())
            assertEquals(250L, store.cacheSizeBytes())
        }

    @Test
    fun CS9_concurrentOperations_noExceptionStateConsistent() =
        runBlocking {
            val store = createStore()
            store.root.mkdirs()

            val jobs =
                List(30) { i ->
                    launch(Dispatchers.Default) {
                        val hash = "hash_$i"
                        store.markActive(hash)
                        createTorrentDir(store.root, hash, 100L, 1000L + i)
                        store.onRemoved(hash, kept = true)
                        store.cacheSizeBytes()
                        store.clearCache()
                        store.onSessionStart()
                    }
                }
            jobs.joinAll()
            assertTrue(store.root.exists())
        }

    @Test
    fun CS10_missingRoot_sizeZeroOnSessionStartCreatesItClearCacheNoOp() =
        runBlocking {
            val root = File(tmpFolder.root, "missing_root")
            val store =
                TorrentCacheStore(
                    root = root,
                    limitBytes = { 1000L },
                    keepAfterStopProvider = { true },
                    io = Dispatchers.Default,
                    nowMs = { 1000L },
                )

            assertEquals(0L, store.cacheSizeBytes())
            store.clearCache() // no crash

            store.onSessionStart()
            assertTrue(store.root.exists())
        }

    @Test
    fun CS11_markActiveTwiceThenMarkInactiveOnce_becomesInactive_upperCaseMatches() =
        runBlocking {
            val store = createStore()
            store.root.mkdirs()

            store.markActive("ABCDEF")
            store.markActive("abcdef")
            store.markInactive("ABCDEF")

            val dir = createTorrentDir(store.root, "abcdef", 200L, 1000L)
            store.clearCache()

            // Since it became inactive, clearCache evicts it
            assertFalse(dir.exists())
        }

    @Test
    fun CS12_plainFileDirectlyUnderRoot_countsAndCanBeEvicted() =
        runBlocking {
            limitBytesVal = 200L
            keepAfterStopVal = true
            val store = createStore()
            store.root.mkdirs()

            val file1 = createSparseFile(store.root, "file1.bin", 150L, 1000L)
            val file2 = createSparseFile(store.root, "file2.bin", 150L, 2000L)

            assertEquals(300L, store.cacheSizeBytes())

            // onSessionStart triggers trim -> oldest file1.bin evicted
            store.onSessionStart()

            assertFalse(file1.exists())
            assertTrue(file2.exists())
            assertEquals(150L, store.cacheSizeBytes())
        }
}
