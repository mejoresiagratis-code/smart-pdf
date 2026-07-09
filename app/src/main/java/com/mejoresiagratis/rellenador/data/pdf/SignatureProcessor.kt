package com.mejoresiagratis.rellenador.data.pdf

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.mejoresiagratis.rellenador.data.model.SignatureBox
import com.mejoresiagratis.rellenador.data.model.SignatureData
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Procesado de firma fiel a la web: recorte a caja IA, aplanado de iluminación,
 * umbral Otsu automático, tintado con color elegible y fondo transparente/blanco.
 */
class SignatureProcessor @Inject constructor() {

    enum class Background { TRANSPARENT, WHITE }

    fun crop(src: Bitmap, box: SignatureBox): Bitmap {
        val x = (box.x / 100f * src.width).toInt().coerceIn(0, src.width - 1)
        val y = (box.y / 100f * src.height).toInt().coerceIn(0, src.height - 1)
        val w = (box.w / 100f * src.width).toInt().coerceIn(1, src.width - x)
        val h = (box.h / 100f * src.height).toInt().coerceIn(1, src.height - y)
        return Bitmap.createBitmap(src, x, y, w, h)
    }

    /** Umbral Otsu automático (fiel a otsuThreshold), acotado a [60, 230]. */
    fun otsuThreshold(px: IntArray): Int {
        val hist = IntArray(256)
        val n = px.size
        for (c in px) {
            val lum = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)).roundToInt()
            hist[lum.coerceIn(0, 255)]++
        }
        var sum = 0.0
        for (i in 0 until 256) sum += i * hist[i]
        var sumB = 0.0; var wB = 0; var maxVar = 0.0; var thr = 170
        for (t in 0 until 256) {
            wB += hist[t]; if (wB == 0) continue
            val wF = n - wB; if (wF == 0) break
            sumB += t * hist[t]
            val mB = sumB / wB; val mF = (sum - sumB) / wF
            val v = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (v > maxVar) { maxVar = v; thr = t }
        }
        return min(230, max(60, thr))
    }

    /** Aplana la iluminación desigual (fiel a flattenIllumination: divide por fondo difuso). */
    fun flattenIllumination(src: Bitmap): Bitmap {
        val w = src.width; val h = src.height
        val smallW = max(1, (w / 40.0).roundToInt())
        val smallH = max(1, (h / 40.0).roundToInt())
        // fondo difuso = reducir y volver a ampliar (blur por escalado)
        val bg = src.scale(smallW, smallH).scale(w, h)
        val srcPx = IntArray(w * h); src.getPixels(srcPx, 0, w, 0, 0, w, h)
        val bgPx = IntArray(w * h); bg.getPixels(bgPx, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        for (i in srcPx.indices) {
            val s = srcPx[i]; val b = bgPx[i]
            fun norm(sc: Int, bc: Int): Int {
                val bb = max(40, bc)
                return min(255, (sc * 255.0 / bb).roundToInt())
            }
            out[i] = Color.argb(255,
                norm(Color.red(s), Color.red(b)),
                norm(Color.green(s), Color.green(b)),
                norm(Color.blue(s), Color.blue(b)))
        }
        bg.recycle()
        return createBitmap(w, h).apply { setPixels(out, 0, w, 0, 0, w, h) }
    }

    /**
     * Tintado con alpha graduado + recorte a bounding box (fiel a processInk).
     * @return el bitmap de firma listo, o null si no hay trazo.
     */
    fun processInk(src: Bitmap, threshold: Int, tint: Int, bg: Background): Bitmap? {
        val w = src.width; val h = src.height
        val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
        val tr = Color.red(tint); val tg = Color.green(tint); val tb = Color.blue(tint)
        var minX = w; var minY = h; var maxX = 0; var maxY = 0; var found = false
        for (i in px.indices) {
            val c = px[i]
            val lum = 0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)
            if (lum > threshold || Color.alpha(c) < 40) {
                px[i] = Color.TRANSPARENT
            } else {
                val a = min(255, ((threshold - lum) / max(30.0, threshold * 0.35) * 255).roundToInt() + 90)
                px[i] = Color.argb(a, tr, tg, tb)
                found = true
                val pxi = i % w; val pyi = i / w
                if (pxi < minX) minX = pxi; if (pxi > maxX) maxX = pxi
                if (pyi < minY) minY = pyi; if (pyi > maxY) maxY = pyi
            }
        }
        if (!found) return null
        val pad = 6
        minX = max(0, minX - pad); minY = max(0, minY - pad)
        maxX = min(w - 1, maxX + pad); maxY = min(h - 1, maxY + pad)
        val cw = maxX - minX + 1; val ch = maxY - minY + 1

        val full = createBitmap(w, h).apply { setPixels(px, 0, w, 0, 0, w, h) }
        val cropped = Bitmap.createBitmap(full, minX, minY, cw, ch)
        full.recycle()
        if (bg == Background.WHITE) {
            val white = createBitmap(cw, ch)
            white.eraseColor(Color.WHITE)
            val canvas = android.graphics.Canvas(white)
            canvas.drawBitmap(cropped, 0f, 0f, null)
            cropped.recycle()
            return white
        }
        return cropped
    }

    /** Pipeline completo desde una foto: aplanar → Otsu → tintar. */
    fun fromPhoto(src: Bitmap, tint: Int = Color.rgb(20, 30, 90), bg: Background = Background.TRANSPARENT): Bitmap? {
        val flat = flattenIllumination(src)
        val flatPx = IntArray(flat.width * flat.height)
        flat.getPixels(flatPx, 0, flat.width, 0, 0, flat.width, flat.height)
        val thr = otsuThreshold(flatPx)
        val result = processInk(flat, thr, tint, bg)
        flat.recycle()
        return result
    }

    fun toSignatureData(bmp: Bitmap): SignatureData {
        // Upscale para evitar pixelado al estampar en el PDF a alta DPI
        val targetSide = 1500
        val maxSide = maxOf(bmp.width, bmp.height)
        val scaled = if (maxSide in 1 until targetSide) {
            val ratio = targetSide.toFloat() / maxSide
            Bitmap.createScaledBitmap(bmp, (bmp.width * ratio).toInt(), (bmp.height * ratio).toInt(), true)
        } else bmp
        val png = ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val ar = if (scaled.width > 0) scaled.height.toFloat() / scaled.width.toFloat() else 0.4f
        if (scaled !== bmp) scaled.recycle()
        return SignatureData(png, ar)
}
