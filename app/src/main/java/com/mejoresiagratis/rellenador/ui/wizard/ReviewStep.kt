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
fun ReviewStep(
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
                    text = "Campos Detectados",
                    style = MaterialTheme.typography.headlineMedium
                )
                
                state.detectedFields.forEach { field ->
                    OutlinedTextField(
                        value = field.value,
                        onValueChange = { newValue -> 
                            viewModel.updateField(field.id, newValue)
                        },
                        label = { Text(field.label) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        ExpressiveButton(
            onClick = { viewModel.nextStep() },
            text = "Confirmar y Continuar",
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}
