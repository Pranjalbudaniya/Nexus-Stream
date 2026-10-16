package com.nexus.stream.domain.model

sealed interface RowState {
    data object Loading : RowState

    data class Loaded(
        val items: List<MetaPreview>,
    ) : RowState

    data class Failed(
        val error: AppError = AppError.Unknown("Failed to load row"),
    ) : RowState
}
