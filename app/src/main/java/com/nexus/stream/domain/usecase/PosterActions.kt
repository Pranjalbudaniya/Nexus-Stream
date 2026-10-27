package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.ListStatus

enum class TrailerState {
    CHECKING,
    AVAILABLE,
    NONE,
}

sealed interface PosterAction {
    data object Play : PosterAction

    data class Resume(
        val videoId: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
    ) : PosterAction

    data object Detail : PosterAction

    data class Trailer(
        val state: TrailerState,
    ) : PosterAction

    data class Watchlist(
        val status: ListStatus?,
    ) : PosterAction

    data object RemoveFromContinue : PosterAction

    data object RemoveFromHistory : PosterAction
}

@Suppress("LongParameterList")
fun posterActions(
    type: String,
    trailer: TrailerState,
    status: ListStatus?,
    fromContinue: Boolean,
    resume: PosterAction.Resume? = null,
    fromHistory: Boolean = false,
): List<PosterAction> {
    val actions = mutableListOf<PosterAction>()
    if (resume != null) {
        actions.add(resume)
    } else if (!type.equals("series", ignoreCase = true)) {
        actions.add(PosterAction.Play)
    }
    actions.add(PosterAction.Detail)
    actions.add(PosterAction.Trailer(trailer))
    actions.add(PosterAction.Watchlist(status))
    if (fromContinue) {
        actions.add(PosterAction.RemoveFromContinue)
    }
    if (fromHistory) {
        actions.add(PosterAction.RemoveFromHistory)
    }
    return actions
}
