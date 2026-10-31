package com.nexus.stream.presentation.feature.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.stream.R
import com.nexus.stream.domain.model.ThemeSettings
import com.nexus.stream.domain.usecase.LanguageCodes
import com.nexus.stream.presentation.common.NexusIconButton
import com.nexus.stream.presentation.theme.NexusTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val RES_4K = 2160
private const val RES_1080P = 1080
private const val RES_720P = 720
private const val RES_SD = 480
private const val SCROLL_MEASURE_DELAY_MS = 250L

@Composable
fun SettingsScreen(
    onNavigateUp: () -> Unit,
    onNavigateToAddons: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    appearanceViewModel: AppearanceViewModel = hiltViewModel(),
    homeRowsViewModel: HomeRowsSettingsViewModel = hiltViewModel(),
    torrentViewModel: TorrentSettingsViewModel = hiltViewModel(),
    downloadsViewModel: DownloadsSettingsViewModel = hiltViewModel(),
    aboutViewModel: AboutViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appearanceState by appearanceViewModel.uiState.collectAsStateWithLifecycle()
    val homeRowsState by homeRowsViewModel.uiState.collectAsStateWithLifecycle()
    val torrentState by torrentViewModel.uiState.collectAsStateWithLifecycle()
    val downloadsState by downloadsViewModel.uiState.collectAsStateWithLifecycle()
    val aboutState by aboutViewModel.uiState.collectAsStateWithLifecycle()
    val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    updateViewModel.onEvent(UpdateEvent.Resumed)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(torrentViewModel) {
        torrentViewModel.effects.collect { effect ->
            when (effect) {
                TorrentSettingsEffect.CacheCleared -> {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.settings_torrent_cache_cleared_toast),
                    )
                }

                TorrentSettingsEffect.ClearFailed -> {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.settings_torrent_cache_clear_failed_toast),
                    )
                }
            }
        }
    }

    val onOpenUrl: (String) -> Unit =
        remember(context, scope, snackbarHostState) {
            { url ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.settings_about_no_app),
                        )
                    }
                }
            }
        }

    LaunchedEffect(aboutViewModel) {
        aboutViewModel.effects.collect { effect ->
            when (effect) {
                is AboutEffect.OpenUrl -> onOpenUrl(effect.url)
            }
        }
    }

    LaunchedEffect(updateViewModel) {
        updateViewModel.effects.collect { effect ->
            when (effect) {
                is UpdateUiEffect.OpenInstallPermissionSettings -> {
                    try {
                        val intent =
                            Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:${effect.packageName}"),
                            )
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        try {
                            val fallback = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                            context.startActivity(fallback)
                        } catch (_: ActivityNotFoundException) {
                            snackbarHostState.showSnackbar(
                                context.getString(R.string.settings_about_no_app),
                            )
                        }
                    }
                }

                is UpdateUiEffect.OpenUrl -> {
                    onOpenUrl(effect.url)
                }

                is UpdateUiEffect.ShowUpdated -> {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.update_updated, effect.versionName),
                    )
                }
            }
        }
    }

    SettingsContent(
        uiState = uiState,
        appearanceState = appearanceState,
        homeRowsState = homeRowsState,
        torrentState = torrentState,
        downloadsState = downloadsState,
        aboutState = aboutState,
        updateState = updateState,
        onEvent = viewModel::onEvent,
        onAppearanceEvent = appearanceViewModel::onEvent,
        onHomeRowsEvent = homeRowsViewModel::onEvent,
        onTorrentEvent = torrentViewModel::onEvent,
        onDownloadsEvent = downloadsViewModel::onEvent,
        onAboutEvent = aboutViewModel::onEvent,
        onUpdateEvent = updateViewModel::onEvent,
        onOpenUrl = onOpenUrl,
        onNavigateUp = onNavigateUp,
        onNavigateToAddons = onNavigateToAddons,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsContent(
    uiState: SettingsUiState,
    appearanceState: AppearanceUiState,
    homeRowsState: HomeRowsUiState = HomeRowsUiState(),
    torrentState: TorrentSettingsUiState,
    downloadsState: DownloadsSettingsUiState = DownloadsSettingsUiState(),
    aboutState: AboutUiState,
    updateState: UpdateUi,
    onEvent: (SettingsEvent) -> Unit,
    onAppearanceEvent: (AppearanceEvent) -> Unit,
    onHomeRowsEvent: (HomeRowsEvent) -> Unit = {},
    onTorrentEvent: (TorrentSettingsEvent) -> Unit,
    onDownloadsEvent: (DownloadsSettingsEvent) -> Unit = {},
    onAboutEvent: (AboutEvent) -> Unit,
    onUpdateEvent: (UpdateEvent) -> Unit,
    onOpenUrl: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateToAddons: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val spacing = NexusTheme.spacing
    val scrollState = rememberScrollState()

    var expandedSections by rememberSaveable(stateSaver = SettingsSectionSaver) {
        mutableStateOf(initialSettingsSections())
    }
    var lastOpenedSection by remember { mutableStateOf<SettingsSection?>(null) }

    var columnCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var cardCoordinates by remember { mutableStateOf(mapOf<SettingsSection, LayoutCoordinates>()) }
    val bringIntoViewRequesters =
        remember {
            SettingsSection.entries.associateWith { BringIntoViewRequester() }
        }

    LaunchedEffect(lastOpenedSection, expandedSections) {
        val section = lastOpenedSection ?: return@LaunchedEffect
        if (section !in expandedSections) return@LaunchedEffect
        delay(SCROLL_MEASURE_DELAY_MS)
        val col = columnCoordinates ?: return@LaunchedEffect
        val card = cardCoordinates[section] ?: return@LaunchedEffect
        if (!col.isAttached || !card.isAttached) return@LaunchedEffect

        val cardTopInCol = col.localPositionOf(card, Offset.Zero).y
        val cardBottomInCol = cardTopInCol + card.size.height
        val currentScroll = scrollState.value
        val viewportHeight = scrollState.viewportSize.toFloat()

        val cardBottomInViewport = cardBottomInCol - currentScroll
        if (cardBottomInViewport > viewportHeight) {
            scrollState.animateScrollTo(cardTopInCol.roundToInt())
        }
    }

    fun toggleSection(section: SettingsSection) {
        val willExpand = section !in expandedSections
        expandedSections = expandedSections.toggle(section)
        if (willExpand) {
            lastOpenedSection = section
            if (section == SettingsSection.TORRENT) {
                onTorrentEvent(TorrentSettingsEvent.SectionOpened)
            }
        }
    }

    var showDisableDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showSkipLengthDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    NexusIconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(scrollState)
                    .onGloballyPositioned { columnCoordinates = it }
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            // 1. Appearance
            val isAppearanceExpanded = SettingsSection.APPEARANCE in expandedSections
            val appearanceRequester = bringIntoViewRequesters.getValue(SettingsSection.APPEARANCE)
            SettingsSectionCard(
                section = SettingsSection.APPEARANCE,
                summary = sectionSummary(isAppearanceExpanded, appearanceSummary(appearanceState.settings)),
                expanded = isAppearanceExpanded,
                onToggle = { toggleSection(SettingsSection.APPEARANCE) },
                modifier =
                    Modifier
                        .bringIntoViewRequester(appearanceRequester)
                        .onGloballyPositioned {
                            cardCoordinates = cardCoordinates + (SettingsSection.APPEARANCE to it)
                        },
            ) {
                AppearanceSection(
                    state = appearanceState,
                    onEvent = onAppearanceEvent,
                )
            }

            // 2. Addons and content
            AddonsAndContentSectionCard(
                uiState = uiState,
                isExpanded = SettingsSection.CONTENT in expandedSections,
                onToggle = { toggleSection(SettingsSection.CONTENT) },
                onNavigateToAddons = onNavigateToAddons,
                onSetFamilyMode = { onEvent(SettingsEvent.SetFamilyMode(it)) },
                onRequestDisableFamily = { showDisableDialog = true },
                requester = bringIntoViewRequesters.getValue(SettingsSection.CONTENT),
                onPositioned = { cardCoordinates = cardCoordinates + (SettingsSection.CONTENT to it) },
            )

            // 3. Home rows
            val isHomeRowsExpanded = SettingsSection.HOME_ROWS in expandedSections
            val homeRowsRequester = bringIntoViewRequesters.getValue(SettingsSection.HOME_ROWS)
            SettingsSectionCard(
                section = SettingsSection.HOME_ROWS,
                summary =
                    sectionSummary(
                        isHomeRowsExpanded,
                        homeRowsSummary(homeRowsState.totalCount, homeRowsState.hiddenCount),
                    ),
                expanded = isHomeRowsExpanded,
                onToggle = { toggleSection(SettingsSection.HOME_ROWS) },
                modifier =
                    Modifier
                        .bringIntoViewRequester(homeRowsRequester)
                        .onGloballyPositioned {
                            cardCoordinates = cardCoordinates + (SettingsSection.HOME_ROWS to it)
                        },
            ) {
                HomeRowsSection(
                    state = homeRowsState,
                    onEvent = onHomeRowsEvent,
                )
            }

            // 4. Player
            PlayerSectionCard(
                uiState = uiState,
                isExpanded = SettingsSection.PLAYER in expandedSections,
                onToggle = { toggleSection(SettingsSection.PLAYER) },
                onEvent = onEvent,
                onOpenLanguageDialog = { showLanguageDialog = true },
                onOpenQualityDialog = { showQualityDialog = true },
                onOpenSkipLengthDialog = { showSkipLengthDialog = true },
                requester = bringIntoViewRequesters.getValue(SettingsSection.PLAYER),
                onPositioned = { cardCoordinates = cardCoordinates + (SettingsSection.PLAYER to it) },
            )

            // 5. Subtitles and audio
            val isSubtitlesExpanded = SettingsSection.SUBTITLES in expandedSections
            val subtitlesRequester = bringIntoViewRequesters.getValue(SettingsSection.SUBTITLES)
            SettingsSectionCard(
                section = SettingsSection.SUBTITLES,
                summary =
                    sectionSummary(
                        isSubtitlesExpanded,
                        subtitlesSummary(uiState.preferredAudioLanguage, uiState.preferredSubtitleLanguage),
                    ),
                expanded = isSubtitlesExpanded,
                onToggle = { toggleSection(SettingsSection.SUBTITLES) },
                modifier =
                    Modifier
                        .bringIntoViewRequester(subtitlesRequester)
                        .onGloballyPositioned {
                            cardCoordinates = cardCoordinates + (SettingsSection.SUBTITLES to it)
                        },
            ) {
                SubtitleSettingsSection(
                    uiState = uiState,
                    onEvent = onEvent,
                )
            }

            // 6. Torrent
            val isTorrentExpanded = SettingsSection.TORRENT in expandedSections
            val torrentRequester = bringIntoViewRequesters.getValue(SettingsSection.TORRENT)
            SettingsSectionCard(
                section = SettingsSection.TORRENT,
                summary =
                    sectionSummary(
                        isTorrentExpanded,
                        torrentSummary(torrentState.wifiOnly, torrentState.cacheLimitGb),
                    ),
                expanded = isTorrentExpanded,
                onToggle = { toggleSection(SettingsSection.TORRENT) },
                modifier =
                    Modifier
                        .bringIntoViewRequester(torrentRequester)
                        .onGloballyPositioned {
                            cardCoordinates = cardCoordinates + (SettingsSection.TORRENT to it)
                        },
            ) {
                TorrentSection(
                    state = torrentState,
                    onEvent = onTorrentEvent,
                )
            }

            // 7. Downloads
            val isDownloadsExpanded = SettingsSection.DOWNLOADS in expandedSections
            val downloadsRequester = bringIntoViewRequesters.getValue(SettingsSection.DOWNLOADS)
            SettingsSectionCard(
                section = SettingsSection.DOWNLOADS,
                summary =
                    sectionSummary(
                        isDownloadsExpanded,
                        downloadsSummary(downloadsState.wifiOnly, downloadsState.maxConcurrent),
                    ),
                expanded = isDownloadsExpanded,
                onToggle = { toggleSection(SettingsSection.DOWNLOADS) },
                modifier =
                    Modifier
                        .bringIntoViewRequester(downloadsRequester)
                        .onGloballyPositioned {
                            cardCoordinates = cardCoordinates + (SettingsSection.DOWNLOADS to it)
                        },
            ) {
                DownloadsSection(
                    state = downloadsState,
                    onEvent = onDownloadsEvent,
                )
            }

            // 8. About
            val isAboutExpanded = SettingsSection.ABOUT in expandedSections
            val aboutRequester = bringIntoViewRequesters.getValue(SettingsSection.ABOUT)
            val aboutSummaryText =
                aboutSummary(
                    versionName = aboutState.versionName,
                    versionCode = aboutState.versionCode,
                    updateAvailable = updateState.summaryFlag,
                    updateSuffix = stringResource(R.string.settings_summary_about_update),
                )
            SettingsSectionCard(
                section = SettingsSection.ABOUT,
                summary = sectionSummary(isAboutExpanded, aboutSummaryText),
                expanded = isAboutExpanded,
                onToggle = { toggleSection(SettingsSection.ABOUT) },
                modifier =
                    Modifier
                        .bringIntoViewRequester(aboutRequester)
                        .onGloballyPositioned {
                            cardCoordinates = cardCoordinates + (SettingsSection.ABOUT to it)
                        },
            ) {
                AboutSection(
                    state = aboutState,
                    updateState = updateState,
                    onEvent = onAboutEvent,
                    onUpdateEvent = onUpdateEvent,
                    onOpenUrl = onOpenUrl,
                )
            }
        }

        SettingsDialogs(
            showDisableDialog = showDisableDialog,
            showLanguageDialog = showLanguageDialog,
            showQualityDialog = showQualityDialog,
            showSkipLengthDialog = showSkipLengthDialog,
            uiState = uiState,
            onEvent = onEvent,
            onDismissDisable = { showDisableDialog = false },
            onDismissLanguage = { showLanguageDialog = false },
            onDismissQuality = { showQualityDialog = false },
            onDismissSkipLength = { showSkipLengthDialog = false },
        )

        if (updateState.showMeteredDialog) {
            ConfirmMeteredDialog(
                sizeBytes = updateState.meteredSizeBytes,
                onEvent = onUpdateEvent,
            )
        }

        if (updateState.showPermissionDialog) {
            InstallPermissionDialog(
                onEvent = onUpdateEvent,
            )
        }
    }
}

