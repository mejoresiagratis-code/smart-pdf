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

    /**
     * Añade un margen blanco SINTÉTICO alrededor de la imagen antes de aplanar
     * iluminación. Cuando el recorte (manual o "foto completa") queda muy ajustado a
     * la firma, casi sin papel limpio alrededor, `flattenIllumination()` estima el
     * fondo reduciendo y ampliando la imagen entera — con poco margen real, esa
     * estimación queda contaminada por la propia tinta cerca de los bordes, y los
     * trazos más finos (p.ej. la parte superior de una letra, con menos presión de
     * bolígrafo) acaban justo por debajo del umbral y se pierden ("corte por arriba").
     * Añadir un margen blanco de mentira le da a esa estimación zonas de fondo fiables
     * cerca de cada borde. El recorte final a bounding-box de `processInk()` vuelve a
     * ajustar el resultado al trazo real, así que esto no dejar el resultado con
     * bordes de más — solo mejora la calibración del paso intermedio.
     */
    private fun padWithWhiteMargin(src: Bitmap, marginRatio: Float = 0.25f): Bitmap {
        val marginX = (src.width * marginRatio).roundToInt().coerceAtLeast(12)
        val marginY = (src.height * marginRatio).roundToInt().coerceAtLeast(12)
        val w = src.width + marginX * 2
        val h = src.height + marginY * 2
        val out = createBitmap(w, h)
        out.eraseColor(Color.WHITE)
        android.graphics.Canvas(out).drawBitmap(src, marginX.toFloat(), marginY.toFloat(), null)
        return out
    }

    /**
     * Elimina motas de ruido aisladas (textura del papel, grano de la foto, sombras
     * puntuales) que pasan el umbral de tinta pero no forman parte del trazo real.
     * Etiqueta componentes conexas (8-conectividad, para no partir trazos cursivos en
     * diagonal) sobre la máscara de "es tinta" y descarta las que tengan menos de
     * `minPixels` — el trazo real de una firma es, con mucha diferencia, la componente
     * más grande; una mota de ruido son unos pocos píxeles sueltos.
     */
    private fun despeckle(isInk: BooleanArray, w: Int, h: Int, minPixels: Int = 12): BooleanArray {
        val visited = BooleanArray(w * h)
        val keep = BooleanArray(w * h)
        val stack = ArrayDeque<Int>()
        val component = ArrayList<Int>()
        for (start in 0 until w * h) {
            if (!isInk[start] || visited[start]) continue
            component.clear()
            stack.addLast(start)
            visited[start] = true
            while (stack.isNotEmpty()) {
                val i = stack.removeLast()
                component.add(i)
                val x = i % w; val y = i / w
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx; val ny = y + dy
                    if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue
                    val ni = ny * w + nx
                    if (isInk[ni] && !visited[ni]) { visited[ni] = true; stack.addLast(ni) }
                }
            }
            if (component.size >= minPixels) {
                for (i in component) keep[i] = true
            }
        }
        return keep
    }

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
     * Tintado con alpha graduado + recorte a bounding box opcional (fiel a processInk).
     * @param applyBoundingCrop si false, no recorta al bounding box del trazo — úsalo
     *   cuando la imagen YA viene recortada de forma fiable (p.ej. por SignatureLocator),
     *   porque recortar dos veces sobre una foto completa desviaba el resultado a una
     *   esquina cuando cualquier píxel oscuro (sombra, arruga) se colaba como "trazo".
     * @return el bitmap de firma listo, o null si no hay trazo.
     */
    fun processInk(src: Bitmap, threshold: Int, tint: Int, bg: Background, applyBoundingCrop: Boolean = true): Bitmap? {
        val w = src.width; val h = src.height
        val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
        val tr = Color.red(tint); val tg = Color.green(tint); val tb = Color.blue(tint)

        // Primera pasada: máscara de "es tinta" + luminancia guardada para el alpha
        // graduado posterior. Separado del despeckle para no confundir "no es tinta
        // por umbral" con "es tinta pero se descarta por ser una mota aislada".
        val isInk = BooleanArray(w * h)
        val lums = DoubleArray(w * h)
        for (i in px.indices) {
            val c = px[i]
            val lum = 0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)
            lums[i] = lum
            // Umbral relajado ×1.15 (fix de otra sesión, restaurado): sin esto, trazos
            // ligeramente más claros o finos (bordes antialiaseados de la tinta) se
            // descartaban como fondo, dejando solo el núcleo más oscuro del trazo —
            // una firma real podía quedar reducida a un fragmento irreconocible.
            isInk[i] = !(lum > threshold * 1.15 || Color.alpha(c) < 40)
        }
        // Motas de ruido aisladas (textura de papel, grano) fuera — el trazo real es,
        // con diferencia, la(s) componente(s) grande(s); no se recorta nada del trazo.
        val keep = despeckle(isInk, w, h)

        var minX = w; var minY = h; var maxX = 0; var maxY = 0; var found = false
        for (i in px.indices) {
            if (!keep[i]) {
                px[i] = Color.TRANSPARENT
            } else {
                val lum = lums[i]
                val a = min(255, ((threshold - lum) / max(30.0, threshold * 0.35) * 255).roundToInt() + 90)
                px[i] = Color.argb(a, tr, tg, tb)
                found = true
                val pxi = i % w; val pyi = i / w
                if (pxi < minX) minX = pxi; if (pxi > maxX) maxX = pxi
                if (pyi < minY) minY = pyi; if (pyi > maxY) maxY = pyi
            }
        }
        if (!found) return null

        val full = createBitmap(w, h).apply { setPixels(px, 0, w, 0, 0, w, h) }
        val cropped = if (applyBoundingCrop) {
            val pad = 6
            minX = max(0, minX - pad); minY = max(0, minY - pad)
            maxX = min(w - 1, maxX + pad); maxY = min(h - 1, maxY + pad)
            val cw = maxX - minX + 1; val ch = maxY - minY + 1
            val c = Bitmap.createBitmap(full, minX, minY, cw, ch)
            full.recycle()
            c
        } else full
        val cw = cropped.width; val ch = cropped.height
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

    /** Pipeline completo desde una foto: aplanar → Otsu → tintar.
     *  @param applyBoundingCrop ver processInk() — desactívalo si la imagen ya viene
     *    recortada de forma fiable (p.ej. por SignatureLocator). */
    fun fromPhoto(
        src: Bitmap,
        tint: Int = Color.rgb(20, 30, 90),
        bg: Background = Background.TRANSPARENT,
        applyBoundingCrop: Boolean = true
    ): Bitmap? {
        val padded = padWithWhiteMargin(src)
        val flat = flattenIllumination(padded)
        padded.recycle()
        val flatPx = IntArray(flat.width * flat.height)
        flat.getPixels(flatPx, 0, flat.width, 0, 0, flat.width, flat.height)
        val thr = otsuThreshold(flatPx)
        val result = processInk(flat, thr, tint, bg, applyBoundingCrop)
        flat.recycle()
        return result
    }

    fun toSignatureData(bmp: Bitmap): SignatureData {
        val png = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val ar = if (bmp.width > 0) bmp.height.toFloat() / bmp.width.toFloat() else 0.4f
        return SignatureData(png, ar)
    }
}
