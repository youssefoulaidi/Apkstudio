package com.apkstudio.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.apkstudio.app.ui.screens.AnalysisScreen
import com.apkstudio.app.ui.screens.BuildScreen
import com.apkstudio.app.ui.screens.ImportScreen
import com.apkstudio.app.ui.screens.LoginScreen
import com.apkstudio.app.ui.screens.RepoListScreen
import com.apkstudio.app.ui.screens.ResultScreen
import com.apkstudio.app.ui.screens.SettingsScreen
import com.apkstudio.app.ui.screens.UploadScreen
import com.apkstudio.app.ui.screens.WelcomeScreen

object Routes {
    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val REPOS = "repos"
    const val IMPORT = "import"
    const val ANALYSIS = "analysis"
    const val UPLOAD = "upload"
    const val BUILD = "build"
    const val RESULT = "result"
    const val SETTINGS = "settings"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.WELCOME) {
        composable(Routes.WELCOME) {
            WelcomeScreen(
                onLinked = {
                    nav.navigate(Routes.REPOS) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                },
                onLoginNeeded = {
                    nav.navigate(Routes.LOGIN) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                onDone = {
                    nav.navigate(Routes.REPOS) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.REPOS) {
            RepoListScreen(
                onRepoSelected = { nav.navigate(Routes.IMPORT) },
                onResumeBuild = { nav.navigate(Routes.BUILD) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onLoggedOut = {
                    nav.navigate(Routes.LOGIN) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.IMPORT) {
            ImportScreen(
                onAnalyzed = { nav.navigate(Routes.ANALYSIS) },
                onBack = { nav.popBackStack() }
            )
        }
        composable(Routes.ANALYSIS) {
            AnalysisScreen(
                onUpload = { nav.navigate(Routes.UPLOAD) },
                onBack = { nav.popBackStack() }
            )
        }
        composable(Routes.UPLOAD) {
            UploadScreen(
                onDispatched = { nav.navigate(Routes.BUILD) },
                onBack = { nav.popBackStack() }
            )
        }
        composable(Routes.BUILD) {
            BuildScreen(
                onFinished = { nav.navigate(Routes.RESULT) },
                onLeave = { nav.popBackStack(Routes.REPOS, false) }
            )
        }
        composable(Routes.RESULT) {
            ResultScreen(
                onNewBuild = { nav.popBackStack(Routes.REPOS, false) },
                onRebuild = {
                    nav.navigate(Routes.BUILD) {
                        popUpTo(Routes.RESULT) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onLoggedOut = {
                    nav.navigate(Routes.LOGIN) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                }
            )
        }
    }
}
