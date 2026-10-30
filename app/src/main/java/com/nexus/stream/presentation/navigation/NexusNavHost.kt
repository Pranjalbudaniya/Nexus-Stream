package com.nexus.stream.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.nexus.stream.presentation.feature.addons.AddonManagerScreen
import com.nexus.stream.presentation.feature.detail.DetailScreen
import com.nexus.stream.presentation.feature.discover.DiscoverScreen
import com.nexus.stream.presentation.feature.downloads.DownloadsScreen
import com.nexus.stream.presentation.feature.home.HomeScreen
import com.nexus.stream.presentation.feature.library.LibraryScreen
import com.nexus.stream.presentation.feature.player.PlaybackSessionHolder
import com.nexus.stream.presentation.feature.player.PlayerScreen
import com.nexus.stream.presentation.feature.settings.SettingsScreen
import kotlinx.coroutines.flow.filter

@Composable
fun NexusNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    playbackSessionHolder: PlaybackSessionHolder? = null,
    tabReselects: TabReselects = remember { TabReselects() },
) {
    LaunchedEffect(playbackSessionHolder) {
        playbackSessionHolder?.openRequests?.collect {
            if (navController.currentDestination?.hasRoute<Player>() != true) {
                navController.navigate(Player)
            }
        }
    }

    val discoverReselects =
        remember(tabReselects) {
            tabReselects.events.filter { it == TopLevelDestination.DISCOVER }
        }

    val libraryReselects =
        remember(tabReselects) {
            tabReselects.events.filter { it == TopLevelDestination.LIBRARY }
        }

    val downloadsReselects =
        remember(tabReselects) {
            tabReselects.events.filter { it == TopLevelDestination.DOWNLOADS }
        }

    NavHost(
        navController = navController,
        startDestination = Home,
        modifier = modifier,
    ) {
        composable<Home> {
            HomeScreen(
                onNavigateToSettings = { navController.navigate(Settings) },
                onNavigateToAddons = { navController.navigate(Addons()) },
                onItemClick = { type, id ->
                    navController.navigate(Detail(type = type, id = id))
                },
            )
        }
        composable<Discover> {
            DiscoverScreen(
                onItemClick = { type, id ->
                    navController.navigate(Detail(type = type, id = id))
                },
                onNavigateToAddons = { navController.navigate(Addons()) },
                reselectEvents = discoverReselects,
            )
        }
        composable<Downloads> {
            DownloadsScreen(
                onItemClick = { type, id ->
                    navController.navigate(Detail(type = type, id = id))
                },
                onNavigateToDiscover = { navController.navigate(Discover) },
                onOpenSettings = { navController.navigate(Settings) },
                reselectEvents = downloadsReselects,
                playbackSessionHolder = playbackSessionHolder,
            )
        }
        composable<Library> {
            LibraryScreen(
                onItemClick = { type, id ->
                    navController.navigate(Detail(type = type, id = id))
                },
                onNavigateToDiscover = { navController.navigate(Discover) },
                reselectEvents = libraryReselects,
            )
        }
        composable<Settings> {
            SettingsScreen(
                onNavigateUp = { navController.navigateUp() },
                onNavigateToAddons = { navController.navigate(Addons()) },
            )
        }
        composable<Addons> { backStackEntry ->
            val args = backStackEntry.toRoute<Addons>()
            AddonManagerScreen(
                onNavigateUp = { navController.navigateUp() },
                prefillUrl = args.prefillUrl,
                storeCategory = args.storeCategory,
            )
        }
        composable<Detail> {
            DetailScreen(
                onNavigateUp = { navController.navigateUp() },
                onOpenAddons = { navController.navigate(Addons()) },
                onOpenDownloads = {
                    navController.navigate(Downloads) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
        composable<Player> {
            PlayerScreen(
                onNavigateUp = { navController.navigateUp() },
                onSwitchToNext = {
                    navController.navigate(Player) {
                        popUpTo<Player> { inclusive = true }
                    }
                },
                playbackSessionHolder = playbackSessionHolder,
            )
        }
    }
}
