package com.nexus.stream.presentation.feature.streams

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexus.stream.R
import com.nexus.stream.domain.model.StreamSort
import com.nexus.stream.domain.model.TypeFilter
import com.nexus.stream.domain.usecase.LanguageCodes
import com.nexus.stream.presentation.theme.NexusTheme

private const val RES_4K = 2160
private const val ICON_SIZE_WARN = 16

@Composable
fun FilterBar(
    uiState: StreamsUiState,
    onEvent: (StreamsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (uiState.showResolutionFilter) {
            ResolutionDropdownChip(uiState) { onEvent(StreamsEvent.SelectResolution(it)) }
        }

        if (uiState.showAddonFilter) {
            AddonDropdownChip(uiState) { onEvent(StreamsEvent.SelectAddon(it)) }
        }

        if (uiState.showLanguageFilter) {
            LanguageDropdownChip(uiState) { onEvent(StreamsEvent.SelectLanguage(it)) }
        }

        if (uiState.showTypeFilter) {
            TypeDropdownChip(uiState) { onEvent(StreamsEvent.SelectType(it)) }
        }

        if (uiState.showCachedFilter) {
            CachedToggleChip(
                cachedOnly = uiState.cachedOnly,
                onToggle = { onEvent(StreamsEvent.ToggleCached) },
            )
        }

        SortDropdownChip(uiState.sort) { onEvent(StreamsEvent.SelectSort(it)) }
    }
}

@Composable
private fun ResolutionDropdownChip(
    uiState: StreamsUiState,
    onSelectResolution: (Int?) -> Unit,
) {
    val selectedRes = uiState.selectedResolution
    val selectedLabel =
        when (selectedRes) {
            null -> stringResource(R.string.streams_filter_resolution_title)
            RES_4K -> "4K"
            else -> "${selectedRes}p"
        }

    val options = mutableListOf<DropdownOption<Int>>()
    options.add(
        DropdownOption(
            value = null,
            label = stringResource(R.string.streams_filter_all_resolutions),
            count = uiState.allResolutionsCount,
            selected = selectedRes == null,
        ),
    )
    uiState.availableResolutions.forEach { res ->
        options.add(
            DropdownOption(
                value = res,
                label = if (res == RES_4K) "4K" else "${res}p",
                count = uiState.resolutionCounts[res] ?: 0,
                selected = selectedRes == res,
            ),
        )
    }

    FilterDropdownChip(
        selectedLabel = selectedLabel,
        isNonDefault = selectedRes != null,
        options = options,
        onSelect = onSelectResolution,
    )
}

