package com.nexus.stream.data.torrent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class CacheRetentionCoordinatorTest {
    private class FakeLedger : CacheLedger {
        val entries = mutableMapOf<String, LedgerEntry>()
        var throwException: Exception? = null

        override suspend fun get(key: String): LedgerEntry? {
            throwException?.let { throw it }
            return entries[key]
        }

        override suspend fun put(entry: LedgerEntry) {
            throwException?.let { throw it }
            entries[entry.key] = entry
        }

        override suspend fun remove(key: String) {
            throwException?.let { throw it }
            entries.remove(key)
        }

        override suspend fun all(): List<LedgerEntry> {
            throwException?.let { throw it }
            return entries.values.toList()
        }
    }

    private class FakeTorrentCacheFiles : TorrentCacheFiles {
        val diskBytes = mutableMapOf<Pair<String, Int>, Long>()
        val fileSizes = mutableMapOf<Pair<String, Int>, Long>()
        val deletedFiles = mutableListOf<Pair<String, Int>>()
        val deletedFolders = mutableListOf<String>()
        val folders = mutableSetOf<String>()

        override suspend fun bytesOnDisk(
            hash: String,
            fileIdx: Int,
        ): Long = diskBytes[hash.lowercase() to fileIdx] ?: 0L

        override suspend fun fileSize(
            hash: String,
            fileIdx: Int,
        ): Long = fileSizes[hash.lowercase() to fileIdx] ?: 0L

        override suspend fun deleteFile(
            hash: String,
            fileIdx: Int,
        ) {
            deletedFiles.add(hash.lowercase() to fileIdx)
            diskBytes.remove(hash.lowercase() to fileIdx)
            fileSizes.remove(hash.lowercase() to fileIdx)
        }

        override suspend fun deleteFolderIfEmpty(hash: String) {
            val h = hash.lowercase()
            val hasFiles = diskBytes.keys.any { it.first == h } || fileSizes.keys.any { it.first == h }
            if (!hasFiles) {
                deletedFolders.add(h)
                folders.remove(h)
            }
        }

        override suspend fun deleteFolder(hash: String) {
            val h = hash.lowercase()
            deletedFolders.add(h)
            folders.remove(h)
            diskBytes.keys.filter { it.first == h }.forEach { diskBytes.remove(it) }
            fileSizes.keys.filter { it.first == h }.forEach { fileSizes.remove(it) }
        }

        override suspend fun listFolders(): Set<String> = folders.toSet()
    }

    private val currentClock = 1000L
    private var clearCacheOnExit = true
    private val ledger = FakeLedger()
    private val files = FakeTorrentCacheFiles()

    private fun createCoordinator(): CacheRetentionCoordinator =
        CacheRetentionCoordinator(
            ledger = ledger,
            files = files,
            clearCacheOnExitProvider = { clearCacheOnExit },
            clock = { currentClock },
        )

    @Test
    fun CO1_onReady_putsAnActiveEntry_lowercaseHash() =
        runTest {
            val coordinator = createCoordinator()
            coordinator.onReady("k", "ABC", 2, 1000L)

            val entry = ledger.get("k")
            assertEquals("k", entry?.key)
            assertEquals("abc", entry?.infoHash)
            assertEquals(2, entry?.fileIdx)
            assertEquals(LedgerState.ACTIVE, entry?.state)
            assertEquals(1000L, entry?.atMs)
        }

    @Test
    fun CO2_onReady_nullKey_doesNothing() =
        runTest {
            val coordinator = createCoordinator()
            coordinator.onReady(null, "ABC", 2, 1000L)

            assertTrue(ledger.entries.isEmpty())
            assertTrue(files.deletedFiles.isEmpty())
        }

    @Test
    fun CO3_onReady_otherSource_purgesThePreviousFile() =
        runTest {
            val coordinator = createCoordinator()
            ledger.put(
                LedgerEntry("k", "abc", 1, LedgerState.RETAINED, 500L),
            )
            files.folders.add("abc")
            files.diskBytes["abc" to 1] = 500L

            coordinator.onReady("k", "def", 1, 1000L)

            assertTrue(files.deletedFiles.contains("abc" to 1))
            val entry = ledger.get("k")
            assertEquals("def", entry?.infoHash)
            assertEquals(LedgerState.ACTIVE, entry?.state)
        }

    @Test
    fun CO4_onReady_sameSourceAgain_purgesNothing() =
        runTest {
            val coordinator = createCoordinator()
            ledger.put(
                LedgerEntry("k", "abc", 1, LedgerState.RETAINED, 500L),
            )
            coordinator.onReady("k", "abc", 1, 1000L)

            assertTrue(files.deletedFiles.isEmpty())
            assertEquals(LedgerState.ACTIVE, ledger.get("k")?.state)
        }

    @Test
    fun CO5_onReady_keepCacheOn_stillPurgesAReplacedSource() =
        runTest {
            clearCacheOnExit = false
            val coordinator = createCoordinator()
            ledger.put(
                LedgerEntry("k", "abc", 1, LedgerState.RETAINED, 500L),
            )
            files.folders.add("abc")
            files.diskBytes["abc" to 1] = 500L

            coordinator.onReady("k", "def", 1, 1000L)

            assertTrue(files.deletedFiles.contains("abc" to 1))
            assertEquals("def", ledger.get("k")?.infoHash)
        }

    @Test
    fun CO6_onStop_keepCacheOff_atTwentyPercent_retains() =
        runTest {
            clearCacheOnExit = true
            val coordinator = createCoordinator()
            files.diskBytes["abc" to 2] = 200L

            val kept = coordinator.onStop("k", "abc", 2, 1000L, completed = false)

            assertTrue(kept)
            assertEquals(LedgerState.RETAINED, ledger.get("k")?.state)
            assertTrue(files.deletedFiles.isEmpty())
        }

    @Test
    fun CO7_onStop_keepCacheOff_belowTwentyPercent_deletes() =
        runTest {
            clearCacheOnExit = true
            val coordinator = createCoordinator()
            files.diskBytes["abc" to 2] = 190L
            ledger.put(LedgerEntry("k", "abc", 2, LedgerState.ACTIVE, 500L))

            val kept = coordinator.onStop("k", "abc", 2, 1000L, completed = false)

            assertFalse(kept)
            assertNull(ledger.get("k"))
            assertTrue(files.deletedFiles.contains("abc" to 2))
        }

    @Test
    fun CO8_onStop_keepCacheOn_alwaysRetains() =
        runTest {
            clearCacheOnExit = false
            val coordinator = createCoordinator()
            files.diskBytes["abc" to 2] = 0L

            val kept = coordinator.onStop("k", "abc", 2, 1000L, completed = false)

            assertTrue(kept)
            assertEquals(LedgerState.RETAINED, ledger.get("k")?.state)
            assertTrue(files.deletedFiles.isEmpty())
        }

    @Test
    fun CO9_onStop_completed_keepCacheOff_deletes_keepCacheOn_retains() =
        runTest {
            val coordinator = createCoordinator()
            files.diskBytes["abc" to 2] = 1000L

            clearCacheOnExit = true
            ledger.put(LedgerEntry("k1", "abc", 2, LedgerState.ACTIVE, 500L))
            val keptOff = coordinator.onStop("k1", "abc", 2, 1000L, completed = true)
            assertFalse(keptOff)
            assertNull(ledger.get("k1"))
            assertTrue(files.deletedFiles.contains("abc" to 2))

            clearCacheOnExit = false
            ledger.put(LedgerEntry("k2", "def", 2, LedgerState.ACTIVE, 500L))
            val keptOn = coordinator.onStop("k2", "def", 2, 1000L, completed = true)
            assertTrue(keptOn)
            assertEquals(LedgerState.RETAINED, ledger.get("k2")?.state)
        }

    @Test
    fun CO10_onStop_nullKey_changesNothing() =
        runTest {
            clearCacheOnExit = true
            val coordinator = createCoordinator()
            val keptOff = coordinator.onStop(null, "abc", 2, 1000L, completed = false)
            assertFalse(keptOff)
            assertTrue(ledger.entries.isEmpty())
            assertTrue(files.deletedFiles.isEmpty())

            clearCacheOnExit = false
            val keptOn = coordinator.onStop(null, "abc", 2, 1000L, completed = false)
            assertTrue(keptOn)
        }

    @Test
    fun CO11_onWatched_activeEntry_keepCacheOff_deletesOnStop() =
        runTest {
            clearCacheOnExit = true
            val coordinator = createCoordinator()
            ledger.put(LedgerEntry("k", "abc", 1, LedgerState.ACTIVE, 500L))
            files.diskBytes["abc" to 1] = 500L

            coordinator.onWatched("k")
            assertTrue(files.deletedFiles.isEmpty()) // Not deleted yet

            val kept = coordinator.onStop("k", "abc", 1, 1000L, completed = false)
            assertFalse(kept)
            assertTrue(files.deletedFiles.contains("abc" to 1))
            assertNull(ledger.get("k"))
        }

    @Test
    fun CO12_onWatched_retainedEntry_keepCacheOff_deletesNow() =
        runTest {
            clearCacheOnExit = true
            val coordinator = createCoordinator()
            ledger.put(LedgerEntry("k", "abc", 1, LedgerState.RETAINED, 500L))
            files.diskBytes["abc" to 1] = 500L

            coordinator.onWatched("k")

            assertTrue(files.deletedFiles.contains("abc" to 1))
            assertNull(ledger.get("k"))
        }

    @Test
    fun CO13_onWatched_keepCacheOn_doesNothing() =
        runTest {
            clearCacheOnExit = false
            val coordinator = createCoordinator()
            ledger.put(LedgerEntry("k1", "abc", 1, LedgerState.ACTIVE, 500L))
            ledger.put(LedgerEntry("k2", "def", 2, LedgerState.RETAINED, 500L))

            coordinator.onWatched("k1")
            coordinator.onWatched("k2")

            assertTrue(files.deletedFiles.isEmpty())
            assertEquals(2, ledger.entries.size)
        }

    @Test
    fun CO14_onSessionStart_keepCacheOff_wipesOnlyOrphans() =
        runTest {
            clearCacheOnExit = true
            val coordinator = createCoordinator()
            ledger.put(LedgerEntry("k", "kept", 0, LedgerState.RETAINED, 500L))
            files.folders.add("kept")
            files.folders.add("orphan")
            files.fileSizes["kept" to 0] = 1000L
            files.diskBytes["kept" to 0] = 300L

            coordinator.onSessionStart()

            assertTrue(files.deletedFolders.contains("orphan"))
            assertFalse(files.deletedFolders.contains("kept"))
        }

    @Test
    fun CO15_onSessionStart_activeEntryFromAKilledSession_isDecidedLikeAStop() =
        runTest {
            clearCacheOnExit = true
            val coordinator = createCoordinator()
            ledger.put(LedgerEntry("k1", "h1", 0, LedgerState.ACTIVE, 500L))
            ledger.put(LedgerEntry("k2", "h2", 0, LedgerState.ACTIVE, 500L))
            files.folders.add("h1")
            files.folders.add("h2")
            files.fileSizes["h1" to 0] = 1000L
            files.diskBytes["h1" to 0] = 300L // 30% -> RETAINED
            files.fileSizes["h2" to 0] = 1000L
            files.diskBytes["h2" to 0] = 100L // 10% -> DELETED

            coordinator.onSessionStart()

            assertEquals(LedgerState.RETAINED, ledger.get("k1")?.state)
            assertNull(ledger.get("k2"))
            assertTrue(files.deletedFiles.contains("h2" to 0))
        }

    @Test
    fun CO16_onSessionStart_staleEntries_areRemoved() =
        runTest {
            val coordinator = createCoordinator()
            ledger.put(LedgerEntry("k", "missing_folder", 0, LedgerState.RETAINED, 500L))

            coordinator.onSessionStart()

            assertNull(ledger.get("k"))
        }

    @Test
    fun CO17_pack_completingOneEpisode_keepsTheOther() =
        runTest {
            clearCacheOnExit = true
            val coordinator = createCoordinator()
            ledger.put(LedgerEntry("ep1", "pack", 1, LedgerState.RETAINED, 500L))
            ledger.put(LedgerEntry("ep2", "pack", 2, LedgerState.RETAINED, 500L))
            files.folders.add("pack")
            files.diskBytes["pack" to 1] = 1000L
            files.diskBytes["pack" to 2] = 1000L
            files.fileSizes["pack" to 1] = 1000L
            files.fileSizes["pack" to 2] = 1000L

            coordinator.onWatched("ep1")

            assertTrue(files.deletedFiles.contains("pack" to 1))
            assertFalse(files.deletedFiles.contains("pack" to 2))
            assertFalse(files.deletedFolders.contains("pack"))
            assertEquals(LedgerState.RETAINED, ledger.get("ep2")?.state)
            assertNull(ledger.get("ep1"))
        }

    @Test
    fun CO18_ledgerFailure_isSwallowed_neverThrows() =
        runTest {
            val coordinator = createCoordinator()
            ledger.throwException = IOException("disk read error")

            coordinator.onReady("k", "abc", 1, 1000L)
            coordinator.onStop("k", "abc", 1, 1000L, completed = false)
            coordinator.onWatched("k")
            coordinator.onSessionStart()
            coordinator.clearAll()
        }

    @Test
    fun CO19_cancellation_propagates() =
        runTest {
            val coordinator = createCoordinator()
            ledger.throwException = CancellationException("cancelled")

            try {
                coordinator.onReady("k", "abc", 1, 1000L)
                fail("Should have thrown CancellationException")
            } catch (e: CancellationException) {
                assertEquals("cancelled", e.message)
            }
        }

    @Test
    fun CO20_clearAll_emptiesTheLedger() =
        runTest {
            val coordinator = createCoordinator()
            ledger.put(LedgerEntry("k1", "h1", 1, LedgerState.RETAINED, 500L))
            ledger.put(LedgerEntry("k2", "h2", 2, LedgerState.ACTIVE, 500L))

            coordinator.clearAll()

            assertTrue(ledger.entries.isEmpty())
        }

    @Test
    fun AU2_watchedWhileActive_thenStop_deletes_whenKeepCacheOff() =
        runTest {
            // Keep cache OFF: clearCacheOnExit = true
            clearCacheOnExit = true
            val coordinator = createCoordinator()
            val key = "tt123|tt123:1:1"
            val hash = "0123456789abcdef0123456789abcdef01234567"
            val fileIdx = 0
            val fileSize = 1_000_000L

            // onReady marks active
            coordinator.onReady(key, hash, fileIdx, fileSize)
            assertEquals(LedgerState.ACTIVE, ledger.get(key)?.state)

            // onWatched while active
            coordinator.onWatched(key)
            assertTrue(files.deletedFiles.isEmpty())

            // onStop with completed = false
            files.diskBytes[hash to fileIdx] = 500_000L
            val keptOff = coordinator.onStop(key, hash, fileIdx, fileSize, completed = false)
            assertFalse(keptOff)
            assertNull(ledger.get(key))
            assertTrue(files.deletedFiles.contains(hash to fileIdx))

            // Keep cache ON: clearCacheOnExit = false
            clearCacheOnExit = false
            val coordinator2 = createCoordinator()
            val key2 = "tt123|tt123:1:2"
            val hash2 = "fedcba9876543210fedcba9876543210fedcba98"
            coordinator2.onReady(key2, hash2, fileIdx, fileSize)
            coordinator2.onWatched(key2)
            files.diskBytes[hash2 to fileIdx] = 500_000L
            val keptOn = coordinator2.onStop(key2, hash2, fileIdx, fileSize, completed = false)
            assertTrue(keptOn)
            assertEquals(LedgerState.RETAINED, ledger.get(key2)?.state)
            assertFalse(files.deletedFiles.contains(hash2 to fileIdx))
        }
}
