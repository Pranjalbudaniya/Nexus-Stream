package com.nexus.stream.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.nexus.stream.presentation.feature.addons.AddonManagerScreen
import com.nexus.stream.presentation.feature.detail.DetailScreen
import com.nexus.stream.presentation.feature.discover.DiscoverScreen
import com.nexus.stream.presentation.feature.home.HomeScreen
import com.nexus.stream.presentation.feature.library.LibraryScreen
import com.nexus.stream.presentation.feature.player.PlaybackSessionHolder
import com.nexus.stream.presentation.feature.player.PlayerScreen
import com.nexus.stream.presentation.feature.search.SearchScreen
import com.nexus.stream.presentation.feature.settings.SettingsScreen

@Composable
fun NexusNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    playbackSessionHolder: PlaybackSessionHolder? = null,
) {
    LaunchedEffect(playbackSessionHolder) {
        playbackSessionHolder?.openRequests?.collect {
            navController.navigate(Player)
        }
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
            DiscoverScreen()
        }
        composable<Search> {
            SearchScreen()
        }
        composable<Library> {
            LibraryScreen()
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
            )
        }
        composable<Player> {
            PlayerScreen(
                onNavigateUp = { navController.navigateUp() },
            )
        }
    }
}
