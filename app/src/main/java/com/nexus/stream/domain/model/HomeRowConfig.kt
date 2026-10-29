package com.nexus.stream.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val MAX_CONFIG_KEYS = 500

private val homeRowConfigJson =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

@Serializable
data class HomeRowConfig(
    val order: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
) {
    companion object {
        fun decode(json: String?): HomeRowConfig = decodeHomeRowConfig(json)

        fun encode(config: HomeRowConfig): String = encodeHomeRowConfig(config)
    }
}

data class ManagedRow(
    val spec: HomeRowSpec,
    val hidden: Boolean,
)

fun encodeHomeRowConfig(config: HomeRowConfig): String =
    try {
        homeRowConfigJson.encodeToString(config)
    } catch (_: Exception) {
        "{}"
    }

fun decodeHomeRowConfig(json: String?): HomeRowConfig {
    if (json.isNullOrBlank()) return HomeRowConfig()
    return try {
        val parsed = homeRowConfigJson.decodeFromString<HomeRowConfig>(json)
        val cleanOrder =
            parsed.order
                .filter { it.isNotBlank() }
                .distinct()
                .take(MAX_CONFIG_KEYS)
        val cleanHidden =
            parsed.hidden
                .filter { it.isNotBlank() }
                .distinct()
                .take(MAX_CONFIG_KEYS)
                .toSet()
        HomeRowConfig(order = cleanOrder, hidden = cleanHidden)
    } catch (_: Exception) {
        HomeRowConfig()
    }
}
