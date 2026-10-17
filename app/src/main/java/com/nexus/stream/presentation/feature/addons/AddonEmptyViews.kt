package com.nexus.stream.presentation.feature.addons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.nexus.stream.R
import com.nexus.stream.domain.model.AddonCategory
import com.nexus.stream.presentation.common.NexusButton
import com.nexus.stream.presentation.common.NexusCard
import com.nexus.stream.presentation.common.NexusOutlinedButton
import com.nexus.stream.presentation.common.NexusTextButton
import com.nexus.stream.presentation.theme.NexusTheme

@Composable
fun AddonSectionHeader(
    category: AddonCategory,
    modifier: Modifier = Modifier,
) {
    val titleRes =
        when (category) {
            AddonCategory.CATALOGS -> R.string.addon_category_catalogs
            AddonCategory.STREAMS -> R.string.addon_category_streams
            AddonCategory.SUBTITLES -> R.string.addon_category_subtitles
            AddonCategory.OTHER -> R.string.addon_category_other
        }
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(top = NexusTheme.spacing.sm, bottom = NexusTheme.spacing.xs),
    )
}

@Composable
fun EmptyCategoryView(
    category: AddonCategory,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing
    val emptyMessage =
        when (category) {
            AddonCategory.CATALOGS -> stringResource(R.string.addon_empty_catalogs)
            AddonCategory.STREAMS -> stringResource(R.string.addon_empty_streams)
            AddonCategory.SUBTITLES -> stringResource(R.string.addon_empty_subtitles)
            AddonCategory.OTHER -> stringResource(R.string.addon_empty_other)
        }
    Box(
        modifier = modifier.fillMaxSize().padding(spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(
                text = emptyMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            NexusButton(onClick = onInstall) {
                Text(stringResource(R.string.addon_toolbar_install))
            }
        }
    }
}

@Composable
fun EmptySearchMatchesView(modifier: Modifier = Modifier) {
    val spacing = NexusTheme.spacing
    Box(
        modifier = modifier.fillMaxSize().padding(top = spacing.xl),
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(
            text = stringResource(R.string.addon_search_no_matches),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SeedFailedCard(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing
    NexusCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.addon_manager_seed_failed_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            NexusTextButton(onClick = onRetry) {
                Text(
                    text = stringResource(R.string.addon_manager_retry),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
fun EmptyStateView(
    onAddCinemeta: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing
    Box(
        modifier = modifier.fillMaxSize().padding(spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(
                text = stringResource(R.string.addon_manager_empty_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.addon_manager_empty_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(spacing.sm))
            NexusOutlinedButton(onClick = onAddCinemeta) {
                Text(stringResource(R.string.addon_manager_add_cinemeta))
            }
        }
    }
}
