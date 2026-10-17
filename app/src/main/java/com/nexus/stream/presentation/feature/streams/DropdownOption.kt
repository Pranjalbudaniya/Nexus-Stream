package com.nexus.stream.presentation.feature.streams

import androidx.compose.runtime.Composable

data class DropdownOption<T>(
    val value: T?,
    val label: String,
    val count: Int? = null,
    val selected: Boolean = false,
    val leadingIcon: (@Composable () -> Unit)? = null,
)
