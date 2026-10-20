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

    data class Torrent(
        val reason: TorrentFailReason,
    ) : AppError

    data object MeteredBlocked : AppError

    data class Unknown(
        val message: String? = null,
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

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> =
    when (this) {
        is Outcome.Success -> Outcome.Success(transform(value))
        is Outcome.Failure -> this
    }
