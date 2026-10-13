package com.nexus.stream.dev

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nexus.stream.R
import com.nexus.stream.presentation.theme.NexusTheme
import com.nexus.stream.presentation.theme.ThemeUiState
import com.nexus.stream.presentation.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DevToolsActivity : ComponentActivity() {
    private val themeViewModel: ThemeViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState by themeViewModel.uiState.collectAsState()
            when (val state = uiState) {
                is ThemeUiState.Loading -> {}

                is ThemeUiState.Ready -> {
                    val effectiveSettings =
                        state.settings.copy(
                            accentColor = state.previewAccent ?: state.settings.accentColor,
                        )
                    NexusTheme(settings = effectiveSettings) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background,
                        ) {
                            var showGallery by rememberSaveable { mutableStateOf(false) }

                            if (showGallery) {
                                BackHandler { showGallery = false }
                                ThemeGalleryScreen(
                                    settings = effectiveSettings,
                                    onEvent = themeViewModel::onEvent,
                                )
                            } else {
                                Scaffold(
                                    topBar = {
                                        TopAppBar(
                                            title = { Text(stringResource(R.string.dev_tools_label)) },
                                        )
                                    },
                                ) { innerPadding ->
                                    LazyColumn(
                                        modifier =
                                            Modifier
                                                .fillMaxSize()
                                                .padding(innerPadding),
                                    ) {
                                        item {
                                            ListItem(
                                                headlineContent = {
                                                    Text(stringResource(R.string.dev_tools_theme_gallery))
                                                },
                                                modifier =
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .clickable { showGallery = true },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
