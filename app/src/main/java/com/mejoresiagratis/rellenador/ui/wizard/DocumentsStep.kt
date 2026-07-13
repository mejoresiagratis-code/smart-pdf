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
                    modifier = Modifier.size(72.dp).scale(blobScale.value)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.UploadFile, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                AnimatedContent(
                    targetState = state.docUris.size,
                    transitionSpec = {
                        (slideInVertically { h -> h } + fadeIn())
                            .togetherWith(slideOutVertically { h -> -h } + fadeOut())
                    },
                    label = "docCount"
                ) { n ->
                    Text(
                        if (n == 0) "Sin documentos todavía" else "$n documento(s) añadido(s)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Sección Documentos — acordeón tonal (Propuesta 2). Esquinas "medium": forma
            // más contenida, coherente con las tarjetas de documento de dentro.
            AccordionSection(
                title = "Documentos",
                count = state.docUris.size,
                icon = Icons.Outlined.Description,
                shape = MaterialTheme.shapes.medium,
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
            // distinguirla de Documentos. Esquinas "extraLarge": forma más redondeada y
            // orgánica que la de Documentos, generando la "tensión visual" entre bloques
            // que recomienda la guía Expressive (mezclar radios de esquina, no solo color).
            AccordionSection(
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AccordionSection(
    title: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    shape: androidx.compose.ui.graphics.Shape,
    containerColor: androidx.compose.ui.graphics.Color,
    onContainerColor: androidx.compose.ui.graphics.Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    countSuffix: String = "",
    content: @Composable ColumnScope.() -> Unit
) {
    // Motion physics real (M3 Expressive): el muelle del MotionScheme del tema, no un
    // tween/easing manual — mismo principio que el "pop" del blob hero.
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "chevron"
    )
    Surface(
        shape = shape,
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
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text("$title · ", style = MaterialTheme.typography.titleSmall, color = onContainerColor)
                    AnimatedContent(
                        targetState = count,
                        transitionSpec = {
                            (slideInVertically { h -> h } + fadeIn())
                                .togetherWith(slideOutVertically { h -> -h } + fadeOut())
                        },
                        label = "sectionCount"
                    ) { c ->
                        Text("$c$countSuffix", style = MaterialTheme.typography.titleSmall, color = onContainerColor)
                    }
                }
                Icon(
                    Icons.Filled.KeyboardArrowDown, contentDescription = if (expanded) "Contraer" else "Expandir",
                    tint = onContainerColor,
                    modifier = Modifier.rotate(chevronRotation)
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit = shrinkVertically(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec())
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).padding(bottom = 12.dp)) {
                    content()
                }
            }
        }
    }
}
