package com.divarsmartsearch.app.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.divarsmartsearch.app.presentation.screens.aichat.ListingAiChatScreen
import com.divarsmartsearch.app.presentation.screens.history.HistoryScreen
import com.divarsmartsearch.app.presentation.screens.home.NewSearchViewModel
import com.divarsmartsearch.app.presentation.screens.home.SearchFormScreen
import com.divarsmartsearch.app.presentation.screens.home.SelectPermanentFiltersScreen
import com.divarsmartsearch.app.presentation.screens.permanentfilters.PermanentFiltersScreen
import com.divarsmartsearch.app.presentation.screens.results.ResultsScreen
import com.divarsmartsearch.app.presentation.screens.searches.SearchListScreen
import com.divarsmartsearch.app.presentation.screens.sellerreport.SellerReportScreen
import com.divarsmartsearch.app.presentation.screens.settings.SettingsScreen
import com.divarsmartsearch.app.presentation.screens.webview.DivarWebViewScreen
import com.divarsmartsearch.app.presentation.screens.webview.FilterPickerWebViewScreen

private val bottomNavItems = listOf(
    Triple(Destination.NewSearchGraph.route, "جستجوی جدید", Icons.Filled.Add),
    Triple(Destination.SearchList.route, "جستجوها", Icons.Filled.List),
    Triple(Destination.Results.route, "نتایج", Icons.Filled.Search),
    Triple(Destination.History.route, "تاریخچه", Icons.Filled.History),
    Triple(Destination.PermanentFiltersManagement.route, "فیلترها", Icons.Filled.PhoneDisabled),
    Triple(Destination.Settings.route, "تنظیمات", Icons.Filled.Settings),
)

@Composable
fun DivarNavGraph() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = { AppBottomBar(navController) }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.NewSearchGraph.route,
            modifier = Modifier.padding(padding),
        ) {
            // --- Nested graph: new-search creation (2-step wizard) ---
            navigation(
                startDestination = Destination.SearchForm.route,
                route = Destination.NewSearchGraph.route,
            ) {
                composable(Destination.SearchForm.route) { entry ->
                    val parentEntry = remember(entry) {
                        navController.getBackStackEntry(Destination.NewSearchGraph.route)
                    }
                    val viewModel: NewSearchViewModel = hiltViewModel(parentEntry)
                    SearchFormScreen(
                        viewModel = viewModel,
                        onContinue = { navController.navigate(Destination.SelectPermanentFilters.route) },
                        onOpenFilterPicker = { navController.navigate(Destination.FilterPicker.route) },
                    )
                }
                composable(Destination.SelectPermanentFilters.route) { entry ->
                    val parentEntry = remember(entry) {
                        navController.getBackStackEntry(Destination.NewSearchGraph.route)
                    }
                    val viewModel: NewSearchViewModel = hiltViewModel(parentEntry)
                    SelectPermanentFiltersScreen(
                        viewModel = viewModel,
                        onFinished = {
                            navController.navigate(Destination.SearchList.route) {
                                popUpTo(Destination.NewSearchGraph.route) { inclusive = true }
                            }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Destination.FilterPicker.route) { entry ->
                    val parentEntry = remember(entry) {
                        navController.getBackStackEntry(Destination.NewSearchGraph.route)
                    }
                    val viewModel: NewSearchViewModel = hiltViewModel(parentEntry)
                    FilterPickerWebViewScreen(
                        onDone = { pickedUrl ->
                            viewModel.updateSearchUrl(pickedUrl)
                            navController.popBackStack()
                        },
                        onCancel = { navController.popBackStack() },
                    )
                }
            }

            composable(Destination.SearchList.route) {
                SearchListScreen(
                    onEditSearch = { id ->
                        navController.navigate(Destination.EditSearchForm.createRoute(id))
                    },
                    onOpenBrowser = { id ->
                        navController.navigate(Destination.DivarWebView.createRoute(id))
                    },
                )
            }
            composable(Destination.Results.route) {
                ResultsScreen(
                    onOpenSellerReport = { phoneNumber ->
                        navController.navigate(Destination.SellerReport.createRoute(phoneNumber))
                    },
                    onOpenAiChat = { listingId ->
                        navController.navigate(Destination.ListingAiChat.createRoute(listingId))
                    },
                )
            }
            composable(Destination.History.route) { HistoryScreen() }
            composable(Destination.PermanentFiltersManagement.route) { PermanentFiltersScreen() }
            composable(Destination.Settings.route) { SettingsScreen() }

            composable(
                route = Destination.SellerReport.route,
                arguments = listOf(navArgument("phoneNumber") { type = NavType.StringType }),
            ) {
                SellerReportScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = Destination.ListingAiChat.route,
                arguments = listOf(navArgument("listingId") { type = NavType.IntType }),
            ) {
                ListingAiChatScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = Destination.DivarWebView.route,
                arguments = listOf(navArgument("searchId") { type = NavType.IntType }),
            ) { entry ->
                val searchId = entry.arguments?.getInt("searchId") ?: return@composable
                DivarWebViewScreen(searchId = searchId)
            }

            // --- Nested graph: edit an existing search (same 2-step wizard) ---
            navigation(
                startDestination = Destination.EditSearchForm.route,
                route = Destination.EditSearchGraph.route,
            ) {
                composable(
                    route = Destination.EditSearchForm.route,
                    arguments = listOf(navArgument("searchId") { type = NavType.IntType }),
                ) { entry ->
                    val parentEntry = remember(entry) {
                        navController.getBackStackEntry(Destination.EditSearchGraph.route)
                    }
                    val viewModel: NewSearchViewModel = hiltViewModel(parentEntry)
                    val searchId = entry.arguments?.getInt("searchId")
                    LaunchedEffect(searchId) {
                        searchId?.let { viewModel.loadForEdit(it) }
                    }

                    SearchFormScreen(
                        viewModel = viewModel,
                        onContinue = { navController.navigate(Destination.EditSelectPermanentFilters.route) },
                        onOpenFilterPicker = { navController.navigate(Destination.EditFilterPicker.route) },
                    )
                }
                composable(Destination.EditSelectPermanentFilters.route) { entry ->
                    val parentEntry = remember(entry) {
                        navController.getBackStackEntry(Destination.EditSearchGraph.route)
                    }
                    val viewModel: NewSearchViewModel = hiltViewModel(parentEntry)
                    SelectPermanentFiltersScreen(
                        viewModel = viewModel,
                        onFinished = {
                            navController.navigate(Destination.SearchList.route) {
                                popUpTo(Destination.EditSearchGraph.route) { inclusive = true }
                            }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Destination.EditFilterPicker.route) { entry ->
                    val parentEntry = remember(entry) {
                        navController.getBackStackEntry(Destination.EditSearchGraph.route)
                    }
                    val viewModel: NewSearchViewModel = hiltViewModel(parentEntry)
                    FilterPickerWebViewScreen(
                        onDone = { pickedUrl ->
                            viewModel.updateSearchUrl(pickedUrl)
                            navController.popBackStack()
                        },
                        onCancel = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        bottomNavItems.forEach { (route, label, icon) ->
            NavigationBarItem(
                selected = currentRoute == route ||
                    (route == Destination.NewSearchGraph.route &&
                        currentRoute in setOf(
                            Destination.SearchForm.route,
                            Destination.SelectPermanentFilters.route,
                        )),
                onClick = {
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.secondary,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