@Composable
private fun AddonsAndContentSectionCard(
    uiState: SettingsUiState,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onNavigateToAddons: () -> Unit,
    onSetFamilyMode: (Boolean) -> Unit,
    onRequestDisableFamily: () -> Unit,
    requester: BringIntoViewRequester,
    onPositioned: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSectionCard(
        section = SettingsSection.CONTENT,
        summary = sectionSummary(isExpanded, contentSummary(uiState.familyMode)),
        expanded = isExpanded,
        onToggle = onToggle,
        modifier =
            modifier
                .bringIntoViewRequester(requester)
                .onGloballyPositioned(onPositioned),
    ) {
        SettingsRow(
            title = stringResource(R.string.settings_manage_addons),
            onClick = onNavigateToAddons,
            trailing = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )

        val familySubtitle =
            if (uiState.familyMode && uiState.hiddenAdultAddonsCount > 0) {
                if (uiState.hiddenAdultAddonsCount == 1) {
                    stringResource(
                        R.string.settings_family_mode_subtitle_hidden,
                        uiState.hiddenAdultAddonsCount,
                    )
                } else {
                    stringResource(
                        R.string.settings_family_mode_subtitle_hidden_plural,
                        uiState.hiddenAdultAddonsCount,
                    )
                }
            } else {
                stringResource(R.string.settings_family_mode_subtitle)
            }
        SettingsSwitchRow(
            title = stringResource(R.string.settings_family_mode_title),
            subtitle = familySubtitle,
            checked = uiState.familyMode,
            onCheckedChange = { checked ->
                if (checked) {
                    onSetFamilyMode(true)
                } else {
                    onRequestDisableFamily()
                }
            },
        )
    }
}

