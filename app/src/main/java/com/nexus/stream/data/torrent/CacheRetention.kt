package com.nexus.stream.data.torrent

import kotlinx.serialization.Serializable

const val CACHE_RETAIN_PERCENT = 20L

enum class StopDecision { KEEP, DELETE }

enum class CompletionAction { NONE, DELETE_NOW, DELETE_ON_STOP }

@Serializable
enum class LedgerState { ACTIVE, RETAINED }

@Serializable
data class LedgerEntry(
    val key: String, // progress key, metaId|videoId (a movie is metaId|metaId)
    val infoHash: String, // lowercase
    val fileIdx: Int,
    val state: LedgerState,
    val atMs: Long,
)

private const val PERCENT_SCALE = 100L

fun decideOnStop(
    clearCacheOnExit: Boolean,
    bytesOnDisk: Long,
    fileSize: Long,
    completed: Boolean,
): StopDecision =
    when {
        !clearCacheOnExit -> StopDecision.KEEP
        completed -> StopDecision.DELETE
        fileSize > 0 && maxOf(bytesOnDisk, 0L) * PERCENT_SCALE >= fileSize * CACHE_RETAIN_PERCENT -> StopDecision.KEEP
        else -> StopDecision.DELETE
    }

fun switchPurgeTarget(
    previous: LedgerEntry?,
    newInfoHash: String,
    newFileIdx: Int,
): LedgerEntry? {
    if (previous == null) return null
    val differs = !previous.infoHash.equals(newInfoHash, ignoreCase = true) || previous.fileIdx != newFileIdx
    return if (differs) previous else null
}

fun completionAction(
    entry: LedgerEntry?,
    clearCacheOnExit: Boolean,
): CompletionAction {
    if (entry == null || !clearCacheOnExit) return CompletionAction.NONE
    return if (entry.state == LedgerState.ACTIVE) {
        CompletionAction.DELETE_ON_STOP
    } else {
        CompletionAction.DELETE_NOW
    }
}

fun foldersToWipeAtStart(
    onDisk: Set<String>,
    retainedHashes: Set<String>,
): Set<String> {
    val retainedLower = retainedHashes.map { it.lowercase() }.toSet()
    return onDisk.filter { it.lowercase() !in retainedLower }.toSet()
}
