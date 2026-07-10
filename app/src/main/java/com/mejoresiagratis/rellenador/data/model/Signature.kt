package com.mejoresiagratis.rellenador.data.model

/**
 * Una colocación de firma sobre una página del PDF, en coordenadas RELATIVAS
 * (0..1) respecto al ancho/alto de la página. Igual que los "stamps" de la web.
 * x,y = centro de la firma; widthRel = ancho relativo (alto se deriva del aspect ratio).
 */
data class SignatureStamp(
    val pageIndex: Int,          // 0-indexed
    val xRel: Float,             // 0..1 centro X
    val yRel: Float,             // 0..1 centro Y (0 = arriba)
    val widthRel: Float = 0.28f, // ancho MÁXIMO relativo al ancho de página (caja disponible)
    val heightRel: Float = 0.114f // alto MÁXIMO relativo al alto de página (caja disponible)
)

/** La firma preparada: PNG en bytes + relación de aspecto (alto/ancho). */
data class SignatureData(
    val pngBytes: ByteArray,
    val aspectRatio: Float = 0.4f   // alto / ancho
) {
    override fun equals(other: Any?) =
        other is SignatureData && pngBytes.contentEquals(other.pngBytes) && aspectRatio == other.aspectRatio
    override fun hashCode() = pngBytes.contentHashCode() * 31 + aspectRatio.hashCode()
}

/** Caja devuelta por locate_signature (porcentajes 0..100 sobre la imagen). */
@kotlinx.serialization.Serializable
data class SignatureBox(
    val x: Float, val y: Float, val w: Float, val h: Float
) {
    val valid get() = w > 2f && h > 2f
}