@Composable
private fun PlayerSectionCard(
    uiState: SettingsUiState,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onEvent: (SettingsEvent) -> Unit,
    onOpenLanguageDialog: () -> Unit,
    onOpenQualityDialog: () -> Unit,
    onOpenSkipLengthDialog: () -> Unit,
    requester: BringIntoViewRequester,
    onPositioned: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSectionCard(
        section = SettingsSection.PLAYER,
        summary =
            sectionSummary(
                isExpanded,
                playerSummary(uiState.preferredQuality, uiState.seekStepSeconds),
            ),
        expanded = isExpanded,
        onToggle = onToggle,
        modifier =
            modifier
                .bringIntoViewRequester(requester)
                .onGloballyPositioned(onPositioned),
    ) {
        SettingsSwitchRow(
            title = stringResource(R.string.settings_quick_play_title),
            subtitle = stringResource(R.string.settings_quick_play_subtitle),
            checked = uiState.quickPlay,
            onCheckedChange = { onEvent(SettingsEvent.SetQuickPlay(it)) },
        )

        val prefLangLabel =
            uiState.preferredLanguage?.let { LanguageCodes.languageDisplayName(it) }
                ?: stringResource(R.string.settings_player_preferred_language_none)
        SettingsValueRow(
            title = stringResource(R.string.settings_player_preferred_language_title),
            value = prefLangLabel,
            onClick = onOpenLanguageDialog,
        )

        val prefQualityLabel =
            when (uiState.preferredQuality) {
                RES_4K -> stringResource(R.string.settings_player_preferred_quality_4k)
                RES_1080P -> stringResource(R.string.settings_player_preferred_quality_1080p)
                RES_720P -> stringResource(R.string.settings_player_preferred_quality_720p)
                RES_SD -> stringResource(R.string.settings_player_preferred_quality_sd)
                else -> stringResource(R.string.settings_player_preferred_quality_auto)
            }
        SettingsValueRow(
            title = stringResource(R.string.settings_player_preferred_quality_title),
            value = prefQualityLabel,
            onClick = onOpenQualityDialog,
        )

        SettingsSwitchRow(
            title = stringResource(R.string.settings_player_hide_cam_title),
            subtitle = stringResource(R.string.settings_player_hide_cam_subtitle),
            checked = uiState.hideCam,
            onCheckedChange = { onEvent(SettingsEvent.SetHideCam(it)) },
        )

        SettingsValueRow(
            title = stringResource(R.string.settings_player_skip_length_title),
            value =
                stringResource(
                    R.string.settings_player_skip_length_seconds,
                    uiState.seekStepSeconds,
                ),
            onClick = onOpenSkipLengthDialog,
        )

        SettingsSwitchRow(
            title = stringResource(R.string.settings_player_auto_play_next),
            subtitle = stringResource(R.string.settings_player_auto_play_next_desc),
            checked = uiState.autoPlayNext,
            onCheckedChange = { onEvent(SettingsEvent.SetAutoPlayNext(it)) },
        )
    }
}

