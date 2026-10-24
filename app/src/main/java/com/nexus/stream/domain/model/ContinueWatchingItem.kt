package com.nexus.stream.domain.model

enum class ContinueKind {
    RESUME,
    UP_NEXT,
}

data class ContinueWatchingItem(
    val metaId: String,
    val type: String,
    val name: String,
    val poster: String?,
    val videoId: String,
    val season: Int?,
    val episode: Int?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val kind: ContinueKind = ContinueKind.RESUME,
)

val ContinueWatchingItem.canRemove: Boolean
    get() = kind == ContinueKind.RESUME

val ContinueWatchingItem.progress: Float?
    get() =
        if (kind == ContinueKind.UP_NEXT || durationMs <= 0L) {
            null
        } else {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        }
