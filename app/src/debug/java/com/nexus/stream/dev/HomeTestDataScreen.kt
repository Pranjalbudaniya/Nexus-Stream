package com.nexus.stream.dev

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.nexus.stream.R
import com.nexus.stream.presentation.theme.NexusTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTestDataScreen(
    state: HomeTestDataUiState,
    onAddCinemeta: () -> Unit,
    onAddNoPoster: () -> Unit,
    onAddFinishedMovie: () -> Unit,
    onAddShortMovie: () -> Unit,
    onAddSeriesEpisode: () -> Unit,
    onClearTestData: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_test_data_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.addon_tester_back),
                        )
                    }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(NexusTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.md),
        ) {
            val status = state.status
            if (status != null) {
                val statusText =
                    when (status) {
                        is HomeTestDataStatus.Resource -> {
                            val arg = status.arg
                            if (arg != null) {
                                stringResource(status.resId, arg)
                            } else {
                                stringResource(status.resId)
                            }
                        }
                    }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(NexusTheme.spacing.md),
                    )
                }
            }

            Button(
                onClick = onAddCinemeta,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.home_test_data_add_cinemeta))
            }

            Button(
                onClick = onAddNoPoster,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.home_test_data_add_no_poster))
            }

            Button(
                onClick = onAddFinishedMovie,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.home_test_data_add_finished_movie))
            }

            Button(
                onClick = onAddShortMovie,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.home_test_data_add_short_movie))
            }

            Button(
                onClick = onAddSeriesEpisode,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.home_test_data_add_series_episode))
            }

            OutlinedButton(
                onClick = onClearTestData,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.home_test_data_clear_test_data))
            }
        }
    }
}

@Preview(name = "HomeTestDataScreen - Idle")
@Composable
internal fun HomeTestDataScreenPreview() {
    NexusTheme {
        HomeTestDataScreen(
            state = HomeTestDataUiState(),
            onAddCinemeta = {},
            onAddNoPoster = {},
            onAddFinishedMovie = {},
            onAddShortMovie = {},
            onAddSeriesEpisode = {},
            onClearTestData = {},
            onBack = {},
        )
    }
}
