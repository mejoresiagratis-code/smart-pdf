package com.mejoresiagratis.rellenador.ui.wizard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

import com.mejoresiagratis.rellenador.ui.components.EngineChip
import com.mejoresiagratis.rellenador.ui.components.ExpressiveAccordion
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
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DocumentsStep(state: WizardUiState, vm: WizardViewModel) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) vm.addDocuments(uris) }

    // Plegado por defecto solo si YA hay documentos al entrar (nada que ocultar si está
    // vacío); una vez el usuario lo pliega/despliega a mano, se respeta su elección.
    var docsExpanded by remember { mutableStateOf(state.docUris.isEmpty()) }
    var enginesExpanded by remember { mutableStateOf(false) }

    // "Pop" del blob hero cada vez que cambia el nº de documentos — motion physics real
    // de M3 Expressive (spring del MotionScheme del tema, no un tween manual): un pequeño
    // rebote de escala que refuerza que algo cambió, sin depender solo del texto.
    // OJO: `MaterialTheme.motionScheme` es una propiedad @Composable (lee de un
    // CompositionLocal) — hay que leerla AQUÍ, en contexto Composable, y pasarla ya
    // resuelta al LaunchedEffect (función suspendida normal, no Composable). Leerla
    // dentro del LaunchedEffect da error de compilación real (visto en CI: "@Composable
    // invocations can only happen from the context of a @Composable function").
    val motionScheme = MaterialTheme.motionScheme
    var docCountSeen by remember { mutableStateOf(state.docUris.size) }
    val blobScale = remember { Animatable(1f) }
    LaunchedEffect(state.docUris.size) {
        if (state.docUris.size != docCountSeen) {
            docCountSeen = state.docUris.size
            blobScale.animateTo(1.15f, motionScheme.fastSpatialSpec())
            blobScale.animateTo(1f, motionScheme.defaultSpatialSpec())
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Blob hero — foco visual grande y a la vez botón principal para subir docs.
            // Al tocar cualquier parte del bloque (icono + contador) se abre el selector.
            // Deshabilitado durante `busy` para no cambiar los inputs a mitad de análisis.
            // Padding vertical proporcional al espacio de pantalla — antes era 24dp fijo
            // y quedaba demasiado grande dejando huecos abajo; ahora respira dentro de
            // su propia zona sin comerse el resto de la vista.
            Surface(
                shape = blobShape(),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !state.busy) {
                        picker.launch(arrayOf("image/*", "application/pdf"))
                    }
                    .scale(blobScale.value)
            ) {
                Row(
                    Modifier.padding(vertical = 20.dp, horizontal = 24.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.UploadFile, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(40.dp)
                    )
                    AnimatedContent(
                        targetState = state.docUris.size,
                        transitionSpec = {
                            (slideInVertically { h -> h } + fadeIn())
                                .togetherWith(slideOutVertically { h -> -h } + fadeOut())
                        },
                        label = "docCount",
                        modifier = Modifier.weight(1f)
                    ) { n ->
                        Column {
                            Text(
                                if (n == 0) "Toca para añadir documentos" else "$n documento(s)",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text(
                                if (n == 0) "Fotos, PDF, DNI, escritura…" else "Toca para añadir más",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            // Sección Documentos — solo si hay al menos uno.
            AnimatedVisibility(
                visible = state.docUris.isNotEmpty(),
                enter = expandVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
                exit = shrinkVertically(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut()
            ) {
                ExpressiveAccordion(
                    title = "Documentos",
                    count = state.docUris.size,
                    icon = Icons.Outlined.Description,
                    shape = MaterialTheme.shapes.medium,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    onContainerColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    expanded = docsExpanded,
                    onToggle = { docsExpanded = !docsExpanded }
                ) {
                    // Lista con scroll propio si crece — evita que empuje al Motores IA
                    // fuera de la pantalla al añadir muchos documentos.
                    Column(
                        Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        state.docUris.forEach { uri ->
                            ElevatedCard(shape = MaterialTheme.shapes.medium) {
                                ListItem(
                                    headlineContent = { Text(uri.lastPathSegment?.substringAfterLast('/') ?: "documento") },
                                    trailingContent = {
                                        IconButton(
                                            onClick = { vm.removeDocument(uri) },
                                            enabled = !state.busy
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Quitar")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Sección Motores IA — acordeón terciario.
            ExpressiveAccordion(
                title = "Motores IA",
                count = state.enabledProviders.size,
                countSuffix = " activos",
                icon = Icons.Outlined.Memory,
                shape = MaterialTheme.shapes.extraLarge,
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
                            onClick = { if (!state.busy) vm.toggleProvider(p) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                TipBanner("Los motores marcados con 🇪🇺 procesan los datos en servidores europeos.")
            }

            // Spacer que empuja los acordeones hacia arriba cuando están plegados y no
            // llenan por sí solos la pantalla — evita el hueco vacío entre "Motores IA"
            // y la barra inferior que se veía antes.
            Spacer(Modifier.weight(1f))
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

    // Pop-up modal de progreso — no descartable con tap fuera ni con botón "atrás" del
    // sistema. La extracción no debería poder interrumpirse a mitad y dejar el estado a
    // medias. MotorLoadingIndicator ya envuelve su propio ExpressiveSurface con padding,
    // así que aquí solo hace falta el Dialog vacío alrededor — sin Surface ni Column
    // extra (redundantes).
    if (state.busy) {
        Dialog(
            onDismissRequest = { /* no-op: no descartable */ },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
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
}
