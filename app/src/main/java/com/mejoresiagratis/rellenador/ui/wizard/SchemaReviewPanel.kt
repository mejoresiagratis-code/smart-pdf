package com.mejoresiagratis.rellenador.ui.wizard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * El contenido de la pantalla «Analizar y etiquetar un PDF»: la banda del etiquetado por visión,
 * los avisos de reutilizado/guardado y el [LabelEditor] con las secciones del `FormSchema`.
 *
 * Vive aparte desde la 0.10.12 porque lo usan **dos** sitios y tienen que enseñar exactamente lo
 * mismo: Ajustes › Herramientas ([LabelEditorScreen]) y el paso 1 del asistente, donde sustituye
 * al viejo [MappingEditor]. Duplicar esta UI en los dos sería garantizar que se separan.
 *
 * No conoce el asistente: recibe el [LabelEditorViewModel] y dos lambdas. Quien lo usa decide qué
 * significa «hecho» — en Ajustes es guardar y quedarse; en el asistente es guardar, adoptar el
 * esquema y avanzar al paso siguiente.
 */
@Composable
fun SchemaReviewPanel(
    vm: LabelEditorViewModel,
    state: LabelEditorViewModel.UiState,
    onDone: () -> Unit,
    doneLabel: String,
    onSecondary: () -> Unit,
    secondaryLabel: String,
    modifier: Modifier = Modifier,
) {
    val schema = state.schema ?: return

    Column(modifier.fillMaxSize()) {
        // ── Etiquetado por visión (v0.10.5) ──
        // A petición y no automático: es una llamada de red por página con huecos, y si el PDF ya
        // trae nombres legibles no aporta nada. El texto dice qué se envía, porque «mandar el
        // formulario a una IA» merece ser explícito — aunque aquí sea la plantilla en blanco y no
        // documentación del cliente.
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val progress = state.labelProgress
                Text(
                    if (state.labelling) {
                        "Leyendo los rótulos impresos" +
                            (progress?.let { " · página ${it.done} de ${it.total}" } ?: "…")
                    } else {
                        "¿Los nombres de abajo no dicen nada (\"Campo de texto 116\")? " +
                            "La IA puede leer el rótulo impreso al lado de cada hueco. " +
                            "Se envía la plantilla en blanco, no datos de cliente."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                if (state.labelling) {
                    if (progress != null && progress.total > 0) {
                        LinearProgressIndicator(
                            progress = { progress.done.toFloat() / progress.total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                } else {
                    OutlinedButton(
                        onClick = vm::labelWithVision,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Etiquetar con IA") }
                }
                state.labelNotice?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                state.error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (state.reused) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    "Este PDF ya se había analizado antes — se cargó el esquema guardado.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
        if (state.saved) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    "Guardado. La próxima vez que subas este mismo PDF se reutilizará.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }

        LabelEditor(
            schema = schema,
            onSchemaChange = vm::onSchemaChange,
            onBack = onSecondary,
            onDone = onDone,
            backLabel = secondaryLabel,
            doneLabel = doneLabel,
        )
    }
}
