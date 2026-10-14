package com.nexus.stream.data.remote.addon

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

fun JsonElement?.asStringOrNull(): String? =
    if (this is JsonPrimitive && this !is JsonNull) {
        this.content.ifBlank { null }
    } else {
        null
    }

fun JsonElement?.asIntOrNull(): Int? =
    if (this is JsonPrimitive && this !is JsonNull) {
        this.intOrNull ?: this.content.toIntOrNull()
    } else {
        null
    }

fun JsonElement?.asLongOrNull(): Long? =
    if (this is JsonPrimitive && this !is JsonNull) {
        this.longOrNull ?: this.content.toLongOrNull()
    } else {
        null
    }

fun JsonElement?.asBooleanOrNull(): Boolean? =
    if (this is JsonPrimitive && this !is JsonNull) {
        this.booleanOrNull ?: this.content.toBooleanStrictOrNull()
    } else {
        null
    }
