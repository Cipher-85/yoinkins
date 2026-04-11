package com.apkpackager.ui

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.apkpackager.ui.auth.LoginScreen
import com.apkpackager.ui.auth.LoginViewModel
import com.apkpackager.ui.branches.BranchPickerScreen
import com.apkpackager.ui.branches.BranchPickerViewModel
import com.apkpackager.ui.build.BuildDashboardScreen
import com.apkpackager.ui.build.BuildDashboardViewModel
import com.apkpackager.ui.repos.RepoListScreen
import com.apkpackager.ui.repos.RepoListViewModel
import java.net.URLDecoder
import java.net.URLEncoder

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            val vm: LoginViewModel = hiltViewModel()
            LoginScreen(
                viewModel = vm,
                onLoggedIn = { navController.navigate("repos") { popUpTo("login") { inclusive = true } } }
            )
        }
        composable("repos") {
            val vm: RepoListViewModel = hiltViewModel()
            RepoListScreen(
                viewModel = vm,
                onRepoSelected = { owner, repo, defaultBranch ->
                    val encodedOwner = URLEncoder.encode(owner, "UTF-8")
                    val encodedRepo = URLEncoder.encode(repo, "UTF-8")
                    val encodedBranch = URLEncoder.encode(defaultBranch, "UTF-8")
                    navController.navigate("branches/$encodedOwner/$encodedRepo/$encodedBranch")
                },
                onLogout = { navController.navigate("login") { popUpTo(0) { inclusive = true } } }
            )
        }
        composable(
            "branches/{owner}/{repo}/{defaultBranch}",
            arguments = listOf(
                navArgument("owner") { type = NavType.StringType },
                navArgument("repo") { type = NavType.StringType },
                navArgument("defaultBranch") { type = NavType.StringType }
            )
        ) { backStack ->
            val owner = URLDecoder.decode(backStack.arguments?.getString("owner") ?: "", "UTF-8")
            val repo = URLDecoder.decode(backStack.arguments?.getString("repo") ?: "", "UTF-8")
            val defaultBranch = URLDecoder.decode(backStack.arguments?.getString("defaultBranch") ?: "", "UTF-8")
            val vm: BranchPickerViewModel = hiltViewModel()
            BranchPickerScreen(
                viewModel = vm,
                owner = owner,
                repo = repo,
                defaultBranch = defaultBranch,
                onBranchSelected = { branch ->
                    val encodedOwner = URLEncoder.encode(owner, "UTF-8")
                    val encodedRepo = URLEncoder.encode(repo, "UTF-8")
                    val encodedBranch = URLEncoder.encode(branch, "UTF-8")
                    navController.navigate("build/$encodedOwner/$encodedRepo/$encodedBranch")
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            "build/{owner}/{repo}/{branch}",
            arguments = listOf(
                navArgument("owner") { type = NavType.StringType },
                navArgument("repo") { type = NavType.StringType },
                navArgument("branch") { type = NavType.StringType }
            )
        ) { backStack ->
            val owner = URLDecoder.decode(backStack.arguments?.getString("owner") ?: "", "UTF-8")
            val repo = URLDecoder.decode(backStack.arguments?.getString("repo") ?: "", "UTF-8")
            val branch = URLDecoder.decode(backStack.arguments?.getString("branch") ?: "", "UTF-8")
            val vm: BuildDashboardViewModel = hiltViewModel()
            BuildDashboardScreen(
                viewModel = vm,
                owner = owner,
                repo = repo,
                branch = branch,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
