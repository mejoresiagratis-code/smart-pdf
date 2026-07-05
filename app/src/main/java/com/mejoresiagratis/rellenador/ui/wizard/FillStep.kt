package com.mejoresiagratis.rellenador.ui.wizard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mejoresiagratis.rellenador.data.model.ContractFields

/**
 * Paso 4 — Relleno editable. Todos los campos canónicos del contrato, prerrellenados
 * con lo confirmado en Revisión, editables antes de generar el PDF.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FillStep(state: WizardUiState, vm: WizardViewModel) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            Text("Paso 4 · Rellena o corrige los campos", style = MaterialTheme.typography.titleMedium)
            Text("Revisa cada dato antes de firmar. El responsable comercial se rellena automáticamente.",
                style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()

        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 10.dp)) {
            items(ContractFields.CANON, key = { it.key }) { field ->
                OutlinedTextField(
                    value = state.fieldValues[field.key] ?: "",
                    onValueChange = { vm.setFieldValue(field.key, it) },
                    label = { Text(field.label) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                AssistChip(
                    onClick = { },
                    label = { Text("${ContractFields.RESPONSABLE_VALUE} (automático)") },
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        HorizontalDivider()
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::back) { Text("Atrás") }
            Button(onClick = vm::next, modifier = Modifier.weight(1f)) { Text("Ir a la firma") }
        }
    }
}
