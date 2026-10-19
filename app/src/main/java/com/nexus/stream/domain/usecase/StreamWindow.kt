package com.nexus.stream.domain.usecase

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
