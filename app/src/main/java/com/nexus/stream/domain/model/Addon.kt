package com.nexus.stream.domain.model

enum class AddonSource {
    LOCAL,
    STREMIO_ACCOUNT,
}

data class Addon(
    val transportUrl: String,
    val manifest: AddonManifest,
    val enabled: Boolean,
    val position: Int,
    val source: AddonSource,
    val installedAt: Long,
) {
    val isAdult: Boolean get() = manifest.isAdult
}
