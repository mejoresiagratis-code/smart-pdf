package com.mejoresiagratis.rellenador.ui.wizard

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
            PdfPageView(
                renderer = renderer,
                pageIdx = pageIdx,
                isSignPage = pageIdx in state.signPages,
                onTapToPlace = { xRel, yRel -> vm.moveStamp(pageIdx, xRel, yRel) }
            )
        }
    }
}

@Composable
private fun PdfPageView(
    renderer: PdfPageRenderer,
    pageIdx: Int,
    isSignPage: Boolean,
    onTapToPlace: (Float, Float) -> Unit
) {
    var bitmap by remember(pageIdx) { mutableStateOf<Bitmap?>(null) }
    var containerWidthPx by remember { mutableIntStateOf(0) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(pageIdx, containerWidthPx) {
        if (containerWidthPx > 0) {
            bitmap = withContext(Dispatchers.Default) {
                runCatching { renderer.render(pageIdx, containerWidthPx) }.getOrNull()
            }
        }
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { containerWidthPx = it.width; containerSize = it }
                .pointerInput(pageIdx, isSignPage) {
                    if (isSignPage) {
                        detectTapGestures { offset ->
                            if (containerSize.width > 0 && containerSize.height > 0) {
                                onTapToPlace(
                                    offset.x / containerSize.width,
                                    offset.y / containerSize.height
                                )
                            }
                        }
                    }
                }
        ) {
            val bmp = bitmap
            if (bmp != null && !bmp.isRecycled) {
                Image(bmp.asImageBitmap(), contentDescription = "Página ${pageIdx + 1}",
                    modifier = Modifier.fillMaxWidth())
            } else {
                Box(Modifier.fillMaxWidth().height(400.dp).background(Color(0xFFF0F0F0)),
                    contentAlignment = Alignment.Center) { CircularProgressIndicator() }
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
                    Text("✍ firma · toca para mover", color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
        }
    }
}
