package com.mejoresiagratis.rellenador.ui.screens.wizard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mejoresiagratis.rellenador.ui.components.ExpressiveButton
import com.mejoresiagratis.rellenador.ui.components.ExpressiveSurface

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SignatureStep(
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
        ExpressiveSurface {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Firma del Documento",
                    style = MaterialTheme.typography.headlineMedium
                )
                
                Text(
                    text = "Por favor, firma en el espacio indicado para finalizar el proceso.",
                    style = MaterialTheme.typography.bodyLarge
                )
                
                // Aquí iría el componente de firma real,
                // pero lo mantenemos simple para esta integración
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text(
                        text = "Área de firma",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        ExpressiveButton(
            onClick = { viewModel.generatePdf() },
            text = "Generar PDF final",
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}
