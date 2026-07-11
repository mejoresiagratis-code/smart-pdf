package com.mejoresiagratis.rellenador.ui.wizard

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Recorte MANUAL de la firma en una foto: el usuario arrastra un rectángulo sobre
 * la imagen completa. Se usa como alternativa fiable a la localización automática
 * por IA, que en fotos con trazos finos o mucho margen puede recortar mal o perder
 * parte de la firma. El usuario decide exactamente qué región procesar.
 */
@Composable
fun SignatureCropDialog(
    photo: Bitmap,
    onConfirm: (Bitmap) -> Unit,
    onUseWholePhoto: () -> Unit,
    onCancel: () -> Unit
) {
    var containerSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Recorta la firma", style = MaterialTheme.typography.titleMedium)
                    Text("Arrastra el dedo sobre la firma para marcar la zona exacta.",
                        style = MaterialTheme.typography.bodySmall)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .onSizeChanged { containerSize = it }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset -> dragStart = offset; dragEnd = offset },
                                onDrag = { change, _ -> dragEnd = change.position; change.consume() }
                            )
                        }
                ) {
                    Image(
                        photo.asImageBitmap(),
                        contentDescription = "Foto a recortar",
                        modifier = Modifier.fillMaxSize()
                    )
                    val start = dragStart; val end = dragEnd
                    if (start != null && end != null) {
                        Canvas(Modifier.fillMaxSize()) {
                            val rect = Rect(
                                minOf(start.x, end.x), minOf(start.y, end.y),
                                maxOf(start.x, end.x), maxOf(start.y, end.y)
                            )
                            // Oscurecer fuera del recorte para ver claramente qué queda dentro.
                            drawRect(Color.Black.copy(alpha = 0.5f), topLeft = Offset(0f, 0f),
                                size = androidx.compose.ui.geometry.Size(size.width, rect.top))
                            drawRect(Color.Black.copy(alpha = 0.5f), topLeft = Offset(0f, rect.bottom),
                                size = androidx.compose.ui.geometry.Size(size.width, size.height - rect.bottom))
                            drawRect(Color.Black.copy(alpha = 0.5f), topLeft = Offset(0f, rect.top),
                                size = androidx.compose.ui.geometry.Size(rect.left, rect.height))
                            drawRect(Color.Black.copy(alpha = 0.5f), topLeft = Offset(rect.right, rect.top),
                                size = androidx.compose.ui.geometry.Size(size.width - rect.right, rect.height))
                            drawRect(Color(0xFFFF9800), topLeft = rect.topLeft, size = rect.size,
                                style = Stroke(width = 3f))
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                    OutlinedButton(onClick = onUseWholePhoto, modifier = Modifier.weight(1f)) {
                        Text("Foto completa")
                    }
                    Button(
                        onClick = {
                            val start = dragStart; val end = dragEnd
                            if (start == null || end == null || containerSize.width == 0) return@Button
                            val scaleX = photo.width.toFloat() / containerSize.width
                            val scaleY = photo.height.toFloat() / containerSize.height
                            val x0 = (minOf(start.x, end.x) * scaleX).toInt().coerceIn(0, photo.width - 1)
                            val y0 = (minOf(start.y, end.y) * scaleY).toInt().coerceIn(0, photo.height - 1)
                            val x1 = (maxOf(start.x, end.x) * scaleX).toInt().coerceIn(x0 + 1, photo.width)
                            val y1 = (maxOf(start.y, end.y) * scaleY).toInt().coerceIn(y0 + 1, photo.height)
                            val cropped = Bitmap.createBitmap(photo, x0, y0, x1 - x0, y1 - y0)
                            onConfirm(cropped)
                        },
                        enabled = dragStart != null && dragEnd != null,
                        modifier = Modifier.weight(1f)
                    ) { Text("Confirmar") }
                }
            }
        }
    }
}
