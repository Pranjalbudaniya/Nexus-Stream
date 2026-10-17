package com.nexus.stream.domain.model

data class StoreAddon(
    val transportUrl: String,
    val name: String,
    val version: String?,
    val description: String,
    val logo: String?,
    val types: List<String>,
    val resources: Set<String>,
    val categories: Set<AddonCategory>,
    val primary: AddonCategory,
    val isAdult: Boolean,
    val p2p: Boolean,
    val configurable: Boolean,
    val usable: Boolean,
    val hasDetails: Boolean,
    val isInstalled: Boolean = false,
)
