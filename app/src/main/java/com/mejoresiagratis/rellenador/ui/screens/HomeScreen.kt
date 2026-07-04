package com.mejoresiagratis.rellenador.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Rellenador de Contratos") }) }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Motores IA activos", style = MaterialTheme.typography.titleMedium)
            state.enabledProviders.forEach { Text("• ${it.displayName}") }

            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }

            HorizontalDivider()
            Text("Campos extraídos: ${state.fields.size}",
                style = MaterialTheme.typography.titleMedium)
            state.fields.values.take(20).forEach {
                Text("${it.fieldName}: ${it.value}  (${it.source})")
            }

            Button(onClick = {
                // TODO: wire PDF picker -> text extraction -> vm.runExtraction(...)
            }) { Text("Seleccionar PDF y extraer") }
        }
    }
}
