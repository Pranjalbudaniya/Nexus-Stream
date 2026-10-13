@file:Suppress("MatchingDeclarationName")

package com.nexus.stream.presentation.theme.tokens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

@Immutable
data class MotionTokens(
    val durationShort: Int = 150,
    val durationMedium: Int = 300,
    val durationLong: Int = 500,
    val standardEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f),
    val emphasizedDecelerateEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f),
    val emphasizedAccelerateEasing: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f),
)

val LocalMotion = staticCompositionLocalOf { MotionTokens() }
