package com.nexus.stream

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.nexus.stream.presentation.theme.NexusTheme
import com.nexus.stream.presentation.theme.ThemeUiState
import com.nexus.stream.presentation.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val themeViewModel: ThemeViewModel by viewModels()

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
                        Surface(color = MaterialTheme.colorScheme.background) {
                            Text(text = stringResource(R.string.app_name))
                        }
                    }
                }
            }
        }
    }
}
