package com.mejoresiagratis.rellenador.ui.wizard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContractStep(state: WizardUiState, vm: WizardViewModel) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(vm::chooseUserContract) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Paso 1 · Elige el contrato a rellenar", style = MaterialTheme.typography.titleMedium)
        Text("Usa el contrato oficial de distribución MASORANGE incluido, o aporta tu propio PDF.",
            style = MaterialTheme.typography.bodyMedium)

        ElevatedCard(onClick = vm::chooseDefaultContract) {
            ListItem(
                headlineContent = { Text("Contrato por defecto") },
                supportingContent = { Text("Contrato de distribución PdV (54 páginas)") },
                leadingContent = { RadioButton(selected = state.contractSource == ContractSource.DEFAULT, onClick = vm::chooseDefaultContract) }
            )
        }
        ElevatedCard(onClick = { picker.launch(arrayOf("application/pdf")) }) {
            ListItem(
                headlineContent = { Text("Aportar mi PDF") },
                supportingContent = {
                    Text(state.userContractUri?.lastPathSegment?.substringAfterLast('/')
                        ?: "Seleccionar un PDF del dispositivo")
                },
                leadingContent = { RadioButton(selected = state.contractSource == ContractSource.USER, onClick = { picker.launch(arrayOf("application/pdf")) }) }
            )
        }

        Spacer(Modifier.weight(1f))
        Button(
            onClick = vm::next,
            enabled = state.canAdvanceFromContrato,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Continuar") }
    }
}
