package com.mejoresiagratis.rellenador.ui.wizard

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Description
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

import com.mejoresiagratis.rellenador.ui.components.ExpressiveAccordion

/**
 * Paso 5 · Firma. Alineado con el paso 4 de la app web (rellenador-pro.html) en lo que
 * aporta valor real, sin importar cosas que no encajan con la arquitectura Kotlin actual.
 * Cambios sobre la versión anterior:
 *  - Ajustes de firma en acordeón plegable (M3 Expressive, misma ExpressiveAccordion que
 *    ya usa DocumentsStep para "Documentos"/"Motores IA").
 *  - Huecos de firma en acordeón plegable, con 2 sub-secciones numeradas
 *    "1 · Páginas detectadas" y "2 · Estampar la firma" (como en la web).
 *  - Estampado en dos modos: "Una a una" (recorre hueco por hueco) y "⚡ Todos" (masivo).
 *  - Paleta de tintas ampliada a 6 (Negro, Azul bolígrafo, Azul claro, Turquesa, Sepia,
 *    Violeta) — antes solo 3.
 *  - Checkbox "Mejorar con IA (localizar y limpiar)" en modo Extraer de foto — antes
 *    siempre se aplicaba localización IA sin dar opción al usuario.
 *  - Botón dedicado "Hacer foto" con la cámara, además del selector de archivos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureStep(state: WizardUiState, vm: WizardViewModel) {
    val context = LocalContext.current
    var mode by remember { mutableIntStateOf(0) }  // 0 = dibujar, 1 = extraer de foto
    var pickedPhotoForCrop by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    // Estado UI local para "Mejorar con IA (localizar y limpiar)" — no persiste en el
    // state global porque es una preferencia por sesión, no una configuración de perfil.
    var aiCleanEnabled by remember { mutableStateOf(true) }
    // Acordeones plegados por defecto (como en la web).
    var adjustsExpanded by remember { mutableStateOf(false) }
    var holesExpanded by remember { mutableStateOf(false) }
    // Índice del hueco a firmar en modo "Una a una" (0-based sobre state.signPages ordenadas).
    var oneByOneIdx by remember { mutableStateOf(0) }
    // Estado compartido entre botones y previsualización: al pulsar "Una a una" o
    // "Todos", scroll animado al item de la página estampada, más feedback en snackbar.
    val previewListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { vm.buildPreview(); vm.refreshSavedSignatures() }

    if (pickedPhotoForCrop != null) {
        SignatureCropDialog(
            photo = pickedPhotoForCrop!!,
            onConfirm = { cropped -> vm.useManualSignatureCrop(cropped); pickedPhotoForCrop = null },
            onUseWholePhoto = { vm.useWholePhotoAsSignature(pickedPhotoForCrop!!); pickedPhotoForCrop = null },
            onCancel = { pickedPhotoForCrop = null }
        )
    }

    // Handler común para foto elegida (galería o cámara) — respeta la preferencia
    // aiCleanEnabled: si está activo, se localiza+limpia con IA (comportamiento
    // anterior). Si está desactivado, se abre directamente el recorte manual sin
    // pasar por la IA.
    fun handlePhoto(bmp: android.graphics.Bitmap) {
        vm.rememberPickedPhoto(bmp)
        if (aiCleanEnabled) {
            pickedPhotoForCrop = bmp   // el diálogo tendrá el botón "usar toda" que sí llama a IA
        } else {
            // Salto directo al recorte manual, sin ofrecer procesado IA.
            pickedPhotoForCrop = bmp
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { ins ->
                BitmapFactory.decodeStream(ins)?.let { bmp -> handlePhoto(bmp) }
            }
        }
    }
    val cameraPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bmp -> bmp?.let { handlePhoto(it) } }

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

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Dibujar", "Extraer de foto").forEachIndexed { i, label ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                    selected = mode == i,
                    onClick = { mode = i }
                ) { Text(label) }
            }
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
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Sube una foto de un documento firmado o hazla ahora.",
                        style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { photoPicker.launch(arrayOf("image/*")) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Elegir foto") }
                        Button(
                            onClick = { cameraPicker.launch(null) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("📷 Hacer foto")
                        }
                    }
                    // Checkbox "Mejorar con IA (localizar y limpiar)" — activo por defecto
                    // (mismo comportamiento que la app web). Si el usuario lo desactiva,
                    // se abre el recorte manual sin pasar por la localización IA.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = aiCleanEnabled, onCheckedChange = { aiCleanEnabled = it })
                        Text("Mejorar con IA (localizar y limpiar)",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (state.locatingSignature) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp)); Text("Localizando firma…")
                        }
                    }
                    // "Refrescar" — vuelve a preguntar a la IA sobre la MISMA foto. Los
                    // modelos de visión no son perfectamente deterministas: una segunda
                    // pasada puede acertar una caja más ajustada (p.ej. sin colar una línea
                    // impresa cercana a la firma) sin tener que volver a hacer la foto.
                    // Solo tiene sentido si hubo una extracción por IA de por medio.
                    if (aiCleanEnabled && state.signature != null && vm.lastPickedPhotoOrNull() != null && !state.locatingSignature) {
                        OutlinedButton(
                            onClick = vm::retryAiExtraction,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("🔄 Volver a intentar con IA") }
                    }
                }
            }
        }

        // Previsualización compacta de la firma procesada — SOLO en modo Extraer de foto
        // (modo 1). En modo Dibujar (0), SignatureCanvas ya muestra internamente el
        // trazo como preview con el estilo aplicado, así que enseñar aquí otra caja
        // gris con la misma firma quedaba duplicado.
        if (state.signature != null && mode == 1) {
            val sigBmp = remember(state.signature) {
                state.signature?.let {
                    BitmapFactory.decodeByteArray(it.pngBytes, 0, it.pngBytes.size)
                }
            }
            if (sigBmp != null) {
                Box(
                    Modifier.fillMaxWidth().height(120.dp).background(Color(0xFFE0E0E0)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(sigBmp.asImageBitmap(), contentDescription = "Firma procesada",
                        modifier = Modifier.height(100.dp))
                }
            }
        }

        // Chip de estado — se muestra en ambos modos porque es información útil.
        if (state.signature != null) {
            val nPages = state.signPages.size.coerceAtLeast(state.stamps.size)
            AssistChip(onClick = {}, label = {
                Text(if (nPages > 0) "Firma cargada ✓ · lista para $nPages página${if (nPages == 1) "" else "s"}"
                     else "Firma cargada ✓")
            })
        }

        // Retocar firma a mano: borra partes de fondo que no son el trazo real (p.ej.
        // una línea impresa cercana que se coló como firma) — complementa el "Volver a
        // intentar con IA" de arriba para los casos que ni la IA ni el umbral resuelven
        // bien del todo. Disponible en ambos modos (Dibujar y Extraer de foto).
        var showEraser by remember { mutableStateOf(false) }
        if (state.signature != null) {
            OutlinedButton(
                onClick = { showEraser = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("🧹 Retocar firma") }
        }
        if (showEraser) {
            val toEdit = remember(state.signature) {
                state.signature?.let { BitmapFactory.decodeByteArray(it.pngBytes, 0, it.pngBytes.size) }
            }
            if (toEdit != null) {
                SignatureEraserDialog(
                    original = toEdit,
                    onSave = { edited -> vm.applyErasedSignature(edited); showEraser = false },
                    onCancel = { showEraser = false }
                )
            }
        }

        // --- Ajustes de firma (acordeón plegable) ---
        ExpressiveAccordion(
            title = "Ajustes de firma",
            icon = Icons.Filled.Settings,
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            onContainerColor = MaterialTheme.colorScheme.onSecondaryContainer,
            expanded = adjustsExpanded,
            onToggle = { adjustsExpanded = !adjustsExpanded }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Color de tinta", style = MaterialTheme.typography.labelLarge)
                // Paleta ampliada a 6 tintas (misma que la app web).
                val inks = listOf(
                    "Negro" to android.graphics.Color.rgb(20, 20, 20),
                    "Azul bolígrafo" to android.graphics.Color.rgb(22, 48, 140),
                    "Azul claro" to android.graphics.Color.rgb(27, 63, 191),
                    "Turquesa" to android.graphics.Color.rgb(14, 110, 110),
                    "Sepia vintage" to android.graphics.Color.rgb(91, 58, 41),
                    "Tinta violeta" to android.graphics.Color.rgb(75, 46, 131)
                )
                FlowRowSpaced {
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
                    FlowRowSpaced {
                        state.savedSignatures.forEach { name ->
                            AssistChip(onClick = { vm.useSavedSignature(name) }, label = { Text(name) })
                        }
                    }
                }
                // Guardar la firma actual con nombre
                if (state.signature != null) {
                    var sigName by remember { mutableStateOf("") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = sigName, onValueChange = { sigName = it },
                            label = { Text("Nombre") }, singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = {
                            if (sigName.isNotBlank()) { vm.saveCurrentSignature(sigName.trim()); sigName = "" }
                        }) { Text("Guardar firma") }
                    }
                }
            }
        }

        // --- Huecos de firma (acordeón plegable, solo si hay firma cargada) ---
        if (state.signature != null) {
            ExpressiveAccordion(
                title = "Huecos de firma",
                count = state.signPages.size,
                icon = Icons.Outlined.Description,
                shape = MaterialTheme.shapes.extraLarge,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                onContainerColor = MaterialTheme.colorScheme.onTertiaryContainer,
                expanded = holesExpanded,
                onToggle = { holesExpanded = !holesExpanded }
            ) {
                // 1 · Páginas detectadas
                Text("1 · Páginas detectadas", style = MaterialTheme.typography.labelLarge)
                Text("Confirma, descarta (×) o añade páginas:", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                if (state.signPages.isEmpty()) {
                    Text("No se detectaron huecos automáticamente. Añade páginas manualmente.",
                        style = MaterialTheme.typography.bodySmall)
                }
                FlowRowSpaced {
                    state.signPages.sorted().forEach { idx ->
                        // Chip = colocar en esa página. IconButton pequeño al lado = quitar.
                        // No usamos trailingIcon del AssistChip porque no permite un onClick
                        // independiente del onClick principal del chip — quitaría la lógica
                        // de "colocar" y "quitar" al mismo tiempo.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AssistChip(
                                onClick = { vm.stampOnePage(idx) },
                                label = { Text("pág ${idx + 1}") }
                            )
                            IconButton(
                                onClick = { vm.removeSignPage(idx) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Quitar pág ${idx + 1}",
                                    modifier = Modifier.size(16.dp))
                            }
                        }
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
                    }) { Text("＋ Añadir") }
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                // 2 · Estampar la firma
                Text("2 · Estampar la firma", style = MaterialTheme.typography.labelLarge)
                Text("«Una a una» estampa en el hueco actual y avanza al siguiente; «⚡ Todos» los estampa todos de golpe.",
                    style = MaterialTheme.typography.bodySmall)
                val pages = state.signPages.sorted()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (pages.isNotEmpty()) {
                                val target = pages.getOrNull(oneByOneIdx) ?: pages.first()
                                vm.stampOnePage(target)
                                if (oneByOneIdx < pages.size - 1) oneByOneIdx++ else oneByOneIdx = 0
                                // Primero se reconstruye la previsualización (para que la
                                // página ya muestre la firma recién estampada) y SOLO
                                // ENTONCES se hace scroll — antes el scroll llegaba antes
                                // de que el PDF de previsualización reflejara el cambio, y
                                // la página aparecía momentáneamente "en blanco".
                                scope.launch {
                                    vm.rebuildPreviewNow()
                                    previewListState.animateScrollToItem(target)
                                    snackbarHostState.showSnackbar("Firma estampada en la pág ${target + 1}")
                                }
                            }
                        },
                        enabled = pages.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (pages.isEmpty()) "🎯 Una a una"
                             else "🎯 Una a una (pág ${(pages.getOrNull(oneByOneIdx) ?: pages.first()) + 1})")
                    }
                    Button(
                        onClick = {
                            vm.stampAllPages()
                            // Misma corrección: reconstruir la previsualización ANTES de
                            // hacer scroll, para poder ver todas las páginas ya firmadas.
                            val first = pages.firstOrNull()
                            if (first != null) scope.launch {
                                vm.rebuildPreviewNow()
                                previewListState.animateScrollToItem(first)
                                snackbarHostState.showSnackbar("Firmadas ${pages.size} páginas")
                            }
                        },
                        enabled = pages.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("⚡ Todos (${pages.size})")
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vm.lastPickedPhotoOrNull() != null) {
                    OutlinedButton(onClick = { pickedPhotoForCrop = vm.lastPickedPhotoOrNull() }) {
                        Text("Recortar de nuevo")
                    }
                }
                OutlinedButton(onClick = vm::clearSignature) { Text("Quitar firma") }
            }
        }

        HorizontalDivider()

        // --- Previsualización del PDF ---
        Text("Previsualización", style = MaterialTheme.typography.titleSmall)
        Text("54 páginas. Toca una página de firma para recolocar la firma ahí.",
            style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = vm::buildPreview, modifier = Modifier.fillMaxWidth()) {
            Text("Actualizar previsualización")
        }
        // Navegación entre huecos de firma como hace la web (flechas ↑↓ con contador).
        // Solo se muestra si hay huecos detectados y la previsualización está lista.
        if (state.previewReady && state.signPages.isNotEmpty()) {
            val holes = remember(state.signPages) { state.signPages.sorted() }
            var currentHoleIdx by remember { mutableStateOf(0) }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ir al hueco:", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            currentHoleIdx = (currentHoleIdx - 1 + holes.size) % holes.size
                            scope.launch { previewListState.animateScrollToItem(holes[currentHoleIdx]) }
                        },
                        modifier = Modifier.size(width = 48.dp, height = 40.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("↑") }
                    Text(
                        "  ${currentHoleIdx + 1}/${holes.size} · p.${holes[currentHoleIdx] + 1}  ",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(
                        onClick = {
                            currentHoleIdx = (currentHoleIdx + 1) % holes.size
                            scope.launch { previewListState.animateScrollToItem(holes[currentHoleIdx]) }
                        },
                        modifier = Modifier.size(width = 48.dp, height = 40.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("↓") }
                }
            }
        }
        if (state.previewReady) {
            PdfPreview(
                state, vm,
                modifier = Modifier.fillMaxWidth().height(560.dp),
                listState = previewListState
            )
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
            // Empezar otro contrato: reset del wizard y vuelta al Paso 1. Solo se ofrece
            // cuando el PDF ya está generado — antes de eso hay progreso en curso que
            // el usuario probablemente no quiere descartar sin querer.
            var showRestartConfirm by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = { showRestartConfirm = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Empezar otro contrato") }
            if (showRestartConfirm) {
                AlertDialog(
                    onDismissRequest = { showRestartConfirm = false },
                    title = { Text("¿Empezar otro contrato?") },
                    text = { Text("Se borrará el progreso actual (paso, documentos, extracción, " +
                        "firma y datos). El PDF que acabas de generar se conserva si ya lo has " +
                        "guardado o compartido.") },
                    confirmButton = {
                        TextButton(onClick = {
                            showRestartConfirm = false
                            vm.resetSession()
                        }) { Text("Empezar otro") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRestartConfirm = false }) { Text("Cancelar") }
                    }
                )
            }
        }

        OutlinedButton(onClick = vm::back) { Text("Atrás") }
    }
    // Snackbar como overlay flotante en la parte inferior — patrón Material estándar.
    // Muestra los mensajes de "Firma estampada en la pág X" / "Firmadas N páginas" sin
    // desplazar el contenido del scroll.
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
    )
    }
}

/** FlowRow con separación consistente para chips — como no está en material3 sin opt-in
 *  experimental, usamos Row con wrap manual mediante FlowRow de foundation. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowSpaced(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) { content() }
}
