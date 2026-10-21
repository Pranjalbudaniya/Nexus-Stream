@file:Suppress("MatchingDeclarationName")

package com.nexus.stream.domain.usecase

import kotlin.math.abs

data class ReadWindow(
    val position: Long,
    val lastSeenMs: Long,
)

fun activeReadWindows(
    recent: List<ReadWindow>,
    nowMs: Long,
    windowBytes: Long,
    maxWindows: Int = 3,
    ttlMs: Long = 5_000,
): List<Long> {
    if (recent.isEmpty() || maxWindows <= 0 || windowBytes <= 0L) return emptyList()
    val halfWindow = windowBytes / 2L
    val valid = recent.asReversed().filter { rw -> rw.lastSeenMs <= nowMs && (nowMs - rw.lastSeenMs) <= ttlMs }
    val sorted = valid.sortedByDescending { it.lastSeenMs }
    val result = mutableListOf<Long>()
    for (rw in sorted) {
        val isClose = result.any { chosen -> abs(rw.position - chosen) <= halfWindow }
        if (!isClose) {
            result.add(rw.position)
            if (result.size == maxWindows) {
                break
            }
        }
    }
    return result
}

fun piecesForRange(
    fileOffset: Long,
    pieceLength: Long,
    position: Long,
    length: Long,
): List<Int> {
    if (isRangeInvalid(fileOffset, pieceLength, position, length)) {
        return emptyList()
    }
    val startByte = fileOffset + position
    val endByteInclusive = startByte + length - 1L
    val startPiece = (startByte / pieceLength).toInt()
    val endPiece = (endByteInclusive / pieceLength).toInt()
    return (startPiece..endPiece).toList()
}

fun windowPieces(
    fileOffset: Long,
    fileSize: Long,
    pieceLength: Long,
    position: Long,
    windowBytes: Long,
): List<Int> {
    if (isWindowInvalid(fileOffset, fileSize, pieceLength, position, windowBytes)) {
        return emptyList()
    }
    val endPosition = minOf(fileSize, position + windowBytes)
    val length = endPosition - position
    return piecesForRange(fileOffset, pieceLength, position, length)
}

fun needsReprioritize(
    position: Long,
    windowStart: Long?,
    windowBytes: Long,
): Boolean {
    if (windowBytes <= 0L || windowStart == null) return true
    val halfWindow = windowBytes / 2L
    return position < windowStart || position >= windowStart + halfWindow
}

fun deadlineMsFor(
    position: Int,
    stepMs: Long,
): Int {
    if (position < 0 || stepMs <= 0L) return 1
    val posFactor = position.toLong() + 1L
    val maxInt = Int.MAX_VALUE.toLong()
    val exceedsMax = posFactor > maxInt || stepMs > maxInt
    val product = if (exceedsMax) maxInt else posFactor * stepMs
    return minOf(maxInt, maxOf(1L, product)).toInt()
}

private fun isRangeInvalid(
    fileOffset: Long,
    pieceLength: Long,
    position: Long,
    length: Long,
): Boolean {
    val invalidLength = length <= 0L || pieceLength <= 0L
    val invalidOffset = position < 0L || fileOffset < 0L
    return invalidLength || invalidOffset
}

private fun isWindowInvalid(
    fileOffset: Long,
    fileSize: Long,
    pieceLength: Long,
    position: Long,
    windowBytes: Long,
): Boolean {
    val invalidFile = fileSize <= 0L || pieceLength <= 0L
    val invalidPos = position < 0L || position >= fileSize
    val invalidWindow = windowBytes <= 0L || fileOffset < 0L
    return invalidFile || invalidPos || invalidWindow
}
