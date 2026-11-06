package com.expensetracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.expensetracker.app.ui.CategoriesScreen
import com.expensetracker.app.ui.ExpenseViewModel
import com.expensetracker.app.ui.HomeScreen
import com.expensetracker.app.ui.StatsScreen
import com.expensetracker.app.ui.theme.ExpenseTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ExpenseTrackerTheme {
                val nav = rememberNavController()
                // Created here (not per screen) so Home and Stats share the selected month.
                val vm: ExpenseViewModel = viewModel()
                NavHost(nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            vm,
                            onOpenStats = { nav.navigate("stats") },
                            onOpenCategories = { nav.navigate("categories") },
                        )
                    }
                    composable("stats") { StatsScreen(vm, onBack = { nav.navigateUp() }) }
                    composable("categories") {
                        CategoriesScreen(
                            vm,
                            groupId = null,
                            onBack = { nav.navigateUp() },
                            onOpenGroup = { id -> nav.navigate("categories/$id") },
                        )
                    }
                    composable(
                        "categories/{groupId}",
                        arguments = listOf(navArgument("groupId") { type = NavType.LongType }),
                    ) { entry ->
                        CategoriesScreen(
                            vm,
                            groupId = entry.arguments?.getLong("groupId"),
                            onBack = { nav.navigateUp() },
                            onOpenGroup = { id -> nav.navigate("categories/$id") },
                        )
                    }
                }
            }
        }
    }
}
