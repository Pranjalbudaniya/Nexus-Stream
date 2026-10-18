package com.nexus.stream.domain.usecase

import kotlin.math.min

private const val PERCENT_MULTIPLIER = 100
private const val MAX_PERCENT = 100

data class TargetPieces(
    val head: List<Int>,
    val tail: List<Int>,
) {
    val all: List<Int> get() = head + tail
}

fun targetPieces(
    fileOffset: Long,
    fileSize: Long,
    pieceLength: Long,
    headBytes: Long,
    tailBytes: Long,
): TargetPieces {
    if (fileSize <= 0 || pieceLength <= 0 || fileOffset < 0) {
        return TargetPieces(emptyList(), emptyList())
    }

    val headLen = min(fileSize, headBytes)
    val head =
        if (headLen <= 0) {
            emptyList()
        } else {
            val headStart = (fileOffset / pieceLength).toInt()
            val headEnd = ((fileOffset + headLen - 1) / pieceLength).toInt()
            (headStart..headEnd).toList()
        }

    val tailLen = min(fileSize, tailBytes)
    val tail =
        if (tailLen <= 0) {
            emptyList()
        } else {
            val tailStart = ((fileOffset + fileSize - tailLen) / pieceLength).toInt()
            val tailEnd = ((fileOffset + fileSize - 1) / pieceLength).toInt()
            val headSet = head.toSet()
            (tailStart..tailEnd).filter { it !in headSet }
        }

    return TargetPieces(head = head, tail = tail)
}

fun bufferingPercent(
    have: Int,
    total: Int,
): Int {
    if (total <= 0) return 0
    val raw = (have.toLong() * PERCENT_MULTIPLIER / total).toInt()
    return raw.coerceIn(0, MAX_PERCENT)
}
