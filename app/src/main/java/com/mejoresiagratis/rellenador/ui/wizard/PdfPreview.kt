package com.mejoresiagratis.rellenador.ui.wizard

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mejoresiagratis.rellenador.data.pdf.PdfPageRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Previsualización del PDF relleno: 54 páginas navegables (LazyColumn), render bajo
 * demanda, badge "✍ firma" en páginas de firma, y toque para mover la firma de esa
 * página. Depende de que el ViewModel haya generado el preview (previewReady).
 */
@Composable
fun PdfPreview(
    state: WizardUiState,
    vm: WizardViewModel,
    modifier: Modifier = Modifier
) {
    val renderer = vm.renderer() ?: return
    LazyColumn(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items((0 until renderer.pageCount).toList(), key = { it }) { pageIdx ->
            val stamp = state.stamps.firstOrNull { it.pageIndex == pageIdx }
            PdfPageView(
                renderer = renderer,
                pageIdx = pageIdx,
                isSignPage = pageIdx in state.signPages,
                stampXRel = stamp?.xRel,
                stampYRel = stamp?.yRel,
                onMove = { xRel, yRel -> vm.moveStamp(pageIdx, xRel, yRel) }
            )
        }
    }
}

@Composable
private fun PdfPageView(
    renderer: PdfPageRenderer,
    pageIdx: Int,
    isSignPage: Boolean,
    stampXRel: Float?,
    stampYRel: Float?,
    onMove: (Float, Float) -> Unit
) {
    var bitmap by remember(pageIdx) { mutableStateOf<Bitmap?>(null) }
    var containerWidthPx by remember { mutableIntStateOf(0) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var selected by remember(pageIdx) { mutableStateOf(false) }

    LaunchedEffect(pageIdx, containerWidthPx) {
        if (containerWidthPx > 0) {
            bitmap = withContext(Dispatchers.Default) {
                runCatching { renderer.render(pageIdx, containerWidthPx) }.getOrNull()
            }
        }
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Box(
            // Sin gestos aquí: el scroll de la lista funciona libre en toda la página;
            // solo el marcador (abajo) responde al toque/arrastre.
            Modifier
                .fillMaxWidth()
                .onSizeChanged { containerWidthPx = it.width; containerSize = it }
        ) {
            val bmp = bitmap
            if (bmp != null && !bmp.isRecycled) {
                Image(bmp.asImageBitmap(), contentDescription = "Página ${pageIdx + 1}",
                    modifier = Modifier.fillMaxWidth())
            } else {
                Box(Modifier.fillMaxWidth().height(400.dp).background(Color(0xFFF0F0F0)),
                    contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            // Marcador de la firma: primero TÓCALO para seleccionarlo (se resalta),
            // luego ARRÁSTRALO para elegir la nueva posición. Así no interfiere con
            // el scroll de la lista al pasar el dedo por el resto de la página.
            if (isSignPage && stampXRel != null && stampYRel != null &&
                containerSize.width > 0 && containerSize.height > 0) {
                val density = LocalDensity.current
                val xDp = with(density) { (stampXRel * containerSize.width).toDp() }
                val yDp = with(density) { (stampYRel * containerSize.height).toDp() }
                Box(
                    Modifier
                        .absoluteOffset(x = xDp - 22.dp, y = yDp - 14.dp)
                        .size(width = 44.dp, height = 28.dp)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                        .pointerInput(pageIdx) {
                            detectTapGestures { selected = !selected }
                        }
                        .pointerInput(pageIdx, selected) {
                            if (selected) {
                                // Acumular localmente para no perder posición entre eventos
                                // de arrastre (si se re-lanzara con cada onMove, el gesto
                                // se cancelaría a mitad de camino).
                                var curX = stampXRel
                                var curY = stampYRel
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    if (containerSize.width > 0 && containerSize.height > 0) {
                                        curX = (curX + dragAmount.x / containerSize.width).coerceIn(0f, 1f)
                                        curY = (curY + dragAmount.y / containerSize.height).coerceIn(0f, 1f)
                                        onMove(curX, curY)
                                    }
                                }
                            }
                        }
                ) {
                    Text("✍", modifier = Modifier.align(Alignment.Center))
                }
            }
            // Etiqueta de página
            Surface(color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp)) {
                Text("Pág ${pageIdx + 1}", color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
            if (isSignPage) {
                Surface(color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                    Text(
                        if (selected) "✍ arrastra para mover" else "✍ toca la firma para moverla",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
        }
    }
}
