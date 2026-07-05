package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.data.local.AppDatabase
import com.example.di.ServiceLocator
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewer.ViewerScreen
import com.example.ui.viewer.ViewerViewModel
import com.example.ui.settings.SettingsScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle cold start from external files (ACTION_VIEW / Share Sheet)
        val intentUri = handleIncomingIntent(intent)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    // Initialize Repositories and ViewModels via custom factories
                    val docRepository = remember { ServiceLocator.provideDocumentRepository(applicationContext) }
                    val aiRepository = remember { ServiceLocator.provideAiRepository(applicationContext) }
                    val annotationDao = remember { AppDatabase.getDatabase(applicationContext).annotationDao() }

                    val homeViewModel: HomeViewModel = viewModel(
                        factory = HomeViewModel.provideFactory(docRepository, aiRepository)
                    )
                    
                    val viewerViewModel: ViewerViewModel = viewModel(
                        factory = ViewerViewModel.provideFactory(
                            applicationContext,
                            docRepository,
                            aiRepository,
                            annotationDao
                        )
                    )

                    NavHost(
                        navController = navController,
                        startDestination = if (intentUri != null) "viewer/{uri}" else "home"
                    ) {
                        // Home screen
                        composable("home") {
                            HomeScreen(
                                viewModel = homeViewModel,
                                onNavigateToViewer = { uri ->
                                    val encodedUri = Uri.encode(uri.toString())
                                    navController.navigate("viewer/$encodedUri")
                                },
                                onNavigateToSettings = {
                                    navController.navigate("settings")
                                }
                            )
                        }

                        // Viewer screen
                        composable(
                            route = "viewer/{uri}",
                            arguments = listOf(navArgument("uri") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val encodedUri = backStackEntry.arguments?.getString("uri") ?: ""
                            val uriString = Uri.decode(encodedUri)
                            val finalUri = if (uriString.isNotEmpty()) Uri.parse(uriString) else intentUri

                            if (finalUri != null) {
                                ViewerScreen(
                                    viewModel = viewerViewModel,
                                    uri = finalUri,
                                    onNavigateBack = {
                                        // Navigate back to home or finish activity if opened via intent
                                        if (intentUri != null) {
                                            finish()
                                        } else {
                                            navController.popBackStack()
                                        }
                                    }
                                )
                            }
                        }

                        // Settings screen
                        composable("settings") {
                            SettingsScreen(
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun handleIncomingIntent(intent: Intent?): Uri? {
        if (intent == null) return null
        val action = intent.action
        val type = intent.type

        if (Intent.ACTION_VIEW == action && type != null) {
            return intent.data
        } else if (Intent.ACTION_SEND == action && type != null) {
            // Share-to-open
            (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let { uri ->
                return uri
            }
        }
        return null
    }
}
