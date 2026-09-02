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
    // Tanda 5·4 §6.5 — el destino tiene que ser de un `FieldKind` compatible con el origen.
    // Con el contrato de Aire cargado, el mapeo por similitud asignaba `Fecha · mes` a
    // `Casilla de verificación 56` (la del ALTA NUEVA), un checkbox como destino de un campo
    // de texto. Aquí se restringe la lista de opciones que se ofrece por cada canónica al
    // subconjunto de campos del PDF que son del tipo esperado. Un texto sólo puede mapear a
    // otro texto; las casillas de tipo de identificación, sólo a `CHECKBOX`.
    //
    // Sin `activeSchema` no se puede saber el tipo — es el caso de sesiones restauradas antes
    // de la 5·4, o de PDFs sin AcroForm — y entonces se muestran todas las opciones como antes.
    val fieldKindByName: Map<String, com.mejoresiagratis.rellenador.data.model.FieldKind> =
        state.activeSchema?.allFields()?.associate { it.name to it.kind } ?: emptyMap()

    fun expectedKind(canonKey: String): com.mejoresiagratis.rellenador.data.model.FieldKind =
        when (canonKey) {
            com.mejoresiagratis.rellenador.data.model.ContractFields.CHECKBOX_CIF,
            com.mejoresiagratis.rellenador.data.model.ContractFields.CHECKBOX_NIF,
            com.mejoresiagratis.rellenador.data.model.ContractFields.CHECKBOX_NIE ->
                com.mejoresiagratis.rellenador.data.model.FieldKind.CHECKBOX
            else -> com.mejoresiagratis.rellenador.data.model.FieldKind.TEXT
        }

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
                val expected = expectedKind(canon.key)
                val options = if (fieldKindByName.isEmpty()) state.userFieldNames
                    else state.userFieldNames.filter { name ->
                        // Si no consta el tipo (por ejemplo, un nombre que el usuario tecleó a
                        // mano y no está en el AcroForm), no se filtra: no compensa perder una
                        // asignación válida por una comprobación defensiva.
                        val kind = fieldKindByName[name] ?: return@filter true
                        kind == expected
                    }
                MappingRow(
                    label = canon.label,
                    assigned = assigned,
                    options = options,
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
