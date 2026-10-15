package com.nexus.stream.presentation.navigation

import kotlinx.serialization.Serializable

@Serializable
data object Home

@Serializable
data object Discover

@Serializable
data object Search

@Serializable
data object Library

@Serializable
data object Settings

@Serializable
data class Addons(
    val prefillUrl: String? = null,
)

@Serializable
data class Detail(
    val type: String,
    val id: String,
)
