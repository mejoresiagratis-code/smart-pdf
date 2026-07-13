package com.mejoresiagratis.rellenador.ui.wizard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mejoresiagratis.rellenador.data.model.AiProvider
import com.mejoresiagratis.rellenador.data.model.ContractFields
import com.mejoresiagratis.rellenador.data.model.FieldProposal
import com.mejoresiagratis.rellenador.data.model.PackageApplier
import com.mejoresiagratis.rellenador.data.model.Paquete
import com.mejoresiagratis.rellenador.ui.components.ProviderLogo

/**
 * Paso 3 — Revisión IA. Arriba, los "paquetes" detectados se aplican en bloque
 * de un toque (dirección fiscal/comercio, empresa, persona, banco). Debajo, la
 * confirmación campo a campo con candidatos y consenso de motores.
 * Tanda 3: formas unificadas con el resto de la app (shapes.medium), logos reales
 * de motor en vez de texto plano, y candidatos como chips en vez de radio buttons.
 */
@Composable
fun ReviewStep(state: WizardUiState, vm: WizardViewModel) {
    var showEngineDetail by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Paso 3 · Revisa lo detectado por la IA", style = MaterialTheme.typography.titleMedium)
            val engines = state.enginesOk.joinToString(", ").ifEmpty { "—" }
            Text("Motores: $engines" + (state.tipoIdentificacion?.let { " · Tipo: $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall)
            Text("Aplica un bloque completo o confirma campo a campo.",
                style = MaterialTheme.typography.bodySmall)

            // Panel de detalle de motor caído: muestra el estado real de cada motor que
            // falló (código HTTP + mensaje real reenviado por el proxy), en vez de solo
            // el banner genérico. Ayuda a diagnosticar 400/500/429 sin adb logcat.
            if (state.engineErrors.isNotEmpty()) {
                TextButton(onClick = { showEngineDetail = !showEngineDetail }) {
                    Text(if (showEngineDetail) "Ocultar motores no disponibles ▲"
                         else "Ver motores no disponibles (${state.engineErrors.size}) ▼")
                }
                if (showEngineDetail) {
                    ElevatedCard(shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            state.engineErrors.forEach { line ->
                                Text(line, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider()

        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 10.dp)) {

            if (state.packages.isNotEmpty()) {
                item {
                    Text("Bloques detectados", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold)
                }
                itemsIndexed(state.packages, key = { i, _ -> "pkg_$i" }) { _, pk ->
                    PackageCard(pk, onApply = { block2 -> vm.applyPackage(pk, block2) })
                }
                item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
            }

            if (state.proposals.isEmpty()) {
                item {
                    Text("La IA no propuso campos sueltos. Puedes rellenar manualmente en el siguiente paso.",
                        style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                item {
                    Text("Campos", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
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
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::back) { Text("Atrás") }
            Button(onClick = vm::next, modifier = Modifier.weight(1f)) { Text("Ir al relleno") }
        }
    }
}

@Composable
private fun PackageCard(pk: Paquete, onApply: (block2: Boolean) -> Unit) {
    ElevatedCard(shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = {}, label = { Text(tipoLabel(pk.tipo)) })
                Spacer(Modifier.width(8.dp))
                Text(pk.etiqueta.ifBlank { tipoLabel(pk.tipo) },
                    fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            }
            // Resumen de lo que rellena
            val resumen = pk.datos.entries.joinToString(" · ") { "${it.key}: ${it.value}" }
            Text(resumen, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (pk.fuente.isNotBlank()) {
                Text(pk.fuente, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }

            if (PackageApplier.canTargetBlock2(pk)) {
                // Dirección: el usuario elige bloque fiscal o comercio (_2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onApply(false) }, modifier = Modifier.weight(1f)) {
                        Text("A dirección fiscal")
                    }
                    OutlinedButton(onClick = { onApply(true) }, modifier = Modifier.weight(1f)) {
                        Text("A comercio (_2)")
                    }
                }
            } else {
                Button(onClick = { onApply(false) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Aplicar bloque")
                }
            }
        }
    }
}

private fun tipoLabel(tipo: String): String = when (tipo) {
    "direccion" -> "Dirección fiscal"
    "direccion_comercio" -> "Dirección comercio"
    "empresa" -> "Empresa"
    "persona" -> "Representante"
    "banco" -> "Banco"
    else -> tipo
}

@Composable
private fun ProposalCard(
    proposal: FieldProposal,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    ElevatedCard(shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(ContractFields.labelFor(proposal.fieldKey),
                fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)

            // Candidatos como chips seleccionables (antes lista de radio buttons):
            // más táctil y coherente con el lenguaje Expressive del resto del wizard.
            // Logo real del motor que lo propuso en vez de solo el nombre en texto.
            proposal.candidates.forEach { c ->
                val chosen = selected == c.value
                FilterChip(
                    selected = chosen,
                    onClick = { onSelect(if (chosen) null else c.value) },
                    label = { Text(c.value) },
                    leadingIcon = if (c.sources.isNotEmpty()) {
                        {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                c.sources.take(3).forEach { src ->
                                    AiProvider.entries.firstOrNull { it.displayName == src }?.let { p ->
                                        ProviderLogo(provider = p, size = 16.dp)
                                    }
                                }
                            }
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("Dejar en blanco") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
