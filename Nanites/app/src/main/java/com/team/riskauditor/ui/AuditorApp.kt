package com.team.riskauditor.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.automirrored.outlined.Rule as RuleOutlined
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.team.riskauditor.AppContainer
import com.team.riskauditor.ui.apps.AppsScreen
import com.team.riskauditor.ui.components.LocalIconLoader
import com.team.riskauditor.ui.detail.DetailScreen
import com.team.riskauditor.ui.home.HomeScreen
import com.team.riskauditor.ui.rubric.RubricScreen
import com.team.riskauditor.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable object HomeRoute

@Serializable
data class AppsRoute(val band: String? = null, val category: String? = null, val specialOnly: Boolean = false)

@Serializable data class DetailRoute(val packageName: String)

@Serializable object RubricRoute

@Serializable object SettingsRoute

private data class Tab(val route: Any, val routeClass: KClass<*>, val label: String, val selected: ImageVector, val idle: ImageVector)

private val tabs = listOf(
    Tab(HomeRoute, HomeRoute::class, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    Tab(AppsRoute(), AppsRoute::class, "Apps", Icons.Filled.Apps, Icons.Outlined.Apps),
    Tab(RubricRoute, RubricRoute::class, "Scoring", Icons.AutoMirrored.Filled.Rule, Icons.AutoMirrored.Outlined.RuleOutlined),
    Tab(SettingsRoute, SettingsRoute::class, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
)

@Composable
fun AuditorApp(container: AppContainer) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val showBar = destination?.hasRoute(DetailRoute::class) != true

    CompositionLocalProvider(LocalIconLoader provides container.iconLoader) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (showBar) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        tabs.forEach { tab ->
                            val selected = destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { nav.navigateTab(tab.route) },
                                icon = { Icon(if (selected) tab.selected else tab.idle, contentDescription = null) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(nav, startDestination = HomeRoute, modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
                composable<HomeRoute> {
                    HomeScreen(
                        container = container,
                        onOpenApps = { route -> nav.navigate(route) { popUpTo(nav.graph.findStartDestination().id); launchSingleTop = true } },
                        onOpenApp = { nav.navigate(DetailRoute(it)) },
                    )
                }
                composable<AppsRoute> {
                    AppsScreen(container = container, onOpenApp = { nav.navigate(DetailRoute(it)) })
                }
                composable<DetailRoute> {
                    DetailScreen(container = container, onBack = { nav.popBackStack() })
                }
                composable<RubricRoute> { RubricScreen(container.scoringConfig) }
                composable<SettingsRoute> {
                    SettingsScreen(container = container, onOpenRubric = { nav.navigateTab(RubricRoute) })
                }
            }
        }
    }
}

private fun NavHostController.navigateTab(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
