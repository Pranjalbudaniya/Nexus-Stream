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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nexus.stream.R
import com.nexus.stream.data.torrent.CacheLedger
import com.nexus.stream.data.torrent.LedgerEntry
import com.nexus.stream.data.torrent.TorrentCacheFiles
import com.nexus.stream.presentation.theme.NexusTheme
import com.nexus.stream.presentation.theme.ThemeUiState
import com.nexus.stream.presentation.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DevToolsActivity : ComponentActivity() {
    private val themeViewModel: ThemeViewModel by viewModels()
    private val addonTesterViewModel: AddonTesterViewModel by viewModels()
    private val homeTestDataViewModel: HomeTestDataViewModel by viewModels()
    private val databaseDevViewModel: DatabaseDevViewModel by viewModels()
    private val torrentTesterViewModel: TorrentTesterViewModel by viewModels()
    private val proxyTesterViewModel: ProxyTesterViewModel by viewModels()

    @Inject lateinit var cacheLedger: CacheLedger

    @Inject lateinit var cacheFiles: TorrentCacheFiles

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DevToolsContent(
                themeViewModel = themeViewModel,
                addonTesterViewModel = addonTesterViewModel,
                homeTestDataViewModel = homeTestDataViewModel,
                databaseDevViewModel = databaseDevViewModel,
                torrentTesterViewModel = torrentTesterViewModel,
                proxyTesterViewModel = proxyTesterViewModel,
                cacheLedger = cacheLedger,
                cacheFiles = cacheFiles,
            )
        }
    }
}

