package com.mejoresiagratis.rellenador.ui.wizard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.mejoresiagratis.rellenador.data.model.ContractFields
import com.mejoresiagratis.rellenador.data.validation.FieldNormalizer
import com.mejoresiagratis.rellenador.data.validation.FieldValidator

/**
 * Paso 4 — Relleno editable con validación en vivo (dígitos de control).
 * Cada campo muestra su error concreto si el valor no es válido.
 */
@Composable
fun FillStep(state: WizardUiState, vm: WizardViewModel) {
    var showHistory by remember { mutableStateOf(false) }
    if (showHistory) HistoryPanel(vm, onDismiss = { showHistory = false })

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Paso 4 · Rellena o corrige los campos", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { showHistory = true }) { Text("Historial") }
            }
            Text("Se valida NIF/CIF/IBAN/CP en vivo. El responsable comercial es automático.",
                style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()

        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 10.dp)) {
            items(ContractFields.CANON, key = { it.key }) { field ->
                val value = state.fieldValues[field.key] ?: ""
                // Provincia hermana para validar el CP del mismo bloque (_2 o fiscal).
                val provKey = if (field.key.endsWith("_2")) "Provincia_2" else "Provincia"
                val result = FieldValidator.validate(
                    fieldName = field.key,
                    value = value,
                    tipoId = state.tipoIdentificacion,
                    provinciaSibling = state.fieldValues[provKey]
                )
                val isError = result?.ok == false

                Column {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { vm.setFieldValue(field.key, it) },
                        label = { Text(field.label) },
                        singleLine = true,
                        isError = isError,
                        keyboardOptions = keyboardFor(field.key),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isError) {
                        Text(result?.message ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 12.dp, top = 2.dp))
                    }
                }
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

/** Teclado adecuado por tipo de campo. */
private fun keyboardFor(key: String): KeyboardOptions {
    val b = FieldNormalizer.norm(key.substringBefore("_"))
    val type = when {
        b == "telefono" -> KeyboardType.Phone
        b.startsWith("email") -> KeyboardType.Email
        b == "cp" || b == "fecha" -> KeyboardType.Number
        else -> KeyboardType.Text
    }
    return KeyboardOptions(keyboardType = type, imeAction = ImeAction.Next)
}
