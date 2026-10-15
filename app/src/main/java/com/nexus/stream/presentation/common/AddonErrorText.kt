package com.nexus.stream.presentation.common

import androidx.annotation.StringRes
import com.nexus.stream.R
import com.nexus.stream.domain.model.AppError

data class AddonErrorText(
    @StringRes val resId: Int,
    val formatArgs: List<Any> = emptyList(),
)

fun AppError.toUserMessage(): AddonErrorText =
    when (this) {
        is AppError.Network -> AddonErrorText(R.string.error_addon_network)
        is AppError.Timeout -> AddonErrorText(R.string.error_addon_timeout)
        is AppError.NotFound -> AddonErrorText(R.string.error_addon_not_found)
        is AppError.Parse -> AddonErrorText(R.string.error_addon_parse)
        is AppError.InvalidUrl -> AddonErrorText(R.string.error_addon_invalid_url)
        is AppError.Http -> AddonErrorText(R.string.error_addon_http, listOf(code))
        is AppError.Unknown -> AddonErrorText(R.string.error_addon_unknown)
    }
