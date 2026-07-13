package com.mejoresiagratis.rellenador.ui.wizard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp

import com.mejoresiagratis.rellenador.ui.components.EngineChip
import com.mejoresiagratis.rellenador.ui.components.ExpressiveButton
import com.mejoresiagratis.rellenador.ui.components.MotorLoadingIndicator
import com.mejoresiagratis.rellenador.ui.components.TipBanner
import com.mejoresiagratis.rellenador.ui.components.blobShape

/**
 * Tanda 2 + Mezcla 2/3 — Documentación. Blob hero grande como foco visual (Propuesta 3),
 * secciones "Documentos" y "Motores IA" como acordeones en bloques tonales, plegados por
 * defecto una vez hay documentos cargados (Propuesta 2). El indicador de carga muestra
 * en vivo qué documento real y qué motor están en curso, con barra de progreso agregada
 * (documento × motor) — wired a WizardUiState.activeDocLabel/progressCurrent/progressTotal
 * y a MultiAiExtractor.extract(docNames=..., onProgress=...).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DocumentsStep(state: WizardUiState, vm: WizardViewModel) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) vm.addDocuments(uris) }

    // Plegado por defecto solo si YA hay documentos al entrar (nada que ocultar si está
    // vacío); una vez el usuario lo pliega/despliega a mano, se respeta su elección.
    var docsExpanded by remember { mutableStateOf(state.docUris.isEmpty()) }
    var enginesExpanded by remember { mutableStateOf(false) }

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

            // Blob hero — foco visual grande (Propuesta 3), sustituye a la tarjeta de
            // estado más pequeña de la Tanda 2.
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = blobShape(),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.UploadFile, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                Text(
                    if (state.docUris.isEmpty()) "Sin documentos todavía"
                    else "${state.docUris.size} documento(s) añadido(s)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Sección Documentos — acordeón tonal (Propuesta 2).
            AccordionSection(
                title = "Documentos",
                count = state.docUris.size,
                icon = Icons.Outlined.Description,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                onContainerColor = MaterialTheme.colorScheme.onSecondaryContainer,
                expanded = docsExpanded,
                onToggle = { docsExpanded = !docsExpanded }
            ) {
                if (state.docUris.isNotEmpty()) {
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
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("image/*", "application/pdf")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Añadir documentos") }
            }

            // Sección Motores IA — acordeón tonal (Propuesta 2), color terciario para
            // distinguirla visualmente de Documentos (mismo criterio que FillStep/Fecha).
            AccordionSection(
                title = "Motores IA",
                count = state.enabledProviders.size,
                countSuffix = " activos",
                icon = Icons.Outlined.Memory,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                onContainerColor = MaterialTheme.colorScheme.onTertiaryContainer,
                expanded = enginesExpanded,
                onToggle = { enginesExpanded = !enginesExpanded }
            ) {
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
                            // Bloqueado mientras se analiza: cambiar motores a mitad de una
                            // extracción en curso podía confundir sobre a qué tanda aplica.
                            onClick = { if (!state.busy) vm.toggleProvider(p) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                TipBanner("Los motores marcados con 🇪🇺 procesan los datos en servidores europeos.")
            }

            // Indicador contextual con progreso real documento × motor en vivo.
            AnimatedVisibility(
                visible = state.busy,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                MotorLoadingIndicator(
                    busyMsg = state.busyMsg,
                    activeProvider = state.activeProvider,
                    finishedProviders = state.finishedProviders,
                    enabledProviders = state.enabledProviders.toList(),
                    activeDocLabel = state.activeDocLabel,
                    progressCurrent = state.progressCurrent,
                    progressTotal = state.progressTotal
                )
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 4.dp
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = vm::back, enabled = !state.busy) { Text("Atrás") }
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

/**
 * Bloque acordeón tonal reutilizado por Documentos y Motores IA: cabecera con icono,
 * título, contador y chevron que gira; cuerpo con expandVertically/shrinkVertically.
 */
@Composable
private fun AccordionSection(
    title: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: androidx.compose.ui.graphics.Color,
    onContainerColor: androidx.compose.ui.graphics.Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    countSuffix: String = "",
    content: @Composable ColumnScope.() -> Unit
) {
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Column(Modifier.padding(4.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = onContainerColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "$title · $count$countSuffix",
                    style = MaterialTheme.typography.titleSmall,
                    color = onContainerColor,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Filled.KeyboardArrowDown, contentDescription = if (expanded) "Contraer" else "Expandir",
                    tint = onContainerColor,
                    modifier = Modifier.rotate(chevronRotation)
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).padding(bottom = 12.dp)) {
                    content()
                }
            }
        }
    }
}