@Composable
private fun DevToolsContent(
    themeViewModel: ThemeViewModel,
    addonTesterViewModel: AddonTesterViewModel,
    homeTestDataViewModel: HomeTestDataViewModel,
    databaseDevViewModel: DatabaseDevViewModel,
    torrentTesterViewModel: TorrentTesterViewModel,
    proxyTesterViewModel: ProxyTesterViewModel,
    cacheLedger: CacheLedger,
    cacheFiles: TorrentCacheFiles,
) {
    val uiState by themeViewModel.uiState.collectAsState()
    val state = uiState as? ThemeUiState.Ready ?: return
    val effectiveSettings =
        state.settings.copy(
            accentColor = state.previewAccent ?: state.settings.accentColor,
        )
    NexusTheme(settings = effectiveSettings) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            var currentScreen by rememberSaveable { mutableStateOf(DevScreen.MENU) }

            when (currentScreen) {
                DevScreen.THEME_GALLERY -> {
                    BackHandler { currentScreen = DevScreen.MENU }
                    ThemeGalleryScreen(
                        settings = effectiveSettings,
                        onEvent = themeViewModel::onEvent,
                    )
                }

                DevScreen.ADDON_TESTER -> {
                    BackHandler { currentScreen = DevScreen.MENU }
                    val testerState by addonTesterViewModel.uiState.collectAsState()
                    AddonTesterScreen(
                        state = testerState,
                        onEvent = addonTesterViewModel::onEvent,
                        onBack = { currentScreen = DevScreen.MENU },
                    )
                }

                DevScreen.HOME_TEST_DATA -> {
                    BackHandler { currentScreen = DevScreen.MENU }
                    val homeTestDataState by homeTestDataViewModel.uiState.collectAsState()
                    HomeTestDataScreen(
                        state = homeTestDataState,
                        onAddCinemeta = homeTestDataViewModel::addCinemetaItems,
                        onAddNoPoster = homeTestDataViewModel::addNoPosterItem,
                        onAddFinishedMovie = homeTestDataViewModel::addFinishedMovie,
                        onAddShortMovie = homeTestDataViewModel::addShortMovie,
                        onAddSeriesEpisode = homeTestDataViewModel::addSeriesEpisode,
                        onClearTestData = homeTestDataViewModel::clearTestData,
                        onBack = { currentScreen = DevScreen.MENU },
                    )
                }

                DevScreen.DATABASE -> {
                    BackHandler { currentScreen = DevScreen.MENU }
                    val dbState by databaseDevViewModel.state.collectAsState()
                    DatabaseDevScreen(
                        state = dbState,
                        onRefresh = databaseDevViewModel::refresh,
                        onBack = { currentScreen = DevScreen.MENU },
                    )
                }

                DevScreen.TORRENT_TESTER -> {
                    BackHandler { currentScreen = DevScreen.MENU }
                    val torrentState by torrentTesterViewModel.uiState.collectAsState()
                    TorrentTesterScreen(
                        state = torrentState,
                        effects = torrentTesterViewModel.effects,
                        onEvent = torrentTesterViewModel::onEvent,
                        onBack = { currentScreen = DevScreen.MENU },
                    )
                }

                DevScreen.PROXY_TESTER -> {
                    BackHandler { currentScreen = DevScreen.MENU }
                    val proxyState by proxyTesterViewModel.uiState.collectAsState()
                    ProxyTesterScreen(
                        state = proxyState,
                        effects = proxyTesterViewModel.effects,
                        onEvent = proxyTesterViewModel::onEvent,
                        onBack = { currentScreen = DevScreen.MENU },
                    )
                }

                DevScreen.MENU -> {
                    DevToolsMenu(
                        onNavigateGallery = { currentScreen = DevScreen.THEME_GALLERY },
                        onNavigateTester = { currentScreen = DevScreen.ADDON_TESTER },
                        onNavigateTorrentTester = { currentScreen = DevScreen.TORRENT_TESTER },
                        onNavigateProxyTester = { currentScreen = DevScreen.PROXY_TESTER },
                        onNavigateHomeTestData = { currentScreen = DevScreen.HOME_TEST_DATA },
                        onNavigateDatabase = { currentScreen = DevScreen.DATABASE },
                        cacheLedger = cacheLedger,
                        cacheFiles = cacheFiles,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevToolsMenu(
    cacheLedger: CacheLedger,
    cacheFiles: TorrentCacheFiles,
    onNavigateGallery: () -> Unit,
    onNavigateTester: () -> Unit,
    onNavigateTorrentTester: () -> Unit,
    onNavigateProxyTester: () -> Unit,
    onNavigateHomeTestData: () -> Unit,
    onNavigateDatabase: () -> Unit,
) {
    var ledgerEntries by remember { mutableStateOf<List<Pair<LedgerEntry, Pair<Int, Long>>>>(emptyList()) }
    LaunchedEffect(Unit) {
        val entries = cacheLedger.all()
        ledgerEntries =
            entries.map { entry ->
                val bytes = cacheFiles.bytesOnDisk(entry.infoHash, entry.fileIdx)
                val total = cacheFiles.fileSize(entry.infoHash, entry.fileIdx)
                val pct = if (total > 0L) (bytes * 100 / total).toInt() else 0
                val mib = total / (1024L * 1024L)
                entry to (pct to mib)
            }
    }
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
                            .clickable(onClick = onNavigateGallery),
                )
            }
            item {
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.dev_tools_addon_tester))
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNavigateTester),
                )
            }
            item {
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.dev_tools_torrent_tester))
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNavigateTorrentTester),
                )
            }
            item {
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.dev_tools_proxy_tester))
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNavigateProxyTester),
                )
            }
            item {
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.dev_tools_home_test_data))
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNavigateHomeTestData),
                )
            }
            item {
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.dev_tools_database))
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNavigateDatabase),
                )
            }
            items(ledgerEntries.size) { i ->
                val (entry, stats) = ledgerEntries[i]
                ListItem(
                    headlineContent = { Text(entry.key) },
                    supportingContent = { Text("${entry.state} | ${stats.first}% | ${stats.second} MiB") },
                )
            }
        }
    }
}

private enum class DevScreen {
    MENU,
    THEME_GALLERY,
    ADDON_TESTER,
    TORRENT_TESTER,
    PROXY_TESTER,
    HOME_TEST_DATA,
    DATABASE,
}
