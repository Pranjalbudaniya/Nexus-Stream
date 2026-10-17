package com.nexus.stream.presentation.feature.addons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nexus.stream.R
import com.nexus.stream.domain.model.AddonCategory
import com.nexus.stream.domain.model.StoreAddon
import com.nexus.stream.domain.usecase.toDisplayHost
import com.nexus.stream.presentation.common.NexusButton
import com.nexus.stream.presentation.common.NexusCard
import com.nexus.stream.presentation.theme.NexusTheme

@Composable
fun StoreAddonRow(
    addon: StoreAddon,
    isInstalling: Boolean,
    onInstall: (StoreAddon) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing

    NexusCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StoreAddonLogo(logoUrl = addon.logo, addonName = addon.name)

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                ) {
                    val titleText =
                        if (!addon.version.isNullOrBlank()) {
                            "${addon.name} v${addon.version}"
                        } else {
                            addon.name
                        }
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = addon.transportUrl.toDisplayHost(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                StoreAddonAction(
                    addon = addon,
                    isInstalling = isInstalling,
                    onInstall = onInstall,
                )
            }

            if (addon.description.isNotBlank()) {
                Text(
                    text = addon.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!addon.usable) {
                Text(
                    text = stringResource(R.string.addon_store_unsupported),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            StoreAddonChips(addon = addon)
        }
    }
}

@Composable
private fun StoreAddonAction(
    addon: StoreAddon,
    isInstalling: Boolean,
    onInstall: (StoreAddon) -> Unit,
) {
    Box(
        modifier = Modifier.heightIn(min = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isInstalling) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )
        } else if (addon.isInstalled) {
            NexusButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.addon_store_installed))
            }
        } else {
            NexusButton(
                onClick = { onInstall(addon) },
                enabled = addon.usable,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.addon_store_install))
            }
        }
    }
}

@Composable
private fun StoreAddonChips(
    addon: StoreAddon,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (addon.isAdult) {
            item(key = "badge_18") {
                AdultBadge()
            }
        }

        items(addon.categories.toList(), key = { "cat_${it.name}" }) { cat ->
            val labelRes =
                when (cat) {
                    AddonCategory.CATALOGS -> R.string.addon_category_catalogs
                    AddonCategory.STREAMS -> R.string.addon_category_streams
                    AddonCategory.SUBTITLES -> R.string.addon_category_subtitles
                    AddonCategory.OTHER -> R.string.addon_category_other
                }
            SuggestionChip(
                onClick = {},
                label = { Text(stringResource(labelRes), style = MaterialTheme.typography.labelSmall) },
                shape = NexusTheme.shapes.chip,
            )
        }

        items(addon.types, key = { "type_$it" }) { type ->
            SuggestionChip(
                onClick = {},
                label = { Text(type, style = MaterialTheme.typography.labelSmall) },
                shape = NexusTheme.shapes.chip,
            )
        }
    }
}

@Composable
private fun AdultBadge(modifier: Modifier = Modifier) {
    val spacing = NexusTheme.spacing
    Box(
        modifier =
            modifier
                .clip(NexusTheme.shapes.chip)
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(horizontal = spacing.xs, vertical = spacing.xxs),
    ) {
        Text(
            text = "18+",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun StoreAddonLogo(
    logoUrl: String?,
    addonName: String,
    modifier: Modifier = Modifier,
) {
    var isError by remember(logoUrl) { mutableStateOf(logoUrl.isNullOrBlank()) }
    val initial = addonName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    Box(
        modifier =
            modifier
                .size(width = 48.dp, height = 72.dp)
                .clip(NexusTheme.shapes.poster)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (!isError && !logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = logoUrl,
                contentDescription = stringResource(R.string.addon_logo_description, addonName),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { isError = true },
            )
        }
        if (isError || logoUrl.isNullOrBlank()) {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun StoreSkeletonRow(modifier: Modifier = Modifier) {
    val spacing = NexusTheme.spacing
    NexusCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(width = 48.dp, height = 72.dp)
                        .clip(NexusTheme.shapes.poster)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(width = 120.dp, height = 16.dp)
                            .clip(NexusTheme.shapes.button)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Box(
                    modifier =
                        Modifier
                            .size(width = 80.dp, height = 12.dp)
                            .clip(NexusTheme.shapes.button)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(SKELETON_WIDTH_FRACTION)
                            .heightIn(min = 12.dp)
                            .clip(NexusTheme.shapes.button)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
    }
}

private const val SKELETON_WIDTH_FRACTION = 0.8f
