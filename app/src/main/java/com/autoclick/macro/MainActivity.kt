package com.autoclick.macro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.autoclick.macro.ui.AutoClickTheme
import com.autoclick.macro.ui.GuideScreen
import com.autoclick.macro.ui.HomeScreen
import com.autoclick.macro.ui.MainViewModel
import com.autoclick.macro.ui.PlanEditorScreen
import com.autoclick.macro.ui.rememberSetupState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AutoClickTheme {
                Surface(modifier = Modifier.fillMaxSize()) { AppRoot() }
            }
        }
    }
}

@Composable
private fun AppRoot(vm: MainViewModel = viewModel()) {
    val data by vm.data.collectAsStateWithLifecycle()
    val loaded = data
    if (loaded == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val nav = rememberNavController()
    val setup = rememberSetupState()
    NavHost(
        navController = nav,
        startDestination = if (loaded.riskAccepted) "home" else "welcome",
    ) {
        composable("welcome") {
            GuideScreen(
                firstRun = true,
                onAccept = {
                    vm.acceptRisk()
                    nav.navigate("home") { popUpTo("welcome") { inclusive = true } }
                },
                onBack = {},
            )
        }
        composable("home") {
            HomeScreen(
                data = loaded,
                setup = setup,
                vm = vm,
                onOpenPlan = { nav.navigate("plan/$it") },
                onOpenGuide = { nav.navigate("guide") },
            )
        }
        composable("guide") {
            GuideScreen(firstRun = false, onAccept = {}, onBack = { nav.popBackStack() })
        }
        composable("plan/{id}") { entry ->
            val id = entry.arguments?.getString("id")
            PlanEditorScreen(
                plan = loaded.plans.firstOrNull { it.id == id },
                vm = vm,
                onBack = { nav.popBackStack() },
            )
        }
    }
}
