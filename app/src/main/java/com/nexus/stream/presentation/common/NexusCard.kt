package com.nexus.stream.presentation.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexus.stream.domain.model.CardStyle
import com.nexus.stream.presentation.theme.NexusTheme

@Composable
fun NexusCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val style = NexusTheme.cardStyle
    val shape = NexusTheme.shapes.card
    val amoled = NexusTheme.amoledActive
    val amoledBorder =
        if (amoled) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        } else {
            null
        }

    when (style) {
        CardStyle.FILLED -> {
            if (onClick != null) {
                Card(
                    onClick = onClick,
                    modifier = modifier,
                    shape = shape,
                    border = amoledBorder,
                    content = content,
                )
            } else {
                Card(
                    modifier = modifier,
                    shape = shape,
                    border = amoledBorder,
                    content = content,
                )
            }
        }

        CardStyle.OUTLINED -> {
            if (onClick != null) {
                OutlinedCard(
                    onClick = onClick,
                    modifier = modifier,
                    shape = shape,
                    content = content,
                )
            } else {
                OutlinedCard(
                    modifier = modifier,
                    shape = shape,
                    content = content,
                )
            }
        }

        CardStyle.ELEVATED -> {
            val elevatedModifier =
                if (amoledBorder != null) {
                    modifier.border(amoledBorder, shape)
                } else {
                    modifier
                }
            if (onClick != null) {
                ElevatedCard(
                    onClick = onClick,
                    modifier = elevatedModifier,
                    shape = shape,
                    content = content,
                )
            } else {
                ElevatedCard(
                    modifier = elevatedModifier,
                    shape = shape,
                    content = content,
                )
            }
        }
    }
}
