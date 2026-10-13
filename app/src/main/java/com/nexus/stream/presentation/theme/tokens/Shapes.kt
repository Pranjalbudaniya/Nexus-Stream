@file:Suppress("MatchingDeclarationName")

package com.nexus.stream.presentation.theme.tokens

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

@Immutable
data class NexusShapes(
    val poster: CornerBasedShape = RoundedCornerShape(12.dp),
    val card: CornerBasedShape = RoundedCornerShape(16.dp),
    val sheet: CornerBasedShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    val chip: CornerBasedShape = RoundedCornerShape(8.dp),
)

val MaterialShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

val LocalNexusShapes = staticCompositionLocalOf { NexusShapes() }
