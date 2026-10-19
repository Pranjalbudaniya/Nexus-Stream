package com.nexus.stream.domain.usecase

sealed interface RangeSpec {
    data object Full : RangeSpec

    data class Partial(
        val start: Long,
        val endInclusive: Long,
    ) : RangeSpec

    data object Unsatisfiable : RangeSpec
}

private const val BYTES_PREFIX = "bytes="

fun parseRange(
    header: String?,
    length: Long,
): RangeSpec {
    val spec = extractRangeSpecString(header) ?: return RangeSpec.Full
    val dashIndex = spec.indexOf('-')
    val first = spec.substring(0, dashIndex)
    val second = spec.substring(dashIndex + 1)

    return when {
        first.isNotEmpty() && second.isNotEmpty() -> parseExplicitRange(first, second, length)
        first.isNotEmpty() && second.isEmpty() -> parseFromRange(first, length)
        first.isEmpty() && second.isNotEmpty() -> parseSuffixRange(second, length)
        else -> RangeSpec.Full
    }
}

private fun extractRangeSpecString(header: String?): String? {
    val trimmed = header?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val isValidPrefix = trimmed.startsWith(BYTES_PREFIX, ignoreCase = true)
    val hasNoComma = !trimmed.contains(',')
    val spec = if (isValidPrefix && hasNoComma) trimmed.substring(BYTES_PREFIX.length) else ""
    val isValidSpec = spec.isNotEmpty() && spec.contains('-') && !spec.contains(' ')
    return if (isValidSpec) spec else null
}

private fun parseExplicitRange(
    first: String,
    second: String,
    length: Long,
): RangeSpec {
    val areDigits = first.all { it.isDigit() } && second.all { it.isDigit() }
    val start = if (areDigits) first.toLongOrNull() else null
    val end = if (areDigits) second.toLongOrNull() else null

    return when {
        start == null || end == null || end < start -> RangeSpec.Full
        length <= 0L || start >= length -> RangeSpec.Unsatisfiable
        else -> RangeSpec.Partial(start, minOf(end, length - 1))
    }
}

private fun parseFromRange(
    first: String,
    length: Long,
): RangeSpec {
    val isDigits = first.isNotEmpty() && first.all { it.isDigit() }
    val start = if (isDigits) first.toLongOrNull() else null

    return when {
        start == null -> RangeSpec.Full
        length <= 0L || start >= length -> RangeSpec.Unsatisfiable
        else -> RangeSpec.Partial(start, length - 1)
    }
}

private fun parseSuffixRange(
    second: String,
    length: Long,
): RangeSpec {
    val isDigits = second.isNotEmpty() && second.all { it.isDigit() }
    val suffixLen = if (isDigits) second.toLongOrNull() else null

    return when {
        suffixLen == null -> RangeSpec.Full
        suffixLen == 0L || length <= 0L -> RangeSpec.Unsatisfiable
        else -> RangeSpec.Partial(maxOf(0L, length - suffixLen), length - 1)
    }
}
