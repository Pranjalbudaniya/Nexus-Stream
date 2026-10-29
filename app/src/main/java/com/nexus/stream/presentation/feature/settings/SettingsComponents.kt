package com.nexus.stream.presentation.feature.settings

import android.content.res.Configuration
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexus.stream.R
import com.nexus.stream.presentation.common.NexusCard
import com.nexus.stream.presentation.theme.NexusTheme

private const val CHEVRON_COLLAPSED_DEG = 0f
private const val CHEVRON_EXPANDED_DEG = 180f
private const val DISABLED_ROW_ALPHA = 0.5f

fun sectionTitleRes(section: SettingsSection): Int =
    when (section) {
        SettingsSection.APPEARANCE -> R.string.settings_section_appearance
        SettingsSection.CONTENT -> R.string.settings_section_content
        SettingsSection.HOME_ROWS -> R.string.settings_section_home_rows_title
        SettingsSection.PLAYER -> R.string.settings_section_player
        SettingsSection.SUBTITLES -> R.string.settings_section_subtitles
        SettingsSection.TORRENT -> R.string.settings_section_torrent
        SettingsSection.ABOUT -> R.string.settings_section_about
    }

@Composable
fun SettingsSectionCard(
    section: SettingsSection,
    summary: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val spacing = NexusTheme.spacing
    val title = stringResource(sectionTitleRes(section))
    val rotation by animateFloatAsState(
        targetValue = if (expanded) CHEVRON_EXPANDED_DEG else CHEVRON_COLLAPSED_DEG,
        label = "chevron_rotation",
    )
    val clickLabel =
        stringResource(
            if (expanded) R.string.settings_section_collapse else R.string.settings_section_expand,
            title,
        )
    val stateDescription =
        stringResource(
            if (expanded) R.string.settings_state_expanded else R.string.settings_state_collapsed,
        )

    NexusCard(
        modifier =
            modifier
                .fillMaxWidth()
                .animateContentSize(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = clickLabel,
                            onClick = onToggle,
                        ).semantics {
                            this.role = Role.Button
                            this.stateDescription = stateDescription
                        }.padding(horizontal = spacing.md, vertical = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(end = spacing.sm),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (!expanded && !summary.isNullOrBlank()) {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.rotate(rotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                HorizontalDivider()
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.md, vertical = spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val spacing = NexusTheme.spacing
    val rowModifier =
        if (onClick != null && enabled) {
            modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
        } else {
            modifier.fillMaxWidth()
        }
    Row(
        modifier =
            rowModifier
                .defaultMinSize(minHeight = 48.dp)
                .padding(vertical = spacing.xs)
                .alpha(if (enabled) 1f else DISABLED_ROW_ALPHA),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(end = if (trailing != null) spacing.sm else 0.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        onClick =
            if (enabled) {
                { onCheckedChange(!checked) }
            } else {
                null
            },
        modifier = modifier,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                enabled = enabled,
            )
        },
    )
}

@Composable
fun SettingsValueRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        trailing = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Preview(name = "SettingsSectionCard - Collapsed")
@Composable
internal fun SettingsSectionCardCollapsedPreview() {
    NexusTheme {
        SettingsSectionCard(
            section = SettingsSection.APPEARANCE,
            summary = "Dark · Violet · Inter",
            expanded = false,
            onToggle = {},
        ) {}
    }
}

@Preview(name = "SettingsSectionCard - Expanded", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun SettingsSectionCardExpandedPreview() {
    NexusTheme {
        SettingsSectionCard(
            section = SettingsSection.APPEARANCE,
            summary = "Dark · Violet · Inter",
            expanded = true,
            onToggle = {},
        ) {
            SettingsValueRow(title = "Font", value = "Inter")
        }
    }
}
