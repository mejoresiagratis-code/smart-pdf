package com.mejoresiagratis.rellenador.ui.history

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorialScreen(onBack: () -> Unit, vm: HistorialViewModel = hiltViewModel()) {
    val entries by vm.entries.collectAsState()
    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial de contratos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        }
    ) { pad ->
        if (entries.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Aún no has generado ningún contrato.\nAparecerán aquí en cuanto pulses " +
                        "\"Generar PDF final\" en el paso de Firma.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(entries, key = { it.id }) { e ->
                    ElevatedCard {
                        ListItem(
                            headlineContent = { Text(e.label) },
                            supportingContent = { Text(formatDate(e.createdAt)) },
                            trailingContent = {
                                Row {
                                    IconButton(
                                        onClick = { shareLauncher.launch(vm.shareIntentFor(e)) },
                                        enabled = vm.fileExists(e)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = "Compartir")
                                    }
                                    IconButton(onClick = { vm.delete(e) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Borrar del historial")
                                    }
                                }
                            }
                        )
                        if (!vm.fileExists(e)) {
                            Text(
                                "El fichero ya no está en el dispositivo.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDate(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "ES")).format(Date(millis))
