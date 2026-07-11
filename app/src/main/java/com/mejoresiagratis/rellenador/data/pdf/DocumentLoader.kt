package com.mejoresiagratis.rellenador.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Convierte un documento aportado por el cliente (foto o PDF) en DocPayloads
 * listos para el proxy. El proxy ya reduce las imágenes en servidor (MAX_IMG_SIDE),
 * así que aquí solo garantizamos JPEG razonable.
 *
 * - Imagen  -> un DocPayload image/jpeg.
 * - PDF     -> se rasteriza cada página a JPEG (para motores de visión) mediante
 *              PdfRenderer nativo de Android. Además, si el proveedor acepta PDF
 *              nativo (Claude/Gemini), puede mandarse el PDF entero como fallback.
 */
class DocumentLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class Loaded(val name: String, val mime: String, val bytes: ByteArray)

    private fun readBytes(uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }

    private fun displayName(uri: Uri): String =
        uri.lastPathSegment?.substringAfterLast('/') ?: "documento"

    private fun mimeOf(uri: Uri): String =
        context.contentResolver.getType(uri) ?: "application/octet-stream"

    /** Renderiza todas las páginas de un PDF a JPEG (una imagen por página). */
    private fun renderPdf(uri: Uri, targetLongSide: Int = 2000): List<ByteArray> {
        val out = mutableListOf<ByteArray>()
        val pfd: ParcelFileDescriptor =
            context.contentResolver.openFileDescriptor(uri, "r") ?: return out
        pfd.use {
            PdfRenderer(it).use { renderer ->
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { page ->
                        val scale = targetLongSide.toFloat() / maxOf(page.width, page.height)
                        val w = (page.width * scale).toInt().coerceAtLeast(1)
                        val h = (page.height * scale).toInt().coerceAtLeast(1)
                        val bmp = createBitmap(w, h)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        out.add(bmp.toJpegBytes())
                        bmp.recycle()
                    }
                }
            }
        }
        return out
    }

    /** Redimensiona si el lado mayor supera targetLongSide (evita 400/500 en las IAs
     *  por fotos de móvil a resolución completa; el proxy espera imágenes razonables). */
    private fun downscaleIfNeeded(bmp: Bitmap, targetLongSide: Int = 2000): Bitmap {
        val longSide = maxOf(bmp.width, bmp.height)
        if (longSide <= targetLongSide) return bmp
        val scale = targetLongSide.toFloat() / longSide
        val w = (bmp.width * scale).toInt().coerceAtLeast(1)
        val h = (bmp.height * scale).toInt().coerceAtLeast(1)
        return bmp.scale(w, h)
    }

    private fun Bitmap.toJpegBytes(quality: Int = 85): ByteArray =
        ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

    private fun ByteArray.b64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    /**
     * Carga un URI en uno o varios DocPayloads.
     * Los PDFs se rasterizan a JPEG por página (compatibles con todos los motores de visión).
     */
    fun load(uri: Uri): List<com.mejoresiagratis.rellenador.data.model.DocPayload> {
        val mime = mimeOf(uri)
        return when {
            mime.startsWith("image/") -> {
                // Decodificar y redimensionar SIEMPRE a JPEG razonable: evita 400/500 en
                // las IAs por fotos de móvil a resolución completa (varios MB sin comprimir).
                val original = context.contentResolver.openInputStream(uri)!!.use {
                    android.graphics.BitmapFactory.decodeStream(it)
                }
                if (original == null) {
                    // Si no se puede decodificar (formato raro), fallback: bytes tal cual.
                    val bytes = readBytes(uri)
                    val outMime = if (mime in listOf("image/jpeg", "image/png", "image/webp", "image/gif")) mime else "image/jpeg"
                    listOf(com.mejoresiagratis.rellenador.data.model.DocPayload(mime = outMime, b64 = bytes.b64()))
                } else {
                    val resized = downscaleIfNeeded(original)
                    val jpg = resized.toJpegBytes(85)
                    if (resized !== original) original.recycle()
                    resized.recycle()
                    listOf(com.mejoresiagratis.rellenador.data.model.DocPayload(mime = "image/jpeg", b64 = jpg.b64()))
                }
            }
            mime == "application/pdf" -> renderPdf(uri).map {
                com.mejoresiagratis.rellenador.data.model.DocPayload(mime = "image/jpeg", b64 = it.b64())
            }
            else -> emptyList()
        }
    }
}