@Composable
private fun AddonDropdownChip(
    uiState: StreamsUiState,
    onSelectAddon: (String?) -> Unit,
) {
    val selectedAddon = uiState.selectedAddon
    val selectedLabel =
        if (selectedAddon == null) {
            stringResource(R.string.streams_addon_all)
        } else {
            uiState.availableAddons.firstOrNull { it.transportUrl == selectedAddon }?.displayName
                ?: stringResource(R.string.streams_filter_addon_title)
        }

    val options = mutableListOf<DropdownOption<String>>()
    options.add(
        DropdownOption(
            value = null,
            label = stringResource(R.string.streams_addon_all),
            count = uiState.allAddonsCount,
            selected = selectedAddon == null,
        ),
    )
    uiState.availableAddons.forEach { addon ->
        options.add(
            DropdownOption(
                value = addon.transportUrl,
                label = addon.displayName,
                count = addon.count,
                selected = selectedAddon == addon.transportUrl,
                leadingIcon =
                    if (addon.failed) {
                        {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(ICON_SIZE_WARN.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    } else {
                        null
                    },
            ),
        )
    }

    FilterDropdownChip(
        selectedLabel = selectedLabel,
        isNonDefault = selectedAddon != null,
        options = options,
        onSelect = onSelectAddon,
    )
}

@Composable
private fun LanguageDropdownChip(
    uiState: StreamsUiState,
    onSelectLanguage: (String?) -> Unit,
) {
    val selectedLang = uiState.selectedLanguage
    val selectedLabel =
        if (selectedLang == null) {
            stringResource(R.string.streams_filter_language_title)
        } else {
            LanguageCodes.languageDisplayName(selectedLang)
        }

    val options = mutableListOf<DropdownOption<String>>()
    options.add(
        DropdownOption(
            value = null,
            label = stringResource(R.string.streams_filter_any_language),
            count = uiState.anyLanguageCount,
            selected = selectedLang == null,
        ),
    )
    uiState.availableLanguages.forEach { code ->
        options.add(
            DropdownOption(
                value = code,
                label = LanguageCodes.languageDisplayName(code),
                count = uiState.languageCounts[code] ?: 0,
                selected = selectedLang == code,
            ),
        )
    }

    FilterDropdownChip(
        selectedLabel = selectedLabel,
        isNonDefault = selectedLang != null,
        options = options,
        onSelect = onSelectLanguage,
    )
}

@Composable
private fun TypeDropdownChip(
    uiState: StreamsUiState,
    onSelectType: (TypeFilter) -> Unit,
) {
    val selectedType = uiState.selectedType
    val selectedLabel =
        when (selectedType) {
            TypeFilter.ALL -> stringResource(R.string.streams_filter_type_title)
            TypeFilter.DIRECT -> stringResource(R.string.streams_filter_type_direct)
            TypeFilter.TORRENT -> stringResource(R.string.streams_filter_type_torrent)
        }

    val options =
        listOf(
            DropdownOption(
                value = TypeFilter.ALL,
                label = stringResource(R.string.streams_filter_all_types),
                count = uiState.allTypesCount,
                selected = selectedType == TypeFilter.ALL,
            ),
            DropdownOption(
                value = TypeFilter.DIRECT,
                label = stringResource(R.string.streams_filter_type_direct),
                count = uiState.typeDirectCount,
                selected = selectedType == TypeFilter.DIRECT,
            ),
            DropdownOption(
                value = TypeFilter.TORRENT,
                label = stringResource(R.string.streams_filter_type_torrent),
                count = uiState.typeTorrentCount,
                selected = selectedType == TypeFilter.TORRENT,
            ),
        )

    FilterDropdownChip(
        selectedLabel = selectedLabel,
        isNonDefault = selectedType != TypeFilter.ALL,
        options = options,
        onSelect = { onSelectType(it ?: TypeFilter.ALL) },
    )
}

@Composable
private fun CachedToggleChip(
    cachedOnly: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = cachedOnly,
        onClick = onToggle,
        label = { Text(stringResource(R.string.streams_filter_cached)) },
        shape = NexusTheme.shapes.chip,
        colors =
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        modifier = modifier.heightIn(min = 48.dp),
    )
}

@Composable
private fun SortDropdownChip(
    sort: StreamSort,
    onSelectSort: (StreamSort) -> Unit,
) {
    val selectedLabel = stringResource(R.string.streams_filter_sort_label, sortDisplayName(sort))
    val options =
        StreamSort.entries.map { s ->
            DropdownOption(
                value = s,
                label = sortDisplayName(s),
                selected = sort == s,
            )
        }

    FilterDropdownChip(
        selectedLabel = selectedLabel,
        isNonDefault = sort != StreamSort.BEST,
        options = options,
        onSelect = { it?.let(onSelectSort) },
    )
}

@Composable
private fun sortDisplayName(sort: StreamSort): String =
    when (sort) {
        StreamSort.BEST -> stringResource(R.string.streams_filter_sort_best)
        StreamSort.QUALITY -> stringResource(R.string.streams_filter_sort_quality)
        StreamSort.SEEDERS -> stringResource(R.string.streams_filter_sort_seeders)
        StreamSort.SIZE -> stringResource(R.string.streams_filter_sort_size)
    }
