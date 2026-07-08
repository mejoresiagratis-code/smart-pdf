package com.mejoresiagratis.rellenador.ui.wizard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WizardScreen(
    onOpenSettings: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    vm: WizardViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()

    // Al volver de Ajustes (esta pantalla vuelve a componerse), releer motores activos.
    LaunchedEffect(Unit) { vm.reloadEnabledProviders() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rellenador de Contratos") },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Default.History, contentDescription = "Historial")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            StepIndicator(current = state.step)
            HorizontalDivider()

            Box(Modifier.weight(1f)) {
                when (state.step) {
                    Step.CONTRATO -> ContractStep(state, vm)
                    Step.DOCUMENTOS -> DocumentsStep(state, vm)
                    Step.REVISION -> ReviewStep(state, vm)
                    Step.RELLENO -> FillStep(state, vm)
                    Step.FIRMA -> SignatureStep(state, vm)
                }
                if (state.busy) {
                    Surface(
                        Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)
                    ) {
                        Column(
                            Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(state.busyMsg, color = Color.White)
                        }
                    }
                }
            }

            state.error?.let { err ->
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(err, Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = vm::dismissError) { Text("OK") }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(current: Step) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Step.entries.forEach { s ->
            val active = s.index <= current.index
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Surface(
                    modifier = Modifier.size(28.dp),
                    shape = CircleShape,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                        Text("${s.index + 1}",
                            color = if (active) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(s.title, style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    fontWeight = if (s == current) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}
