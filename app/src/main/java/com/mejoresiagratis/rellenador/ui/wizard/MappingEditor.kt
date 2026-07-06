package com.mejoresiagratis.rellenador.ui.wizard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mejoresiagratis.rellenador.data.model.ContractFields

/**
 * Editor de mapeo: por cada clave canónica, muestra a qué campo real del PDF del
 * usuario está asignada, y permite corregirlo. Solo se muestra si el usuario
 * aportó su propio PDF (needsMapping = true).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MappingEditor(state: WizardUiState, vm: WizardViewModel, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            Text("Mapeo de campos del PDF", style = MaterialTheme.typography.titleMedium)
            Text("Revisa a qué campo de tu PDF corresponde cada dato. Se detectaron " +
                "${state.userFieldNames.size} campos.",
                style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ContractFields.CANON.forEach { canon ->
                val assigned = state.fieldMapping[canon.key]
                MappingRow(
                    label = canon.label,
                    assigned = assigned,
                    options = state.userFieldNames,
                    onAssign = { real -> vm.setMapping(canon.key, real) }
                )
            }
        }

        HorizontalDivider()
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::back) { Text("Atrás") }
            Button(onClick = onDone, modifier = Modifier.weight(1f)) { Text("Confirmar mapeo") }
        }
    }
}

@Composable
private fun MappingRow(
    label: String,
    assigned: String?,
    options: List<String>,
    onAssign: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ElevatedCard {
        Column(Modifier.padding(10.dp)) {
            Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { expanded = true }) {
                        Text(assigned ?: "— sin asignar —",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text("— sin asignar —") },
                            onClick = { onAssign(null); expanded = false }
                        )
                        options.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt) },
                                onClick = { onAssign(opt); expanded = false }
                            )
                        }
                    }
                }
            }
        }
    }
}
