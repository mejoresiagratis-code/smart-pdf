package com.mejoresiagratis.rellenador.ui.wizard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import com.mejoresiagratis.rellenador.ui.components.EngineChip
import com.mejoresiagratis.rellenador.ui.components.ExpressiveButton
import com.mejoresiagratis.rellenador.ui.components.ExpressiveSurface
import com.mejoresiagratis.rellenador.ui.components.MotorLoadingIndicator
import com.mejoresiagratis.rellenador.ui.components.TipBanner
import com.mejoresiagratis.rellenador.ui.components.blobShape

/**
 * Tanda 2 — Documentación, densidad y paddings alineados al criterio ya fijado en
 * ContractStep tras el ajuste de la otra sesión (20dp horizontal / 16dp vertical en
 * el contenedor exterior; SIN envolver todo en un único ExpressiveSurface gigante,
 * que sobrecargaba el padding y forzaba scroll de más).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DocumentsStep(state: WizardUiState, vm: WizardViewModel) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) vm.addDocuments(uris) }

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Paso 2 · Aporta la documentación del cliente",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Fotos o PDF (DNI, escritura, certificado bancario…). La IA extraerá los datos del distribuidor.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Tarjeta Expressive con icono en blob (mismo lenguaje visual que
            // ContractOptionCard) en vez del OutlinedButton plano anterior. El botón
            // real de acción va debajo — esta tarjeta es solo estado + contexto.
            ExpressiveSurface {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = blobShape(),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.UploadFile, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (state.docUris.isEmpty()) "Sin documentos todavía"
                            else "${state.docUris.size} documento(s) añadido(s)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Toca \"Añadir documentos\" para elegir fotos o PDF",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = { picker.launch(arrayOf("image/*", "application/pdf")) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Añadir documentos") }

            if (state.docUris.isNotEmpty()) {
                Text("Documentos:", style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.docUris.forEach { uri ->
                        ElevatedCard(shape = MaterialTheme.shapes.medium) {
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
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.availableProviders.forEach { p ->
                    EngineChip(
                        provider = p,
                        selected = p in state.enabledProviders,
                        active = state.busy && state.activeProvider == p,
                        onClick = { vm.toggleProvider(p) }
                    )
                }
            }

            TipBanner(
                "Los motores marcados con 🇪🇺 procesan los datos en servidores europeos."
            )

            // Indicador contextual: sustituye al texto plano "Analizando con IA…"
            // mientras dura la extracción, mostrando qué motor concreto trabaja ahora.
            AnimatedVisibility(
                visible = state.busy,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                MotorLoadingIndicator(
                    busyMsg = state.busyMsg,
                    activeProvider = state.activeProvider,
                    finishedProviders = state.finishedProviders,
                    enabledProviders = state.enabledProviders.toList()
                )
            }
        }

        // Acción primaria anclada (mismo criterio de paddings que ContractStep tras
        // el ajuste de la otra sesión: 20dp horizontal / 14dp vertical).
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 4.dp
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = vm::back) { Text("Atrás") }
                ExpressiveButton(
                    onClick = vm::runExtraction,
                    text = "Analizar con IA",
                    enabled = state.canAdvanceFromDocs && !state.busy,
                    trailingIcon = Icons.AutoMirrored.Filled.ArrowForward,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
