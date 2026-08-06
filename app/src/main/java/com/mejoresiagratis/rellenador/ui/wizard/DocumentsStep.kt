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
import androidx.compose.ui.draw.alpha
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
                // v0.8.6: faltaba el scroll. Con varios documentos y los dos acordeones
                // abiertos el contenido desborda y se cortaba sin poder desplazarlo.
                .verticalScroll(rememberScrollState())
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
                    // La lista fluye dentro del scroll de la pantalla. Antes tenía scroll
                    // propio acotado a 240 dp; ahora que el contenedor externo también se
                    // desplaza, dos scrolls anidados en el mismo eje se pelean por el
                    // gesto y la lista quedaba difícil de desplazar.
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.docUris.forEach { uri ->
                            ElevatedCard(shape = MaterialTheme.shapes.medium) {
                                ListItem(
                                    // Las copias locales llevan prefijo "<millis>_" para
                                    // evitar colisiones de nombre; no se enseña al usuario.
                                    headlineContent = {
                                        Text(
                                            uri.lastPathSegment
                                                ?.substringAfterLast('/')
                                                ?.replace(Regex("^\\d{10,}_"), "")
                                                ?: "documento"
                                        )
                                    },
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

            // v0.9.1 — aviso previo obligatorio antes de mandar documentos a la IA.
            if (state.showConsent) {
                ConsentSheet(
                    engines = state.enabledProviders.toList().sortedBy { it.displayName },
                    docCount = state.docUris.size,
                    onAccept = vm::acceptConsent,
                    onDismiss = vm::dismissConsent,
                )
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

                // v0.9.1 — filtro de región. Va ARRIBA del listado a propósito: decide qué
                // motores son elegibles, así que leerlo después de haber elegido sería
                // llegar tarde.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                ) {
                    Switch(
                        checked = state.euOnly,
                        onCheckedChange = { if (!state.busy) vm.setEuOnly(it) },
                        enabled = !state.busy
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Solo motores europeos", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (state.euOnly) "Los datos no salen de la UE"
                            else "Algunos motores procesan fuera de la UE",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.availableProviders.forEach { p ->
                        // Con el filtro solo-UE, los de fuera se ven atenuados y no
                        // responden: se mantienen a la vista para que quede claro que
                        // existen y por qué no se pueden usar ahora.
                        val blocked = state.euOnly && !p.eu
                        EngineChip(
                            provider = p,
                            selected = p in state.enabledProviders,
                            active = state.busy && state.activeProvider == p,
                            onClick = { if (!state.busy && !blocked) vm.toggleProvider(p) },
                            modifier = Modifier.alpha(if (blocked) 0.38f else 1f)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                TipBanner("Los motores marcados con 🇪🇺 procesan los datos en servidores europeos.")
            }

            // (Se retira el antiguo `Spacer(Modifier.weight(1f))`: dentro de un contenedor
            // con scroll la altura es infinita, así que `weight` no reparte nada — y con
            // el contenido ya alineado arriba no aportaba nada.)
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
                    onClick = vm::requestExtraction,
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
