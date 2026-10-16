package com.nexus.stream.presentation.feature.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.nexus.stream.R
import com.nexus.stream.domain.usecase.cleanNames
import com.nexus.stream.presentation.common.NexusTextButton
import com.nexus.stream.presentation.theme.NexusTheme

@Composable
fun ChipRowSection(
    title: String,
    items: List<String>,
    onAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cleaned = remember(items) { cleanNames(items) }
    if (cleaned.isEmpty()) return

    val listState = rememberLazyListState()
    val canScroll by remember {
        derivedStateOf {
            listState.canScrollForward || listState.canScrollBackward
        }
    }
    val allDescription = stringResource(R.string.detail_show_all_desc, title)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NexusTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            if (canScroll) {
                NexusTextButton(
                    onClick = onAll,
                    modifier = Modifier.semantics { contentDescription = allDescription },
                ) {
                    Text(
                        text = stringResource(R.string.detail_btn_all),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = NexusTheme.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
        ) {
            items(
                items = cleaned,
                key = { it },
            ) { item ->
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            text = item,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    shape = NexusTheme.shapes.chip,
                )
            }
        }
    }
}
