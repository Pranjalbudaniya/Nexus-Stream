package com.nexus.stream.data.remote.addon

import com.nexus.stream.domain.model.AddonCategory
import com.nexus.stream.domain.model.StoreAddon
import com.nexus.stream.domain.model.categoriesOf
import com.nexus.stream.domain.model.isUsable
import com.nexus.stream.domain.model.primaryCategory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement

object StoreMappers {
    fun mapStoreAddons(
        jsonElement: JsonElement,
        json: Json,
    ): List<StoreAddon>? {
        val rawArray =
            when (jsonElement) {
                is JsonArray -> jsonElement
                is JsonObject -> jsonElement["addons"] as? JsonArray
                else -> null
            } ?: return null

        val seen = mutableSetOf<String>()
        val result = mutableListOf<StoreAddon>()
        for (element in rawArray) {
            val addon = mapSingleEntry(element, json) ?: continue
            if (seen.add(addon.transportUrl)) {
                result.add(addon)
            }
        }
        return result
    }

    private fun mapSingleEntry(
        element: JsonElement,
        json: Json,
    ): StoreAddon? {
        val obj = element as? JsonObject
        val normalized = obj?.let { extractNormalizedUrl(it) } ?: return null
        val manifestObj = obj["manifest"] as? JsonObject
        return if (manifestObj == null) {
            createFallback(normalized.toString(), normalized.host)
        } else {
            parseManifestObj(normalized.toString(), normalized.host, manifestObj, json)
        }
    }

    private fun extractNormalizedUrl(obj: JsonObject): okhttp3.HttpUrl? {
        val rawTransportUrl = (obj["transportUrl"] as? JsonPrimitive)?.contentOrNull
        val normalized = rawTransportUrl?.let { AddonUrlBuilder.normalize(it) }
        return normalized?.takeIf { it.scheme == "http" || it.scheme == "https" }
    }

    private fun parseManifestObj(
        url: String,
        host: String,
        manifestObj: JsonObject,
        json: Json,
    ): StoreAddon {
        val dto =
            try {
                json.decodeFromJsonElement<ManifestDto>(manifestObj)
            } catch (_: Exception) {
                null
            } ?: return createFallback(url, host)

        val parsed = AddonMappers.mapManifest(dto, json)
        val categories = categoriesOf(parsed)
        return StoreAddon(
            transportUrl = url,
            name = parsed.name.ifBlank { host },
            version = parsed.version.takeIf { it.isNotBlank() },
            description = parsed.description,
            logo = parsed.logo,
            types = parsed.types,
            resources = parsed.resources.map { it.name }.toSet(),
            categories = categories,
            primary = primaryCategory(categories),
            isAdult = parsed.isAdult,
            p2p = parsed.behaviorHints.p2p,
            configurable = parsed.behaviorHints.configurable,
            usable = parsed.isUsable,
            hasDetails = true,
        )
    }

    private fun createFallback(
        url: String,
        host: String,
    ): StoreAddon =
        StoreAddon(
            transportUrl = url,
            name = host,
            version = null,
            description = "",
            logo = null,
            types = emptyList(),
            resources = emptySet(),
            categories = setOf(AddonCategory.OTHER),
            primary = AddonCategory.OTHER,
            isAdult = false,
            p2p = false,
            configurable = false,
            usable = true,
            hasDetails = false,
        )
}
