package com.nexus.stream.presentation.feature.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ContextualFlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.nexus.stream.domain.usecase.cleanNames
import com.nexus.stream.presentation.theme.NexusTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AllItemsSheet(
    title: String,
    items: List<String>,
    isFlow: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
) {
    val cleaned = remember(items) { cleanNames(items) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = NexusTheme.shapes.sheet,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = NexusTheme.spacing.xl),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = NexusTheme.spacing.lg)
                        .padding(bottom = NexusTheme.spacing.md)
                        .semantics { heading() },
            )

            if (isFlow) {
                FlowRow(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = NexusTheme.spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
                ) {
                    cleaned.forEach { name ->
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            shape = NexusTheme.shapes.chip,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = NexusTheme.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
                ) {
                    items(cleaned, key = { it }) { name ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = NexusTheme.spacing.xs),
                        )
                    }
                }
            }
        }
    }
}
