package com.nexus.stream.presentation.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nexus.stream.R
import com.nexus.stream.presentation.common.OneShot
import com.nexus.stream.presentation.common.PosterActionsHost
import com.nexus.stream.presentation.common.StreamsSheetHost
import com.nexus.stream.presentation.feature.downloads.DownloadBadgeViewModel
import com.nexus.stream.presentation.feature.player.PlaybackSessionHolder

@Composable
fun NexusApp(
    deepLinkHolder: OneShot<String>,
    modifier: Modifier = Modifier,
    openDownloadsHolder: OneShot<Unit> = remember { OneShot() },
    navController: NavHostController = rememberNavController(),
    playbackSessionHolder: PlaybackSessionHolder? = null,
    downloadBadgeViewModel: DownloadBadgeViewModel = hiltViewModel(),
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val activeDownloadsCount by downloadBadgeViewModel.activeCount.collectAsStateWithLifecycle()

    val showBottomBar =
        TopLevelDestination.entries.any { destination ->
            currentDestination?.hasRoute(destination.route::class) == true
        }

    val pendingDeepLink = deepLinkHolder.state.value
    LaunchedEffect(pendingDeepLink) {
        if (pendingDeepLink != null) {
            val url = deepLinkHolder.consume()
            if (url != null) {
                navController.navigate(Addons(prefillUrl = url))
            }
        }
    }

    val pendingOpenDownloads = openDownloadsHolder.state.value
    LaunchedEffect(pendingOpenDownloads) {
        if (pendingOpenDownloads != null) {
            openDownloadsHolder.consume()
            navController.navigate(TopLevelDestination.DOWNLOADS.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val tabReselects = remember { TabReselects() }

    StreamsSheetHost(
        onNavigateToStore = { category ->
            navController.navigate(Addons(storeCategory = category))
        },
        onNavigateToDownloads = {
            navController.navigate(TopLevelDestination.DOWNLOADS.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        },
        onNavigateToSettings = {
            navController.navigate(Settings)
        },
    ) {
        PosterActionsHost(
            onNavigateToDetail = { type, id ->
                navController.navigate(Detail(type = type, id = id))
            },
        ) {
            Scaffold(
                modifier = modifier,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (showBottomBar) {
                        NavigationBar {
                            TopLevelDestination.entries.forEach { destination ->
                                val isSelected =
                                    currentDestination?.hasRoute(destination.route::class) == true
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = {
                                        tabReselects.onTabClick(destination, isSelected)
                                        navController.navigate(destination.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        val iconContent: @Composable () -> Unit = {
                                            val vector = destination.icon
                                            val resId = destination.iconResId
                                            if (vector != null) {
                                                Icon(
                                                    imageVector = vector,
                                                    contentDescription = stringResource(destination.labelResId),
                                                )
                                            } else if (resId != null) {
                                                Icon(
                                                    painter = painterResource(resId),
                                                    contentDescription = stringResource(destination.labelResId),
                                                )
                                            }
                                        }

                                        if (destination == TopLevelDestination.DOWNLOADS && activeDownloadsCount > 0) {
                                            val badgeDesc =
                                                stringResource(
                                                    R.string.downloads_badge_desc,
                                                    activeDownloadsCount,
                                                )
                                            BadgedBox(
                                                badge = {
                                                    Badge {
                                                        Text(
                                                            text = activeDownloadsCount.toString(),
                                                            modifier =
                                                                Modifier.semantics {
                                                                    contentDescription = badgeDesc
                                                                },
                                                        )
                                                    }
                                                },
                                            ) {
                                                iconContent()
                                            }
                                        } else {
                                            iconContent()
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(destination.labelResId),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                )
                            }
                        }
                    }
                },
            ) { innerPadding ->
                NexusNavHost(
                    navController = navController,
                    modifier =
                        Modifier
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding),
                    playbackSessionHolder = playbackSessionHolder,
                    tabReselects = tabReselects,
                )
            }
        }
    }
}
