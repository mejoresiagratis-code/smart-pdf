package com.mejoresiagratis.rellenador.ui.wizard

import android.graphics.Bitmap
import android.graphics.Color as AColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Retocar firma a mano: permite borrar (poner transparente) partes del resultado ya
 * procesado que NO son el trazo real — p.ej. una línea impresa cercana que la IA o el
 * umbral de tinta confundieron con firma. Complementa (no sustituye) la localización
 * automática: sirve como último ajuste fino sobre cualquier resultado, sea de IA, de
 * recorte manual, o de foto completa.
 *
 * El bitmap de trabajo se muta directamente (más simple y eficiente que reconstruir
 * IntArray en cada gesto); `version` fuerza la recomposición de la Image ya que Compose
 * no puede detectar mutaciones internas de un Bitmap por sí solo.
 */
@Composable
fun SignatureEraserDialog(
    original: Bitmap,
    onSave: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    // Copia mutable de trabajo — nunca se toca `original` directamente, así "Deshacer
    // todo" siempre puede volver al punto de partida real.
    val working = remember { original.copy(Bitmap.Config.ARGB_8888, true) }
    var version by remember { mutableStateOf(0) }
    val undoStack = remember { ArrayDeque<Bitmap>() }
    var undoCount by remember { mutableStateOf(0) }  // espejo observable de undoStack.size
    var containerSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var brushRadiusDp by remember { mutableStateOf(14f) }

    fun pushUndoSnapshot() {
        // Límite de 15 pasos de deshacer — suficiente para corregir un retoque sin
        // acumular copias de bitmap indefinidamente en memoria.
        if (undoStack.size >= 15) undoStack.removeFirst() else undoCount++
        undoStack.addLast(working.copy(Bitmap.Config.ARGB_8888, true))
    }

    fun eraseAt(ix: Int, iy: Int, radiusPx: Int) {
        val r2 = radiusPx * radiusPx
        val x0 = (ix - radiusPx).coerceAtLeast(0)
        val x1 = (ix + radiusPx).coerceAtMost(working.width - 1)
        val y0 = (iy - radiusPx).coerceAtLeast(0)
        val y1 = (iy + radiusPx).coerceAtMost(working.height - 1)
        if (x0 > x1 || y0 > y1) return
        for (y in y0..y1) for (x in x0..x1) {
            val dx = x - ix; val dy = y - iy
            if (dx * dx + dy * dy <= r2) working.setPixel(x, y, AColor.TRANSPARENT)
        }
    }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Retocar firma", style = MaterialTheme.typography.titleMedium)
                    Text("Arrastra el dedo sobre lo que NO es tu firma (líneas impresas, " +
                        "manchas de fondo…) para borrarlo.", style = MaterialTheme.typography.bodySmall)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .background(Color(0xFFBDBDBD))  // gris medio: la transparencia se ve clara
                        .onSizeChanged { containerSize = it }
                ) {
                    // `remember(version)` fuerza recalcular el ImageBitmap cuando el bitmap
                    // mutable cambia tras cada trazo — Compose no puede detectar mutaciones
                    // internas de un Bitmap por sí solo, solo referencias nuevas.
                    val displayBitmap = remember(version) { working.asImageBitmap() }
                    Image(
                        displayBitmap,
                        contentDescription = "Firma a retocar",
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(working) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        pushUndoSnapshot()
                                        val (ix, iy) = mapToBitmap(offset, working, containerSize) ?: return@detectDragGestures
                                        eraseAt(ix, iy, brushRadiusDp.toInt())
                                        version++
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val (ix, iy) = mapToBitmap(change.position, working, containerSize) ?: return@detectDragGestures
                                        eraseAt(ix, iy, brushRadiusDp.toInt())
                                        version++
                                    }
                                )
                            }
                            .pointerInput(working) {
                                // Un toque simple también borra (útil para motas puntuales
                                // sin necesidad de arrastrar).
                                detectTapGestures(onTap = { offset ->
                                    pushUndoSnapshot()
                                    val (ix, iy) = mapToBitmap(offset, working, containerSize) ?: return@detectTapGestures
                                    eraseAt(ix, iy, brushRadiusDp.toInt())
                                    version++
                                })
                            }
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Grosor", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = brushRadiusDp, onValueChange = { brushRadiusDp = it },
                        valueRange = 6f..30f, modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                    OutlinedButton(
                        onClick = {
                            if (undoStack.isNotEmpty()) {
                                val prev = undoStack.removeLast()
                                undoCount--
                                val px = IntArray(prev.width * prev.height)
                                prev.getPixels(px, 0, prev.width, 0, 0, prev.width, prev.height)
                                working.setPixels(px, 0, prev.width, 0, 0, prev.width, prev.height)
                                version++
                            }
                        },
                        enabled = undoCount > 0,
                        modifier = Modifier.weight(1f)
                    ) { Text("Deshacer") }
                    Button(onClick = { onSave(working) }, modifier = Modifier.weight(1f)) {
                        Text("Guardar")
                    }
                }
            }
        }
    }
}

/**
 * Traduce un punto de pantalla (dentro del Box donde se dibuja la Image con
 * ContentScale.Fit por defecto) a coordenadas de píxel del bitmap. Devuelve null si el
 * contenedor aún no tiene tamaño medido. Misma lógica que SignatureCropDialog — ver el
 * comentario allí sobre por qué no basta un escalado lineal ingenuo cuando hay letterbox.
 */
private fun mapToBitmap(offset: Offset, bmp: Bitmap, containerSize: androidx.compose.ui.unit.IntSize): Pair<Int, Int>? {
    if (containerSize.width == 0 || containerSize.height == 0) return null
    val photoAspect = bmp.width.toFloat() / bmp.height.toFloat()
    val containerAspect = containerSize.width.toFloat() / containerSize.height.toFloat()
    val renderedW: Float; val renderedH: Float
    if (photoAspect > containerAspect) {
        renderedW = containerSize.width.toFloat()
        renderedH = renderedW / photoAspect
    } else {
        renderedH = containerSize.height.toFloat()
        renderedW = renderedH * photoAspect
    }
    val offsetX = (containerSize.width - renderedW) / 2f
    val offsetY = (containerSize.height - renderedH) / 2f
    val ix = (((offset.x - offsetX) / renderedW) * bmp.width).coerceIn(0f, bmp.width - 1f)
    val iy = (((offset.y - offsetY) / renderedH) * bmp.height).coerceIn(0f, bmp.height - 1f)
    return ix.toInt() to iy.toInt()
}
