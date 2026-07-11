package com.mejoresiagratis.rellenador.ui.wizard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Importamos nuestros componentes globales
import com.mejoresiagratis.rellenador.ui.components.ExpressiveButton
import com.mejoresiagratis.rellenador.ui.components.ExpressiveSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContractStep(state: WizardUiState, vm: WizardViewModel) {
    var showMapping by remember { mutableStateOf(false) }
    
    // Evaluamos el estado de forma limpia
    val isMappingState = showMapping && state.needsMapping && state.userFieldNames.isNotEmpty()

    // 1. Transición Fluida: Evitamos el "return" abrupto que rompe el ciclo de vida de Compose
    AnimatedContent(
        targetState = isMappingState,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "ContractStepTransition"
    ) { isMapping ->
        if (isMapping) {
            MappingEditor(
                state = state,
                vm = vm,
                onDone = {
                    vm.rememberTemplateMapping()
                    showMapping = false
                    vm.next()
                }
            )
        } else {
            ContractSelectionContent(
                state = state,
                vm = vm,
                onReviewMapping = { showMapping = true }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContractSelectionContent(
    state: WizardUiState,
    vm: WizardViewModel,
    onReviewMapping: () -> Unit
) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(vm::chooseUserContract) }

    // 2. Layout Estructural: Separamos el área de scroll del botón de acción anclado
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp), // Espaciado exterior para que el Surface respire
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Aplicamos el contenedor orgánico M3 Expressive
            ExpressiveSurface {
                Column(
                    modifier = Modifier.padding(24.dp), // Márgenes internos
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Paso 1 · Elige el contrato a rellenar",
                            style = MaterialTheme.typography.headlineSmall, // Mayor jerarquía
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Usa el contrato oficial de distribución MASORANGE incluido, o aporta tu propio PDF.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 3. Semántica de Grupo: Le dice a los servicios de accesibilidad que esto es un grupo de opciones
                    Column(
                        modifier = Modifier.selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val isDefault = state.contractSource == ContractSource.DEFAULT
                        ContractOptionCard(
                            selected = isDefault,
                            onClick = vm::chooseDefaultContract,
                            headline = "Contrato por defecto",
                            supporting = "Contrato de distribución PdV (54 páginas)",
                            icon = { Icon(Icons.Outlined.Description, contentDescription = null) }
                        )

                        val isUser = state.contractSource == ContractSource.USER
                        val fileName = state.userContractUri?.lastPathSegment?.substringAfterLast('/')
                            ?: "Seleccionar un PDF del dispositivo"

                        ContractOptionCard(
                            selected = isUser,
                            onClick = { picker.launch(arrayOf("application/pdf")) },
                            headline = "Aportar mi PDF",
                            supporting = fileName,
                            icon = { Icon(Icons.Outlined.UploadFile, contentDescription = null) }
                        )
                    }

                    com.mejoresiagratis.rellenador.ui.components.TipBanner(
                        "Usa \"Aportar mi PDF\" solo si tienes una versión del contrato distinta a la incluida por defecto."
                    )
                }
            }
        }

        // 4. Acción Primaria Anclada: Fundamental en flujos tipo "Wizard"
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp, // Sutil elevación para separarlo del contenido scrolleable
            shadowElevation = 4.dp
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                // Usamos nuestro botón global estandarizado
                ExpressiveButton(
                    onClick = {
                        if (state.contractSource == ContractSource.USER && state.needsMapping) {
                            onReviewMapping()
                        } else {
                            vm.next()
                        }
                    },
                    enabled = state.canAdvanceFromContrato,
                    text = if (state.contractSource == ContractSource.USER && state.needsMapping) "Revisar mapeo" else "Continuar",
                    trailingIcon = Icons.AutoMirrored.Filled.ArrowForward
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContractOptionCard(
    selected: Boolean,
    onClick: () -> Unit,
    headline: String,
    supporting: String,
    icon: @Composable () -> Unit
) {
    // Alineado al mockup: la tarjeta seleccionada pasa a `primaryContainer` (antes
    // usaba `secondaryContainer`) — es el color de marca, no el neutro secundario.
    val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val borderColor = if (selected) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.outlineVariant
    val onContainerColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.5.dp, borderColor),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Icono en contenedor "blob" orgánico (fiel al mockup): relleno con
            // `primary` cuando está seleccionado, `secondaryContainer` si no.
            Surface(
                shape = com.mejoresiagratis.rellenador.ui.components.blobShape(),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CompositionLocalProvider(
                        LocalContentColor provides if (selected) MaterialTheme.colorScheme.onPrimary
                                                    else MaterialTheme.colorScheme.onSecondaryContainer
                    ) { icon() }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(headline, style = MaterialTheme.typography.titleMedium, color = onContainerColor)
                Text(
                    supporting, style = MaterialTheme.typography.bodySmall,
                    color = if (selected) onContainerColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Marca de verificación circular — rellena si está seleccionada, solo
            // contorno si no (mismo patrón que el ".check" del mockup).
            if (selected) {
                Surface(shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Check, contentDescription = "Seleccionado",
                            tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(13.dp))
                    }
                }
            } else {
                Box(
                    Modifier.size(22.dp)
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, androidx.compose.foundation.shape.CircleShape)
                )
            }
        }
    }
}
