package com.mejoresiagratis.rellenador.data.pdf

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import com.mejoresiagratis.rellenador.data.model.SignatureBox
import com.mejoresiagratis.rellenador.data.model.SignatureData
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Convierte un bitmap de firma (trazo dibujado o foto) en un PNG con fondo
 * transparente listo para estampar. Pipeline simplificado del de la web
 * (recorte a la caja + umbralización a trazo sobre transparente).
 */
class SignatureProcessor @Inject constructor() {

    fun crop(src: Bitmap, box: SignatureBox): Bitmap {
        val x = (box.x / 100f * src.width).toInt().coerceIn(0, src.width - 1)
        val y = (box.y / 100f * src.height).toInt().coerceIn(0, src.height - 1)
        val w = (box.w / 100f * src.width).toInt().coerceIn(1, src.width - x)
        val h = (box.h / 100f * src.height).toInt().coerceIn(1, src.height - y)
        return Bitmap.createBitmap(src, x, y, w, h)
    }

    /**
     * Trazo oscuro sobre transparente. Píxeles con luminancia < threshold se
     * conservan (negro sólido); el resto → transparente. Usa arrays por rendimiento.
     */
    fun toTransparentStroke(src: Bitmap, threshold: Int = 150): Bitmap {
        val w = src.width; val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val ink = Color.argb(255, 20, 20, 20)
        for (i in pixels.indices) {
            val c = pixels[i]
            val lum = (Color.red(c) * 0.299 + Color.green(c) * 0.587 + Color.blue(c) * 0.114).toInt()
            pixels[i] = if (lum < threshold) ink else Color.TRANSPARENT
        }
        val out = createBitmap(w, h)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    fun toSignatureData(bmp: Bitmap): SignatureData {
        val png = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val ar = if (bmp.width > 0) bmp.height.toFloat() / bmp.width.toFloat() else 0.4f
        return SignatureData(png, ar)
    }
}
