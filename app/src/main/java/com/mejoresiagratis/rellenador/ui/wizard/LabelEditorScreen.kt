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

                else -> SchemaReviewPanel(
                    vm = vm,
                    state = state,
                    onDone = vm::save,
                    doneLabel = "Confirmar etiquetas",
                    // El botón de abajo vuelve al selector; el de la barra sale de la pantalla.
                    // Son dos acciones distintas, así que no pueden llamarse igual.
                    onSecondary = vm::reset,
                    secondaryLabel = "Elegir otro PDF",
                )
            }
        }
    }
}
