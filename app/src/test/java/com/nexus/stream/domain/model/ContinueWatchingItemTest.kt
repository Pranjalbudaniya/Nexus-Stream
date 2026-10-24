package com.nexus.stream.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinueWatchingItemTest {
    private fun createItem(
        kind: ContinueKind = ContinueKind.RESUME,
        positionMs: Long = 50_000L,
        durationMs: Long = 100_000L,
    ) = ContinueWatchingItem(
        metaId = "m1",
        type = "series",
        name = "Title",
        poster = null,
        videoId = "v1",
        season = 1,
        episode = 1,
        positionMs = positionMs,
        durationMs = durationMs,
        updatedAt = 1000L,
        kind = kind,
    )

    @Test
    fun CK1_canRemove_trueForResume_falseForUpNext() {
        val resumeItem = createItem(kind = ContinueKind.RESUME)
        val upNextItem = createItem(kind = ContinueKind.UP_NEXT)

        assertTrue(resumeItem.canRemove)
        assertFalse(upNextItem.canRemove)
    }

    @Test
    fun CK2_progress_nullForUpNext_valueForResume() {
        val upNextItem = createItem(kind = ContinueKind.UP_NEXT, positionMs = 50_000L, durationMs = 100_000L)
        assertNull(upNextItem.progress)

        val resumeItem = createItem(kind = ContinueKind.RESUME, positionMs = 50_000L, durationMs = 100_000L)
        assertEquals(0.5f, resumeItem.progress)
    }
}
