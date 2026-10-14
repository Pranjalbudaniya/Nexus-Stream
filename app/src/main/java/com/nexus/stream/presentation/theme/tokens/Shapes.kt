@file:Suppress("MatchingDeclarationName")

package com.nexus.stream.presentation.theme.tokens

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import kotlin.math.min

@Immutable
data class NexusShapes(
    val poster: CornerBasedShape = RoundedCornerShape(12.dp),
    val card: CornerBasedShape = RoundedCornerShape(16.dp),
    val sheet: CornerBasedShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    val chip: CornerBasedShape = RoundedCornerShape(8.dp),
    val button: CornerBasedShape = RoundedCornerShape(percent = 50),
)

fun nexusShapes(scale: Float): NexusShapes =
    NexusShapes(
        poster = RoundedCornerShape((12 * scale).dp),
        card = RoundedCornerShape((16 * scale).dp),
        sheet = RoundedCornerShape(topStart = (28 * scale).dp, topEnd = (28 * scale).dp),
        chip = RoundedCornerShape((8 * scale).dp),
        button = RoundedCornerShape(percent = min(50f, 50f * scale).toInt()),
    )

fun materialShapes(scale: Float): Shapes =
    Shapes(
        extraSmall = RoundedCornerShape((4 * scale).dp),
        small = RoundedCornerShape((8 * scale).dp),
        medium = RoundedCornerShape((12 * scale).dp),
        large = RoundedCornerShape((16 * scale).dp),
        extraLarge = RoundedCornerShape((28 * scale).dp),
    )

val LocalNexusShapes = staticCompositionLocalOf { nexusShapes(1f) }
