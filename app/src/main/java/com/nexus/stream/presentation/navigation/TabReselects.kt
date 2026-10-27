package com.nexus.stream.presentation.navigation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class TabReselects {
    private val _events = MutableSharedFlow<TopLevelDestination>(extraBufferCapacity = 1)
    val events: Flow<TopLevelDestination> = _events.asSharedFlow()

    fun onTabClick(
        destination: TopLevelDestination,
        isSelected: Boolean,
    ) {
        if (isSelected) {
            _events.tryEmit(destination)
        }
    }
}
