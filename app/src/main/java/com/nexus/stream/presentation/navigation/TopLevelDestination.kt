package com.nexus.stream.presentation.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector
import com.nexus.stream.R

enum class TopLevelDestination(
    val route: Any,
    @StringRes val labelResId: Int,
    val icon: ImageVector? = null,
    @DrawableRes val iconResId: Int? = null,
) {
    HOME(
        route = Home,
        labelResId = R.string.nav_home,
        icon = Icons.Default.Home,
    ),
    DISCOVER(
        route = Discover,
        labelResId = R.string.nav_discover,
        icon = Icons.Default.Search,
    ),
    LIBRARY(
        route = Library,
        labelResId = R.string.nav_library,
        icon = Icons.AutoMirrored.Filled.List,
    ),
    DOWNLOADS(
        route = Downloads,
        labelResId = R.string.nav_downloads,
        iconResId = R.drawable.ic_download,
    ),
}
