package com.nexus.stream.presentation.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nexus.stream.R
import com.nexus.stream.presentation.theme.tokens.AccentSwatches
import com.nexus.stream.presentation.theme.tokens.BrandAccent

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccentPicker(
    selectedAccent: Long?,
    recents: List<Long>,
    enabled: Boolean,
    onPick: (argb: Long) -> Unit,
    onCustom: () -> Unit,
    onTurnOffDynamic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val defaultAccentArgb = (255L shl 24) or (BrandAccent.toArgb().toLong() and 0x00FFFFFFL)
    val currentAccent = selectedAccent ?: defaultAccentArgb

    Column(
        modifier =
            modifier.then(
                if (!enabled) Modifier.alpha(DISABLED_ALPHA) else Modifier,
            ),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            AccentSwatches.forEach { swatch ->
                val swatchArgb = (255L shl 24) or (swatch.color.toArgb().toLong() and 0x00FFFFFFL)
                val isSelected = (swatchArgb and 0x00FFFFFFL) == (currentAccent and 0x00FFFFFFL)
                val swatchName = stringResource(swatch.nameRes)

                SwatchCircle(
                    color = swatch.color,
                    isSelected = isSelected,
                    contentDescription = swatchName,
                    enabled = enabled,
                    onClick = { onPick(swatchArgb) },
                )
            }

            recents.forEach { recentArgb ->
                val normalizedRecent = (255L shl 24) or (recentArgb and 0x00FFFFFFL)
                val isSelected = (normalizedRecent and 0x00FFFFFFL) == (currentAccent and 0x00FFFFFFL)
                val recentColor = Color(normalizedRecent.toInt()).copy(alpha = 1f)
                val hexDesc = recentColor.toHexString()

                SwatchCircle(
                    color = recentColor,
                    isSelected = isSelected,
                    contentDescription = hexDesc,
                    enabled = enabled,
                    onClick = { onPick(normalizedRecent) },
                )
            }

            AssistChip(
                onClick = onCustom,
                enabled = enabled,
                label = { Text(stringResource(R.string.accent_custom)) },
                modifier =
                    Modifier
                        .align(Alignment.CenterVertically)
                        .minimumInteractiveComponentSize(),
            )
        }

        if (!enabled) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.accent_dynamic_caption),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onTurnOffDynamic) {
                    Text(stringResource(R.string.turn_off_dynamic_color))
                }
            }
        }
    }
}

@Composable
private fun SwatchCircle(
    color: Color,
    isSelected: Boolean,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val checkColor = if (color.luminance() > 0.5f) Color.Black else Color.White

    Box(
        modifier =
            modifier
                .minimumInteractiveComponentSize()
                .size(48.dp)
                .semantics {
                    this.contentDescription = contentDescription
                    this.selected = isSelected
                }.then(
                    if (enabled) {
                        Modifier.selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (isSelected) {
                            Modifier.border(
                                BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface),
                                CircleShape,
                            )
                        } else {
                            Modifier.border(
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                CircleShape,
                            )
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = checkColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private const val DISABLED_ALPHA = 0.38f
