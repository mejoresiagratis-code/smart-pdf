package com.mejoresiagratis.rellenador.ui.wizard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DocumentsStep(state: WizardUiState, vm: WizardViewModel) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) vm.addDocuments(uris) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Paso 2 · Aporta la documentación del cliente", style = MaterialTheme.typography.titleMedium)
        Text("Fotos o PDF (DNI, escritura, certificado bancario…). La IA extraerá los datos del distribuidor.",
            style = MaterialTheme.typography.bodyMedium)

        OutlinedButton(
            onClick = { picker.launch(arrayOf("image/*", "application/pdf")) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Añadir documentos") }

        if (state.docUris.isNotEmpty()) {
            Text("${state.docUris.size} documento(s):", style = MaterialTheme.typography.labelLarge)
            Column(Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.docUris.forEach { uri ->
                    ElevatedCard {
                        ListItem(
                            headlineContent = { Text(uri.lastPathSegment?.substringAfterLast('/') ?: "documento") },
                            trailingContent = {
                                IconButton(onClick = { vm.removeDocument(uri) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Quitar")
                                }
                            }
                        )
                    }
                }
            }
        }

        HorizontalDivider()
        Text("Motores IA (con clave en servidor):", style = MaterialTheme.typography.labelLarge)
        if (state.availableProviders.isEmpty()) {
            Text("Comprobando motores disponibles…", style = MaterialTheme.typography.bodySmall)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.availableProviders.forEach { p ->
                FilterChip(
                    selected = p in state.enabledProviders,
                    onClick = { vm.toggleProvider(p) },
                    label = { Text(p.displayName + if (p.eu) " 🇪🇺" else "") }
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::back) { Text("Atrás") }
            Button(
                onClick = vm::runExtraction,
                enabled = state.canAdvanceFromDocs && !state.busy,
                modifier = Modifier.weight(1f)
            ) { Text("Analizar con IA") }
        }
    }
}
