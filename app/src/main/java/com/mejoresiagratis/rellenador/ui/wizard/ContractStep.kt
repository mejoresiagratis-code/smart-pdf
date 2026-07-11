package com.mejoresiagratis.rellenador.ui.screens.wizard

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mejoresiagratis.rellenador.ui.components.ExpressivePrimaryButton
import com.mejoresiagratis.rellenador.ui.components.ExpressiveSurface

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ContractStep(
    viewModel: WizardViewModel
) {
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Contenedor principal con diseño fluido
        ExpressiveSurface {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Revisión del Contrato",
                    style = MaterialTheme.typography.headlineMedium
                )
                
                // Transición suave entre los diferentes estados del contrato
                AnimatedContent(
                    targetState = state.contractStatus,
                    transitionSpec = {
                        fadeIn() + slideInVertically { height -> height / 2 } togetherWith
                        fadeOut() + slideOutVertically { height -> -height / 2 }
                    },
                    label = "Contract Status Animation"
                ) { status ->
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Botón de acción principal fijado abajo
        ExpressivePrimaryButton(
            onClick = { viewModel.nextStep() },
            text = "Aceptar y Continuar",
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}
