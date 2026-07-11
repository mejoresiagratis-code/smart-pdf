package com.mejoresiagratis.rellenador.ui.wizard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mejoresiagratis.rellenador.ui.components.ExpressivePrimaryButton
import com.mejoresiagratis.rellenador.ui.components.ExpressiveSurface

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ContractStep(state: WizardUiState, vm: WizardViewModel) {
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
                    text = "Paso 1 · Elige el contrato a rellenar",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Usa el contrato oficial de distribución incluido, o aporta tu propio PDF.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        ExpressivePrimaryButton(
            onClick = { vm.next() },
            text = "Continuar",
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}
