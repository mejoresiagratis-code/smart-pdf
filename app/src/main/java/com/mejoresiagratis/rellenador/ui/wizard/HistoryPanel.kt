package com.mejoresiagratis.rellenador.ui.wizard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mejoresiagratis.rellenador.data.model.ContractProfile

/**
 * Panel de historial y perfiles (Tanda F): guardar el contrato actual, cargar uno
 * anterior, o borrar. Réplica ligera del modal de historial de la web.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryPanel(vm: WizardViewModel, onDismiss: () -> Unit) {
    var entries by remember { mutableStateOf<List<Pair<String, ContractProfile>>>(emptyList()) }
    var saveLabel by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.loadHistoryList { entries = it } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Historial de contratos") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = saveLabel, onValueChange = { saveLabel = it },
                        label = { Text("Nombre") }, singleLine = true, modifier = Modifier.weight(1f)
                    )
                    Button(onClick = {
                        vm.saveCurrentToHistory(saveLabel.ifBlank { "Contrato" })
                        saveLabel = ""
                        vm.loadHistoryList { entries = it }
                    }) { Text("Guardar") }
                }
                HorizontalDivider()
                if (entries.isEmpty()) {
                    Text("Aún no has guardado ningún contrato.",
                        style = MaterialTheme.typography.bodySmall)
                }
                entries.forEach { (id, profile) ->
                    ListItem(
                        headlineContent = { Text(profile.label.ifBlank { "Contrato" }) },
                        supportingContent = { Text("${profile.campos.size} campos") },
                        trailingContent = {
                            Row {
                                TextButton(onClick = { vm.applyProfile(profile) }) { Text("Cargar") }
                                TextButton(onClick = {
                                    vm.deleteHistoryEntry(id)
                                    vm.loadHistoryList { entries = it }
                                }) { Text("Borrar") }
                            }
                        }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}