@Preview(name = "SettingsScreen - Collapsed Light")
@Composable
internal fun SettingsScreenCollapsedLightPreview() {
    NexusTheme {
        SettingsContent(
            uiState =
                SettingsUiState(
                    familyMode = true,
                    quickPlay = false,
                    preferredLanguage = "en",
                    preferredQuality = 1080,
                    hideCam = false,
                ),
            appearanceState = AppearanceUiState(settings = ThemeSettings()),
            torrentState = TorrentSettingsUiState(),
            aboutState = AboutUiState(versionSummary = "Version 1.0.0"),
            updateState =
                UpdateUi(
                    status = StatusText(R.string.update_not_checked),
                    bigButtons = listOf(UiButton(R.string.update_check, UpdateEvent.CheckNow, isPrimary = true)),
                ),
            onEvent = {},
            onAppearanceEvent = {},
            onTorrentEvent = {},
            onAboutEvent = {},
            onUpdateEvent = {},
            onOpenUrl = {},
            onNavigateUp = {},
            onNavigateToAddons = {},
        )
    }
}

@Preview(name = "SettingsScreen - Collapsed Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun SettingsScreenCollapsedDarkPreview() {
    NexusTheme {
        SettingsContent(
            uiState =
                SettingsUiState(
                    familyMode = false,
                    quickPlay = true,
                    preferredLanguage = null,
                    preferredQuality = null,
                    hideCam = true,
                ),
            appearanceState = AppearanceUiState(settings = ThemeSettings()),
            torrentState = TorrentSettingsUiState(),
            aboutState = AboutUiState(versionSummary = "Version 1.0.0"),
            updateState =
                UpdateUi(
                    status = StatusText(R.string.update_not_checked),
                    bigButtons = listOf(UiButton(R.string.update_check, UpdateEvent.CheckNow, isPrimary = true)),
                ),
            onEvent = {},
            onAppearanceEvent = {},
            onTorrentEvent = {},
            onAboutEvent = {},
            onUpdateEvent = {},
            onOpenUrl = {},
            onNavigateUp = {},
            onNavigateToAddons = {},
        )
    }
}

