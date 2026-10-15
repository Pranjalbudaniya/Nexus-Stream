package com.nexus.stream.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.nexus.stream.presentation.feature.addons.AddonManagerScreen
import com.nexus.stream.presentation.feature.discover.DiscoverScreen
import com.nexus.stream.presentation.feature.home.HomeScreen
import com.nexus.stream.presentation.feature.library.LibraryScreen
import com.nexus.stream.presentation.feature.search.SearchScreen
import com.nexus.stream.presentation.feature.settings.SettingsScreen

@Composable
fun NexusNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Home,
        modifier = modifier,
    ) {
        composable<Home> {
            HomeScreen(
                onNavigateToSettings = { navController.navigate(Settings) },
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
            )
        }
    }
}
