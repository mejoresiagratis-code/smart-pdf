package com.mejoresiagratis.rellenador.ui.wizard

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureStep(state: WizardUiState, vm: WizardViewModel) {
    val context = LocalContext.current
    var mode by remember { mutableIntStateOf(0) }  // 0 = dibujar, 1 = extraer de foto

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { ins ->
                BitmapFactory.decodeStream(ins)?.let(vm::extractSignatureFromPhoto)
            }
        }
    }

    // Lanzar el visor de compartir cuando el PDF esté listo
    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { dest ->
        val file = state.outputFile ?: return@rememberLauncherForActivityResult
        dest?.let {
            context.contentResolver.openOutputStream(it)?.use { os -> file.inputStream().use { it.copyTo(os) } }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Paso 5 · Firma", style = MaterialTheme.typography.titleMedium)

        TabRow(selectedTabIndex = mode) {
            Tab(selected = mode == 0, onClick = { mode = 0 }, text = { Text("Dibujar") })
            Tab(selected = mode == 1, onClick = { mode = 1 }, text = { Text("Extraer de foto") })
        }

        when (mode) {
            0 -> ElevatedCard {
                Column(Modifier.padding(12.dp)) {
                    Text("Dibuja la firma del distribuidor:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    SignatureCanvas(
                        onBitmap = vm::setDrawnSignature,
                        controls = { clear, done ->
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = clear) { Text("Limpiar") }
                                Button(onClick = done) { Text("Usar esta firma") }
                            }
                        }
                    )
                }
            }
            1 -> ElevatedCard {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sube una foto de un documento firmado; la IA localizará la firma.",
                        style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { photoPicker.launch(arrayOf("image/*")) }) {
                        Text("Elegir foto")
                    }
                    if (state.locatingSignature) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp)); Text("Localizando firma…")
                        }
                    }
                }
            }
        }

        if (state.signature != null) {
            AssistChip(onClick = {}, label = { Text("Firma preparada ✓") })

            // Ajuste manual de posición/tamaño en la página 24
            val stamp = state.stamps.firstOrNull()
            if (stamp != null) {
                Text("Ajuste en la página 24 (posición y tamaño):",
                    style = MaterialTheme.typography.labelLarge)
                LabeledSlider("Horizontal", stamp.xRel) { vm.updateStamp(it, stamp.yRel, stamp.widthRel) }
                LabeledSlider("Vertical", stamp.yRel) { vm.updateStamp(stamp.xRel, it, stamp.widthRel) }
                LabeledSlider("Tamaño", stamp.widthRel, 0.1f, 0.6f) { vm.updateStamp(stamp.xRel, stamp.yRel, it) }
            }
            OutlinedButton(onClick = vm::clearSignature) { Text("Quitar firma") }
        }

        HorizontalDivider()

        Button(
            onClick = { vm.generatePdf() },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Generar PDF final") }

        if (state.outputReady && state.outputFile != null) {
            Text("PDF generado ✓", color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { shareLauncher.launch(vm.shareIntentFor(state.outputFile!!)) },
                    modifier = Modifier.weight(1f)
                ) { Text("Compartir") }
                OutlinedButton(
                    onClick = { saveLauncher.launch("contrato-relleno.pdf") },
                    modifier = Modifier.weight(1f)
                ) { Text("Guardar") }
            }
        }

        OutlinedButton(onClick = vm::back) { Text("Atrás") }
    }
}

@Composable
private fun LabeledSlider(
    label: String, value: Float, min: Float = 0f, max: Float = 1f, onChange: (Float) -> Unit
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Slider(value = value, onValueChange = onChange, valueRange = min..max)
    }
}
