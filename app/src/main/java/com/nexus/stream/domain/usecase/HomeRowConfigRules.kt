package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.HomeRowConfig
import com.nexus.stream.domain.model.HomeRowSpec
import com.nexus.stream.domain.model.ManagedRow

fun orderRows(
    specs: List<HomeRowSpec>,
    config: HomeRowConfig,
): List<HomeRowSpec> {
    val specMap = specs.associateBy { it.key }
    val orderedSpecs = config.order.distinct().mapNotNull { specMap[it] }
    val orderedKeySet = orderedSpecs.map { it.key }.toSet()
    val remainingSpecs = specs.filter { it.key !in orderedKeySet }
    return orderedSpecs + remainingSpecs
}

fun applyRowConfig(
    specs: List<HomeRowSpec>,
    config: HomeRowConfig,
): List<HomeRowSpec> = orderRows(specs, config).filter { it.key !in config.hidden }

fun managedRows(
    specs: List<HomeRowSpec>,
    config: HomeRowConfig,
): List<ManagedRow> =
    orderRows(specs, config).map {
        ManagedRow(spec = it, hidden = it.key in config.hidden)
    }

fun moveRow(
    specs: List<HomeRowSpec>,
    config: HomeRowConfig,
    key: String,
    delta: Int,
): HomeRowConfig {
    val currentKeySet = specs.map { it.key }.toSet()
    val ordered = orderRows(specs, config)
    val index = ordered.indexOfFirst { it.key == key }
    val newIndex = index + delta
    if (index == -1 || newIndex !in ordered.indices) return config

    val newKeys = ordered.map { it.key }.toMutableList()
    val temp = newKeys[index]
    newKeys[index] = newKeys[newIndex]
    newKeys[newIndex] = temp

    val newHidden = config.hidden.filter { it in currentKeySet }.toSet()
    return HomeRowConfig(order = newKeys, hidden = newHidden)
}

fun setHidden(
    specs: List<HomeRowSpec>,
    config: HomeRowConfig,
    key: String,
    hidden: Boolean,
): HomeRowConfig {
    val currentKeySet = specs.map { it.key }.toSet()
    if (key !in currentKeySet) return config

    val ordered = orderRows(specs, config)
    val newKeys = ordered.map { it.key }
    val baseHidden = config.hidden.filter { it in currentKeySet }.toMutableSet()
    if (hidden) {
        baseHidden.add(key)
    } else {
        baseHidden.remove(key)
    }
    return HomeRowConfig(order = newKeys, hidden = baseHidden)
}
