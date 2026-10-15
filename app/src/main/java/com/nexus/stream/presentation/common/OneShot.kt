package com.nexus.stream.presentation.common

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

class OneShot<T>(
    initialValue: T? = null,
) {
    private val mutableState = mutableStateOf(initialValue)
    val state: State<T?> = mutableState

    fun set(value: T) {
        mutableState.value = value
    }

    fun consume(): T? {
        val current = mutableState.value
        mutableState.value = null
        return current
    }
}
