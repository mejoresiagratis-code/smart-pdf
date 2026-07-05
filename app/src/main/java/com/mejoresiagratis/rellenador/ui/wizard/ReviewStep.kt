package com.mejoresiagratis.rellenador.ui.wizard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mejoresiagratis.rellenador.data.model.ContractFields
import com.mejoresiagratis.rellenador.data.model.FieldProposal

/**
 * Paso 3a — Revisión IA. Por cada campo con propuestas, el usuario confirma
 * un valor (o lo deja en blanco). Réplica del toast de confirmación de la web:
 * muestra los candidatos con los motores que los proponen (consenso).
 */
@Composable
fun ReviewStep(state: WizardUiState, vm: WizardViewModel) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Paso 3 · Revisa lo detectado por la IA", style = MaterialTheme.typography.titleMedium)
            val engines = state.enginesOk.joinToString(", ").ifEmpty { "—" }
            Text("Motores: $engines" + (state.tipoIdentificacion?.let { " · Tipo: $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall)
            Text("Confirma el valor de cada campo o déjalo en blanco.",
                style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()

        if (state.proposals.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("La IA no propuso ningún campo. Puedes rellenar manualmente en el siguiente paso.")
            }
        } else {
            LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.proposals, key = { it.fieldKey }) { fp ->
                    ProposalCard(
                        proposal = fp,
                        selected = state.fieldValues[fp.fieldKey],
                        onSelect = { v -> if (v == null) vm.clearField(fp.fieldKey) else vm.setFieldValue(fp.fieldKey, v) }
                    )
                }
            }
        }

        HorizontalDivider()
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::back) { Text("Atrás") }
            Button(onClick = vm::next, modifier = Modifier.weight(1f)) { Text("Ir al relleno") }
        }
    }
}

@Composable
private fun ProposalCard(
    proposal: FieldProposal,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    ElevatedCard(shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(ContractFields.labelFor(proposal.fieldKey),
                fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)

            proposal.candidates.forEach { c ->
                Row(
                    Modifier.fillMaxWidth().selectable(
                        selected = selected == c.value, onClick = { onSelect(c.value) }
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected == c.value, onClick = { onSelect(c.value) })
                    Column(Modifier.weight(1f)) {
                        Text(c.value, style = MaterialTheme.typography.bodyMedium)
                        if (c.sources.isNotEmpty()) {
                            Text(c.sources.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            // Opción "dejar en blanco"
            Row(
                Modifier.fillMaxWidth().selectable(selected = selected == null, onClick = { onSelect(null) }),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selected == null, onClick = { onSelect(null) })
                Text("Dejar en blanco", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
