package com.mejoresiagratis.rellenador.ui.wizard

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap

/**
 * Lienzo de firma manuscrita. Entrega el Bitmap del trazo (fondo blanco, trazo
 * negro) al confirmar. `controls` recibe las acciones limpiar/confirmar.
 */
@Composable
fun SignatureCanvas(
    modifier: Modifier = Modifier,
    strokeWidthPx: Float = 5f,
    onBitmap: (Bitmap) -> Unit,
    controls: @Composable (clear: () -> Unit, done: () -> Unit) -> Unit
) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasW by remember { mutableIntStateOf(1) }
    var canvasH by remember { mutableIntStateOf(1) }

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { off -> current = listOf(off) },
                        onDrag = { change, _ -> current = current + change.position; change.consume() },
                        onDragEnd = { if (current.size > 1) strokes.add(current); current = emptyList() }
                    )
                }
        ) {
            canvasW = size.width.toInt().coerceAtLeast(1)
            canvasH = size.height.toInt().coerceAtLeast(1)
            (strokes + listOf(current)).forEach { pts ->
                if (pts.size > 1) {
                    val path = Path().apply {
                        moveTo(pts.first().x, pts.first().y)
                        pts.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(path, Color.Black, style = Stroke(width = strokeWidthPx))
                }
            }
        }
        controls(
            { strokes.clear(); current = emptyList() },
            {
                val bmp = createBitmap(canvasW, canvasH)
                val c = AndroidCanvas(bmp)
                c.drawColor(android.graphics.Color.WHITE)
                val paint = Paint().apply {
                    color = android.graphics.Color.BLACK; strokeWidth = strokeWidthPx
                    style = Paint.Style.STROKE; isAntiAlias = true
                    strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
                }
                (strokes + listOf(current)).forEach { pts ->
                    if (pts.size > 1) {
                        val ap = AndroidPath().apply {
                            moveTo(pts.first().x, pts.first().y)
                            pts.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        c.drawPath(ap, paint)
                    }
                }
                onBitmap(bmp)
            }
        )
    }
}
