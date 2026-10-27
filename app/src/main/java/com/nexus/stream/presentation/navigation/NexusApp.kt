package com.nexus.stream.presentation.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nexus.stream.presentation.common.OneShot
import com.nexus.stream.presentation.common.PosterActionsHost
import com.nexus.stream.presentation.common.StreamsSheetHost
import com.nexus.stream.presentation.feature.player.PlaybackSessionHolder

@Composable
fun NexusApp(
    deepLinkHolder: OneShot<String>,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    playbackSessionHolder: PlaybackSessionHolder? = null,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

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

    StreamsSheetHost(
        onNavigateToStore = { category ->
            navController.navigate(Addons(storeCategory = category))
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
                                NavigationBarItem(
                                    selected =
                                        currentDestination?.hasRoute(destination.route::class) == true,
                                    onClick = {
                                        navController.navigate(destination.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
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
                )
            }
        }
    }
}
