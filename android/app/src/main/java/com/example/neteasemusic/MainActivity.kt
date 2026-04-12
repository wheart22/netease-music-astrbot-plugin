package com.example.neteasemusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.neteasemusic.data.repository.SettingsRepository
import com.example.neteasemusic.ui.player.PlayerScreen
import com.example.neteasemusic.ui.player.PlayerViewModel
import com.example.neteasemusic.ui.search.SearchScreen
import com.example.neteasemusic.ui.search.SearchViewModel
import com.example.neteasemusic.ui.settings.SettingsScreen
import com.example.neteasemusic.ui.settings.SettingsViewModel
import com.example.neteasemusic.ui.theme.NeteaseMusicTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NeteaseMusicTheme {
                val navController = rememberNavController()

                // Single shared SettingsRepository for the whole app.
                val settingsRepository = remember { SettingsRepository(applicationContext) }

                NavHost(
                    navController = navController,
                    startDestination = "search"
                ) {
                    composable("search") {
                        val vm: SearchViewModel = viewModel(
                            factory = SearchViewModel.Factory(settingsRepository)
                        )
                        SearchScreen(
                            viewModel = vm,
                            onSongClick = { songId ->
                                navController.navigate("player/$songId")
                            },
                            onSettingsClick = {
                                navController.navigate("settings")
                            }
                        )
                    }

                    composable(
                        route = "player/{songId}",
                        arguments = listOf(navArgument("songId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val songId = backStackEntry.arguments?.getLong("songId") ?: return@composable
                        val vm: PlayerViewModel = viewModel(
                            factory = PlayerViewModel.Factory(settingsRepository, applicationContext)
                        )
                        PlayerScreen(
                            songId = songId,
                            viewModel = vm,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable("settings") {
                        val vm: SettingsViewModel = viewModel(
                            factory = SettingsViewModel.Factory(settingsRepository)
                        )
                        SettingsScreen(
                            viewModel = vm,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
