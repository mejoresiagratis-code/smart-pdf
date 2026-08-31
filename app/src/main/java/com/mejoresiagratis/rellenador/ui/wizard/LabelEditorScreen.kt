package com.mejoresiagratis.rellenador.ui.wizard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Pantalla contenedora de [LabelEditor]: elige el PDF (SAF), lanza [LabelEditorViewModel] y
 * muestra el resultado. Independiente del asistente — no comparte estado con `WizardViewModel`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelEditorScreen(
    vm: LabelEditorViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsState()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(vm::pickPdf) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.fileName ?: "Analizar y etiquetar un PDF",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val schema = state.schema
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                schema == null -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        "Elige un PDF rellenable (contrato, formulario…). Se leen sus campos " +
                            "y se agrupan en tablas/secciones automáticamente; luego puedes " +
                            "corregir a mano las etiquetas que no sean claras.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { picker.launch(arrayOf("application/pdf")) }) {
                        Text("Elegir PDF")
                    }
                }

                else -> Column(Modifier.fillMaxSize()) {
                    // ── Etiquetado por visión (v0.10.5) ──
                    // A petición y no automático: es una llamada de red por página con huecos, y
                    // si el PDF ya trae nombres legibles no aporta nada. El texto dice qué se
                    // envía, porque «mandar el formulario a una IA» merece ser explícito — aunque
                    // aquí sea la plantilla en blanco y no documentación del cliente.
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
                        // El botón de abajo vuelve al selector; el de la barra sale de la
                        // pantalla. Son dos acciones distintas, así que no pueden llamarse igual.
                        onBack = vm::reset,
                        onDone = vm::save,
                        backLabel = "Elegir otro PDF",
                    )
                }
            }
        }
    }
}
