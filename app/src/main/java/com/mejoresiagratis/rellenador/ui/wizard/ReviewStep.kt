package com.mejoresiagratis.rellenador.ui.wizard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mejoresiagratis.rellenador.ui.components.ExpressiveButton
import com.mejoresiagratis.rellenador.ui.components.ExpressiveSurface

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReviewStep(state: WizardUiState, vm: WizardViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ExpressiveSurface {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Revisión IA",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Confirma los campos detectados.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        ExpressiveButton(
            onClick = { vm.next() },
            text = "Confirmar y Continuar",
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}
