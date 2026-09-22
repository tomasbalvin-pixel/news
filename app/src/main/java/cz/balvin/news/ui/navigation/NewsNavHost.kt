package cz.balvin.news.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cz.balvin.news.R
import cz.balvin.news.ui.article.ArticleScreen
import cz.balvin.news.ui.article.ArticleViewModel
import cz.balvin.news.ui.feeds.FeedsScreen
import cz.balvin.news.ui.settings.SettingsScreen
import cz.balvin.news.ui.timeline.TimelineScreen

private enum class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    TIMELINE("timeline", R.string.tab_timeline, Icons.AutoMirrored.Filled.Article),
    FEEDS("feeds", R.string.tab_feeds, Icons.Filled.RssFeed),
    SETTINGS("settings", R.string.tab_settings, Icons.Filled.Settings),
}

private const val ROUTE_ARTICLE = "article/{${ArticleViewModel.ARG_ARTICLE_ID}}"

@Composable
fun NewsNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = TopLevelDestination.entries.any { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy
                        ?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.TIMELINE.route,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(TopLevelDestination.TIMELINE.route) {
                TimelineScreen(
                    onOpenArticle = { id -> navController.navigate("article/$id") },
                    contentPadding = innerPadding,
                )
            }
            composable(TopLevelDestination.FEEDS.route) {
                FeedsScreen(contentPadding = innerPadding)
            }
            composable(TopLevelDestination.SETTINGS.route) {
                SettingsScreen(contentPadding = innerPadding)
            }
            composable(
                route = ROUTE_ARTICLE,
                arguments = listOf(
                    navArgument(ArticleViewModel.ARG_ARTICLE_ID) { type = NavType.LongType }
                ),
            ) {
                ArticleScreen(onBack = navController::popBackStack)
            }
        }
    }
}
