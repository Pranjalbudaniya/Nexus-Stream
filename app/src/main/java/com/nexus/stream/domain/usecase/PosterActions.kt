package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.ListStatus

enum class TrailerState {
    CHECKING,
    AVAILABLE,
    NONE,
}

sealed interface PosterAction {
    data object Play : PosterAction

    data object Detail : PosterAction

    data class Trailer(
        val state: TrailerState,
    ) : PosterAction

    data class Watchlist(
        val status: ListStatus?,
    ) : PosterAction

    data object RemoveFromContinue : PosterAction
}

fun posterActions(
    type: String,
    trailer: TrailerState,
    status: ListStatus?,
    fromContinue: Boolean,
): List<PosterAction> {
    val actions = mutableListOf<PosterAction>()
    if (!type.equals("series", ignoreCase = true)) {
        actions.add(PosterAction.Play)
    }
    actions.add(PosterAction.Detail)
    actions.add(PosterAction.Trailer(trailer))
    actions.add(PosterAction.Watchlist(status))
    if (fromContinue) {
        actions.add(PosterAction.RemoveFromContinue)
    }
    return actions
}
