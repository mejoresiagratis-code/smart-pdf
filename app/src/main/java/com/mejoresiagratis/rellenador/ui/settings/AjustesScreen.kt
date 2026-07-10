package com.mejoresiagratis.rellenador.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mejoresiagratis.rellenador.ui.wizard.WizardViewModel

/**
 * Ajustes: perfil comercial (nombre que se autorrellena), URL del proxy (con
 * restaurar por defecto), y motores IA activos (persistidos). Reusa el mismo
 * WizardViewModel — comparte el mismo estado que el wizard, así los cambios se
 * ven reflejados de inmediato sin duplicar lógica.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjustesScreen(
    vm: WizardViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()
    var name by remember(state.responsableComercial) { mutableStateOf(state.responsableComercial) }
    var url by remember(state.proxyBaseUrlOverride) { mutableStateOf(state.proxyBaseUrlOverride) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // --- Perfil comercial ---
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Perfil comercial", style = MaterialTheme.typography.titleMedium)
                Text("Nombre que se autorrellena como Responsable Comercial MASORANGE en cada contrato.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Responsable comercial") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { vm.setResponsableComercial(name.trim()) },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Guardar nombre") }
            }

            HorizontalDivider()

            // --- URL del proxy ---
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("URL del proxy IA", style = MaterialTheme.typography.titleMedium)
                Text("Deja en blanco para usar la de fábrica (mejoresiagratis.com/pdf/). " +
                    "Solo cámbiala si tienes tu propio proxy compatible.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("https://tu-dominio.com/ruta/") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { url = ""; vm.setProxyBaseUrlOverride("") },
                        modifier = Modifier.weight(1f)
                    ) { Text("Restaurar por defecto") }
                    Button(
                        onClick = { vm.setProxyBaseUrlOverride(url.trim()) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Guardar URL") }
                }
            }

            HorizontalDivider()

            // --- Motores IA activos ---
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Motores IA activos", style = MaterialTheme.typography.titleMedium)
                Text("Se recuerdan entre sesiones. Solo se muestran los que tienen clave en el servidor.",
                    style = MaterialTheme.typography.bodySmall)
                if (state.availableProviders.isEmpty()) {
                    Text("Comprobando motores disponibles…", style = MaterialTheme.typography.bodySmall)
                }
                state.availableProviders.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(p.displayName + if (p.eu) " 🇪🇺" else "")
                        Switch(
                            checked = p in state.enabledProviders,
                            onCheckedChange = { vm.toggleProvider(p) }
                        )
                    }
                }
            }
        }
    }
}
