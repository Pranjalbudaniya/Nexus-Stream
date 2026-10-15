package com.nexus.stream

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nexus.stream.data.remote.addon.AddonUrlBuilder
import com.nexus.stream.presentation.common.OneShot
import com.nexus.stream.presentation.navigation.NexusApp
import com.nexus.stream.presentation.theme.NexusTheme
import com.nexus.stream.presentation.theme.ThemeUiState
import com.nexus.stream.presentation.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val themeViewModel: ThemeViewModel by viewModels()
    private val deepLinkHolder = OneShot<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleDeepLinkIntent(intent)
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
                            NexusApp(deepLinkHolder = deepLinkHolder)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        val uri = intent?.data?.toString() ?: return
        val url = AddonUrlBuilder.fromDeepLink(uri)
        if (url != null) {
            deepLinkHolder.set(url)
            intent.data = null
        }
    }
}
