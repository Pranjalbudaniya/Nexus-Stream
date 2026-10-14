package com.nexus.stream.domain.model

sealed interface AppError {
    data object Network : AppError

    data object Timeout : AppError

    data class Http(
        val code: Int,
    ) : AppError

    data object Parse : AppError

    data object NotFound : AppError

    data object InvalidUrl : AppError

    data class Unknown(
        val message: String?,
    ) : AppError
}

sealed interface Outcome<out T> {
    data class Success<out T>(
        val value: T,
    ) : Outcome<T>

    data class Failure(
        val error: AppError,
    ) : Outcome<Nothing>
}
