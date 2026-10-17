package com.nexus.stream.presentation.feature.streams

import com.nexus.stream.domain.model.TypeFilter

object StreamFilterMemory {
    var resolution: Int? = null
    var language: String? = null
    var type: TypeFilter = TypeFilter.ALL
    var cachedOnly: Boolean = false

    fun reset() {
        resolution = null
        language = null
        type = TypeFilter.ALL
        cachedOnly = false
    }
}
