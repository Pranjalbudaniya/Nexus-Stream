package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.ParsedVersion
import kotlin.math.max
import kotlin.math.min

private const val MAX_VERSION_PARTS = 4

fun parseVersion(tag: String?): ParsedVersion? {
    val cleaned = tag?.let(::cleanTag) ?: return null
    return parseCleanedVersion(cleaned)
}

private fun cleanTag(tag: String): String? {
    var trimmed = tag.trim()
    if (trimmed.startsWith("v", ignoreCase = true)) {
        trimmed = trimmed.substring(1)
    }
    return trimmed.ifEmpty { null }
}

private fun parseCleanedVersion(cleaned: String): ParsedVersion? {
    val dashIndex = cleaned.indexOf('-')
    val isTrailingDash = dashIndex >= 0 && dashIndex == cleaned.length - 1
    val corePart = if (dashIndex >= 0) cleaned.substring(0, dashIndex) else cleaned
    val prePart = if (dashIndex >= 0) cleaned.substring(dashIndex + 1) else null
    val numericParts = if (isTrailingDash) null else parseCoreParts(corePart)
    return numericParts?.let { ParsedVersion(it, prePart) }
}

private fun isNumericOnly(part: String): Boolean = part.isNotEmpty() && part.all { it in '0'..'9' }

private fun parseCoreParts(corePart: String): List<Int>? {
    val dotParts = corePart.split('.')
    val isValid =
        corePart.isNotEmpty() &&
            dotParts.isNotEmpty() &&
            dotParts.size <= MAX_VERSION_PARTS &&
            dotParts.all(::isNumericOnly)
    if (!isValid) return null
    return dotParts.mapNotNull { it.toIntOrNull() }.takeIf { it.size == dotParts.size }
}

fun compareVersions(
    a: ParsedVersion,
    b: ParsedVersion,
): Int {
    val coreDiff = compareCoreParts(a.parts, b.parts)
    return if (coreDiff != 0) coreDiff else comparePreRelease(a.pre, b.pre)
}

private fun compareCoreParts(
    partsA: List<Int>,
    partsB: List<Int>,
): Int {
    val maxParts = max(partsA.size, partsB.size)
    for (i in 0 until maxParts) {
        val partA = partsA.getOrElse(i) { 0 }
        val partB = partsB.getOrElse(i) { 0 }
        if (partA != partB) return partA.compareTo(partB)
    }
    return 0
}

private fun comparePreRelease(
    preA: String?,
    preB: String?,
): Int =
    when {
        preA == null && preB == null -> 0
        preA == null -> 1
        preB == null -> -1
        else -> comparePrePieces(preA.split('.'), preB.split('.'))
    }

private fun comparePrePieces(
    piecesA: List<String>,
    piecesB: List<String>,
): Int {
    val minPieces = min(piecesA.size, piecesB.size)
    for (i in 0 until minPieces) {
        val pieceA = piecesA[i]
        val pieceB = piecesB[i]
        if (pieceA != pieceB) {
            val numA = pieceA.toLongOrNull()
            val numB = pieceB.toLongOrNull()
            return if (numA != null && numB != null) {
                numA.compareTo(numB)
            } else {
                pieceA.compareTo(pieceB)
            }
        }
    }
    return piecesA.size.compareTo(piecesB.size)
}

fun compareVersions(
    a: String,
    b: String,
): Int {
    val parsedA = parseVersion(a) ?: throw IllegalArgumentException("Unparseable version: $a")
    val parsedB = parseVersion(b) ?: throw IllegalArgumentException("Unparseable version: $b")
    return compareVersions(parsedA, parsedB)
}

fun isNewerVersion(
    candidateTag: String,
    currentName: String,
): Boolean {
    val candidate = parseVersion(candidateTag)
    val current = parseVersion(currentName)
    return if (candidate != null && current != null) {
        compareVersions(candidate, current) > 0
    } else {
        false
    }
}