@Preview(name = "SettingsScreen - Appearance Expanded")
@Composable
internal fun SettingsScreenAppearanceExpandedPreview() {
    NexusTheme {
        AppearanceSection(
            state = AppearanceUiState(settings = ThemeSettings()),
            onEvent = {},
        )
    }
}

@Preview(name = "SettingsScreen - Torrent Expanded")
@Composable
internal fun SettingsScreenTorrentExpandedPreview() {
    NexusTheme {
        TorrentSection(
            state = TorrentSettingsUiState(cacheLimitGb = 5),
            onEvent = {},
        )
    }
}

@Preview(name = "SettingsScreen - Downloads Expanded")
@Composable
internal fun SettingsScreenDownloadsExpandedPreview() {
    NexusTheme {
        DownloadsSection(
            state = DownloadsSettingsUiState(wifiOnly = true, maxConcurrent = 2),
            onEvent = {},
        )
    }
}

@Preview(name = "SettingsScreen - About Expanded")
@Composable
internal fun SettingsScreenAboutExpandedPreview() {
    NexusTheme {
        AboutSection(
            state = AboutUiState(versionText = "1.0.0 (10000)", versionSummary = "Version 1.0.0"),
            updateState =
                UpdateUi(
                    status = StatusText(R.string.update_not_checked),
                    bigButtons = listOf(UiButton(R.string.update_check, UpdateEvent.CheckNow, isPrimary = true)),
                ),
            onEvent = {},
            onUpdateEvent = {},
            onOpenUrl = {},
        )
    }
}
