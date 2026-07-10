package com.mejoresiagratis.rellenador.ui.wizard

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureStep(state: WizardUiState, vm: WizardViewModel) {
    val context = LocalContext.current
    var mode by remember { mutableIntStateOf(0) }  // 0 = dibujar, 1 = extraer de foto

    // Generar preview y cargar firmas guardadas al entrar.
    LaunchedEffect(Unit) { vm.buildPreview(); vm.refreshSavedSignatures() }

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


        // --- Opciones avanzadas de firma (Tanda E) ---
        ElevatedCard {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Color de tinta", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val inks = listOf(
                        "Azul" to android.graphics.Color.rgb(20, 30, 90),
                        "Negro" to android.graphics.Color.rgb(20, 20, 20),
                        "Azul claro" to android.graphics.Color.rgb(30, 80, 180)
                    )
                    inks.forEach { (label, color) ->
                        FilterChip(
                            selected = state.inkColor == color,
                            onClick = { vm.setInkColor(color) },
                            label = { Text(label) }
                        )
                    }
                }
                Text("Fondo", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.sigBackground == com.mejoresiagratis.rellenador.data.pdf.SignatureProcessor.Background.TRANSPARENT,
                        onClick = { vm.setSigBackground(com.mejoresiagratis.rellenador.data.pdf.SignatureProcessor.Background.TRANSPARENT) },
                        label = { Text("Transparente") }
                    )
                    FilterChip(
                        selected = state.sigBackground == com.mejoresiagratis.rellenador.data.pdf.SignatureProcessor.Background.WHITE,
                        onClick = { vm.setSigBackground(com.mejoresiagratis.rellenador.data.pdf.SignatureProcessor.Background.WHITE) },
                        label = { Text("Blanco") }
                    )
                }
                if (state.savedSignatures.isNotEmpty()) {
                    Text("Firmas guardadas", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.savedSignatures.forEach { name ->
                            AssistChip(onClick = { vm.useSavedSignature(name) }, label = { Text(name) })
                        }
                    }
                }
            }
        }

        if (state.signature != null) {
            // Previsualización de la firma ya procesada (recorte/tinta/fondo aplicados).
            val sigBmp = remember(state.signature) {
                state.signature?.let {
                    BitmapFactory.decodeByteArray(it.pngBytes, 0, it.pngBytes.size)
                }
            }
            if (sigBmp != null) {
                Box(
                    Modifier.fillMaxWidth().height(120.dp)
                        .background(Color(0xFFE0E0E0)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(sigBmp.asImageBitmap(), contentDescription = "Firma procesada",
                        modifier = Modifier.height(100.dp))
                }
            }
            AssistChip(onClick = {}, label = { Text("Firma preparada ✓") })

            // --- Páginas de firma detectadas (Tanda B) ---
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text("Páginas de firma detectadas: ${state.signPages.size}",
                style = MaterialTheme.typography.labelLarge)
            if (state.signPages.isEmpty()) {
                Text("No se detectaron huecos automáticamente. Añade páginas manualmente.",
                    style = MaterialTheme.typography.bodySmall)
            }
            state.signPages.sorted().forEach { idx ->
                ElevatedCard {
                    ListItem(
                        headlineContent = { Text("Página ${idx + 1}") },
                        supportingContent = {
                            val anchored = state.signAnchors.containsKey(idx)
                            Text(if (anchored) "Firma anclada bajo «EL DISTRIBUIDOR»" else "Colocación por defecto")
                        },
                        trailingContent = {
                            Row {
                                TextButton(onClick = { vm.stampOnePage(idx) }) { Text("Colocar") }
                                IconButton(onClick = { vm.removeSignPage(idx) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Quitar")
                                }
                            }
                        }
                    )
                }
            }
            // Añadir página manual
            var pageInput by remember { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = { pageInput = it.filter { c -> c.isDigit() } },
                    label = { Text("Nº página") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = {
                    pageInput.toIntOrNull()?.let { vm.addSignPage(it) }; pageInput = ""
                }) { Text("Añadir") }
            }
            // Estampado masivo
            if (state.signPages.size > 1) {
                Button(onClick = vm::stampAllPages, modifier = Modifier.fillMaxWidth()) {
                    Text("Firmar todas las páginas (${state.signPages.size})")
                }
            }

            // Ajuste manual de posición/tamaño en la página 24
            val stamp = state.stamps.firstOrNull()
            if (stamp != null) {
                Text("Ajuste en la página 24 (posición y tamaño):",
                    style = MaterialTheme.typography.labelLarge)
                LabeledSlider("Horizontal", stamp.xRel) { vm.updateStamp(it, stamp.yRel, stamp.widthRel) }
                LabeledSlider("Vertical", stamp.yRel) { vm.updateStamp(stamp.xRel, it, stamp.widthRel) }
                LabeledSlider("Tamaño", stamp.widthRel, 0.1f, 0.6f) { vm.updateStamp(stamp.xRel, stamp.yRel, it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                var sigName by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = sigName, onValueChange = { sigName = it },
                    label = { Text("Nombre") }, singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = {
                    if (sigName.isNotBlank()) vm.saveCurrentSignature(sigName.trim())
                }) { Text("Guardar firma") }
            }
            OutlinedButton(onClick = vm::clearSignature) { Text("Quitar firma") }
        }

        HorizontalDivider()

        // --- Previsualización del PDF (Tanda C) ---
        Text("Previsualización", style = MaterialTheme.typography.titleSmall)
        Text("54 páginas. Toca una página de firma para recolocar la firma ahí.",
            style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = vm::buildPreview, modifier = Modifier.fillMaxWidth()) {
            Text("Actualizar previsualización")
        }
        if (state.previewReady) {
            PdfPreview(state, vm, modifier = Modifier.fillMaxWidth().height(560.dp))
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
