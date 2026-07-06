package com.mejoresiagratis.rellenador.data.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import java.io.Closeable
import java.io.File

/**
 * Renderiza páginas de un PDF bajo demanda (una a una), no todas de golpe.
 * Mantiene el PdfRenderer abierto sobre un File y cachea las últimas páginas.
 * Pensado para previsualizar el contrato de 54 páginas sin agotar memoria.
 */
class PdfPageRenderer(private val file: File) : Closeable {

    private val pfd: ParcelFileDescriptor =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(pfd)

    val pageCount: Int get() = renderer.pageCount

    private val cache = object : LinkedHashMap<Int, Bitmap>(6, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Bitmap>?): Boolean {
            val over = size > 4
            if (over) eldest?.value?.recycle()
            return over
        }
    }

    /** Relación alto/ancho de una página (para dimensionar el contenedor). */
    fun aspectRatio(index: Int): Float {
        renderer.openPage(index).use { p ->
            return if (p.width > 0) p.height.toFloat() / p.width.toFloat() else 1.414f
        }
    }

    /** Renderiza la página [index] a un ancho objetivo en px (alto proporcional). */
    fun render(index: Int, targetWidthPx: Int): Bitmap {
        cache[index]?.let { if (!it.isRecycled) return it }
        renderer.openPage(index).use { page ->
            val scale = targetWidthPx.toFloat() / page.width
            val w = targetWidthPx.coerceAtLeast(1)
            val h = (page.height * scale).toInt().coerceAtLeast(1)
            val bmp = createBitmap(w, h)
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            cache[index] = bmp
            return bmp
        }
    }

    override fun close() {
        cache.values.forEach { if (!it.isRecycled) it.recycle() }
        cache.clear()
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }
}
