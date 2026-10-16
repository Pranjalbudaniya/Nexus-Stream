package com.nexus.stream.domain.usecase

fun cleanNames(list: List<String>): List<String> {
    val seen = mutableSetOf<String>()
    val result = mutableListOf<String>()
    for (item in list) {
        val trimmed = item.trim()
        if (trimmed.isNotEmpty()) {
            val lower = trimmed.lowercase()
            if (seen.add(lower)) {
                result.add(trimmed)
            }
        }
    }
    return result
}
