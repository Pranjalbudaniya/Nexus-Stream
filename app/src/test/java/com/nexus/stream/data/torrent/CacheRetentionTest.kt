package com.nexus.stream.data.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CacheRetentionTest {
    private fun entry(
        key: String = "tt1:1:2|tt1:1:2",
        hash: String = "abc",
        idx: Int = 0,
        state: LedgerState = LedgerState.RETAINED,
    ) = LedgerEntry(key = key, infoHash = hash, fileIdx = idx, state = state, atMs = 1L)

    @Test
    fun PC1_keepCacheOff_belowTwentyPercent_deletes() {
        assertEquals(StopDecision.DELETE, decideOnStop(true, 199, 1000, false))
    }

    @Test
    fun PC2_keepCacheOff_atTwentyPercent_keeps() {
        assertEquals(StopDecision.KEEP, decideOnStop(true, 200, 1000, false))
    }

    @Test
    fun PC3_keepCacheOff_completed_deletes_evenWhenMostIsDownloaded() {
        assertEquals(StopDecision.DELETE, decideOnStop(true, 900, 1000, true))
    }

    @Test
    fun PC4_keepCacheOn_alwaysKeeps() {
        assertEquals(StopDecision.KEEP, decideOnStop(false, 0, 1000, false))
        assertEquals(StopDecision.KEEP, decideOnStop(false, 1000, 1000, true))
    }

    @Test
    fun PC5_unknownFileSize_keepCacheOff_deletes() {
        assertEquals(StopDecision.DELETE, decideOnStop(true, 500, 0, false))
        assertEquals(StopDecision.DELETE, decideOnStop(true, 500, -1, false))
    }

    @Test
    fun PC6_negativeBytes_countAsZero() {
        assertEquals(StopDecision.DELETE, decideOnStop(true, -50, 1000, false))
    }

    @Test
    fun PC7_switchPurge_sameHashSameFile_isNull() {
        assertNull(switchPurgeTarget(entry(hash = "abc", idx = 3), "abc", 3))
    }

    @Test
    fun PC8_switchPurge_differentHash_returnsThePreviousEntry() {
        val previous = entry(hash = "abc", idx = 3)
        assertEquals(previous, switchPurgeTarget(previous, "def", 3))
    }

    @Test
    fun PC9_switchPurge_sameHashOtherFile_returnsThePreviousEntry() {
        val previous = entry(hash = "abc", idx = 3)
        assertEquals(previous, switchPurgeTarget(previous, "abc", 4))
    }

    @Test
    fun PC10_switchPurge_noPreviousEntry_isNull() {
        assertNull(switchPurgeTarget(null, "abc", 0))
    }

    @Test
    fun PC11_switchPurge_hashComparisonIgnoresCase() {
        assertNull(switchPurgeTarget(entry(hash = "abc", idx = 1), "ABC", 1))
    }

    @Test
    fun PC12_completion_noEntry_isNone() {
        assertEquals(CompletionAction.NONE, completionAction(null, true))
    }

    @Test
    fun PC13_completion_keepCacheOn_isNone() {
        assertEquals(CompletionAction.NONE, completionAction(entry(), false))
        assertEquals(CompletionAction.NONE, completionAction(entry(state = LedgerState.ACTIVE), false))
    }

    @Test
    fun PC14_completion_keepCacheOff_activeEntry_deletesOnStop() {
        assertEquals(CompletionAction.DELETE_ON_STOP, completionAction(entry(state = LedgerState.ACTIVE), true))
    }

    @Test
    fun PC15_completion_keepCacheOff_retainedEntry_deletesNow() {
        assertEquals(CompletionAction.DELETE_NOW, completionAction(entry(state = LedgerState.RETAINED), true))
    }

    @Test
    fun PC16_wipeAtStart_removesOnlyOrphans() {
        assertEquals(setOf("orphan"), foldersToWipeAtStart(setOf("kept", "orphan"), setOf("kept")))
    }

    @Test
    fun PC17_wipeAtStart_comparisonIgnoresCase() {
        assertEquals(emptySet<String>(), foldersToWipeAtStart(setOf("ABC"), setOf("abc")))
    }

    @Test
    fun PC18_wipeAtStart_emptyInputs() {
        assertEquals(emptySet<String>(), foldersToWipeAtStart(emptySet(), setOf("abc")))
        assertEquals(setOf("x"), foldersToWipeAtStart(setOf("x"), emptySet()))
    }

    @Test
    fun AU3_completionAction_cases() {
        // no entry or Keep on -> NONE
        assertEquals(CompletionAction.NONE, completionAction(null, clearCacheOnExit = true))
        assertEquals(CompletionAction.NONE, completionAction(null, clearCacheOnExit = false))
        assertEquals(
            CompletionAction.NONE,
            completionAction(entry(state = LedgerState.ACTIVE), clearCacheOnExit = false),
        )
        assertEquals(
            CompletionAction.NONE,
            completionAction(entry(state = LedgerState.RETAINED), clearCacheOnExit = false),
        )

        // ACTIVE -> DELETE_ON_STOP
        assertEquals(
            CompletionAction.DELETE_ON_STOP,
            completionAction(entry(state = LedgerState.ACTIVE), clearCacheOnExit = true),
        )

        // RETAINED -> DELETE_NOW
        assertEquals(
            CompletionAction.DELETE_NOW,
            completionAction(entry(state = LedgerState.RETAINED), clearCacheOnExit = true),
        )
    }

    @Test
    fun AU4_foldersToWipeAtStart_removesOnlyOrphans() {
        val onDisk = setOf("kept1", "Kept2", "orphan1", "orphan2")
        val retained = setOf("kept1", "kept2")
        val toWipe = foldersToWipeAtStart(onDisk, retained)
        assertEquals(setOf("orphan1", "orphan2"), toWipe)
    }
}
