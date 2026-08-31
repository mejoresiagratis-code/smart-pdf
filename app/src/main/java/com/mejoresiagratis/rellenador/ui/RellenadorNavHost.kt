package com.mejoresiagratis.rellenador.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mejoresiagratis.rellenador.ui.settings.AjustesScreen
import com.mejoresiagratis.rellenador.ui.wizard.LabelEditorScreen
import com.mejoresiagratis.rellenador.ui.wizard.WizardScreen
import com.mejoresiagratis.rellenador.ui.wizard.WizardViewModel

@Composable
fun RellenadorNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "wizard") {
        composable("wizard") { entry ->
            val vm: WizardViewModel = hiltViewModel(entry)
            WizardScreen(vm = vm, onOpenSettings = { nav.navigate("ajustes") })
        }
        composable("ajustes") {
            // Comparte la MISMA instancia del ViewModel que "wizard" (ligada a su
            // backstack entry), para que los cambios en Ajustes se vean al volver
            // sin necesidad de recargar nada manualmente.
            val wizardEntry = remember(nav) { nav.getBackStackEntry("wizard") }
            val vm: WizardViewModel = hiltViewModel(wizardEntry)
            AjustesScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onOpenLabelEditor = { nav.navigate("etiquetas") },
            )
        }
        composable("etiquetas") {
            // OJO: aquí NO se comparte el ViewModel del wizard, al contrario que en "ajustes".
            // El editor de etiquetas tiene su propio LabelEditorViewModel con ciclo de vida
            // ligado a esta entrada, que es justo lo que se quiere: analizar un PDF suelto no
            // debe tocar ni el paso actual, ni los documentos, ni la firma de la sesión en
            // curso. `hiltViewModel()` sin argumentos ya resuelve contra ESTA entrada.
            LabelEditorScreen(onBack = { nav.popBackStack() })
        }
    }
}
