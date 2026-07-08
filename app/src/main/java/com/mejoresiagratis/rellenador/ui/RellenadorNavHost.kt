package com.mejoresiagratis.rellenador.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mejoresiagratis.rellenador.ui.history.HistorialScreen
import com.mejoresiagratis.rellenador.ui.settings.AjustesScreen
import com.mejoresiagratis.rellenador.ui.wizard.WizardScreen

@Composable
fun RellenadorNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "wizard") {
        composable("wizard") {
            WizardScreen(
                onOpenSettings = { nav.navigate("ajustes") },
                onOpenHistory = { nav.navigate("historial") }
            )
        }
        composable("ajustes") { AjustesScreen(onBack = { nav.popBackStack() }) }
        composable("historial") { HistorialScreen(onBack = { nav.popBackStack() }) }
    }
}
