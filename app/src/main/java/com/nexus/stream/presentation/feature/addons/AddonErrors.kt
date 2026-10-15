package com.nexus.stream.presentation.feature.addons

import com.nexus.stream.domain.model.AppError
import com.nexus.stream.presentation.common.toUserMessage as commonToUserMessage

typealias AddonErrorText = com.nexus.stream.presentation.common.AddonErrorText

fun AppError.toUserMessage(): AddonErrorText = commonToUserMessage()
