package com.mejoresiagratis.rellenador.ui.wizard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings

/**
 * Tanda 1 — wizard shell (M3 Expressive).
 *
 * Antes: TopAppBar sin color propio (heredaba `surface`, casi idéntico al fondo →
 * cero separación visual), stepper con círculos planos donde los pasos pendientes
 * (`surfaceVariant`) quedaban casi invisibles sobre el fondo cálido, y overlay de
 * carga genérico (CircularProgressIndicator + texto blanco sobre scrim).
 *
 * Ahora: TopAppBar con `primaryContainer` propio (separa la cabecera del contenido
 * sin necesitar scroll), stepper con 3 estados reales (actual/completado/pendiente)
 * en vez de un binario activo/inactivo, y `LoadingIndicator` (forma "squiggly" nueva
 * de Expressive, ya disponible en 1.4.0-alpha16) dentro de una tarjeta elevada en
 * vez de flotar directamente sobre el scrim.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WizardScreen(vm: WizardViewModel = hiltViewModel(), onOpenSettings: () -> Unit = {}) {
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rellenador de Contratos") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                StepIndicator(current = state.step)
            }
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
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp,
                            shadowElevation = 6.dp
                        ) {
                            Column(
                                Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                LoadingIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    state.busyMsg,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center
                                )
                            }
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

/**
 * Tres estados en vez del binario anterior (activo/inactivo):
 * - Pendiente: círculo con borde (`outline`), sin relleno — visible sobre el fondo
 *   cálido a diferencia del `surfaceVariant` plano de antes.
 * - Completado: círculo relleno `primaryContainer` + check — se distingue del actual
 *   sin competir en intensidad con él.
 * - Actual: círculo `primary` más grande y con elevación propia — es el único que
 *   "flota" sobre la fila, dirigiendo la atención (principio Expressive).
 */
@Composable
private fun StepIndicator(current: Step) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Step.entries.forEach { s ->
            val isCurrent = s == current
            val isDone = s.index < current.index

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                when {
                    isCurrent -> Surface(
                        modifier = Modifier.size(34.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 3.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "${s.index + 1}",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    isDone -> Surface(
                        modifier = Modifier.size(28.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Completado",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    else -> Surface(
                        modifier = Modifier.size(28.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "${s.index + 1}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    s.title,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
