package com.mejoresiagratis.rellenador.ui.wizard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Paso 5 — Firma. Se implementará en la siguiente tanda (captura en Canvas +
 * inserción en página 24 + generación del PDF final con AcroFormFiller).
 * El andamiaje de navegación ya está cableado.
 */
@Composable
fun SignaturePlaceholderStep(state: WizardUiState, vm: WizardViewModel) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Paso 5 · Firma y PDF final", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            "Los ${state.fieldValues.count { it.value.isNotBlank() }} campos están listos. " +
            "La captura de firma manuscrita y la generación del PDF final se añaden en la próxima fase.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = vm::back) { Text("Volver al relleno") }
    }
}
