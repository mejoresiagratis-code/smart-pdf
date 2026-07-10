package com.mejoresiagratis.rellenador.data.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.InputStream
import javax.inject.Inject

/**
 * Detecta las páginas con hueco de firma del DISTRIBUIDOR, replicando
 * buildSignPages() de la web y VERIFICADO contra el contrato real:
 * huecos = páginas 30, 33, 45, 54 (+ 24) — NO solo la 24 fija.
 *
 * Algoritmo (fiel a la web):
 *  1. Campos que se repiten en varias páginas (widgets multipágina) → los huecos
 *     de firma tienen su bloque Fecha/de/año repetido. Excluir la portada (pág 0).
 *  2. Cruzar con la presencia del rótulo "EL DISTRIBUIDOR" en el texto de la página
 *     (señal fuerte), y su posición Y para anclar la firma justo debajo.
 *  3. Si el texto no es fiable, caer a "todas las multipágina salvo portada".
 * El usuario SIEMPRE puede añadir/quitar páginas (los tokenizadores varían).
 */
class SignaturePageDetector @Inject constructor() {

    data class Detection(
        val signPages: List<Int>,                 // índices 0-based
        val anchors: Map<Int, Float>,             // página -> yr del rótulo (0 arriba)
        val multipageFields: List<String>         // campos multipágina detectados
    )

    /** Y relativa (0 arriba…1 abajo) del rótulo DISTRIBUIDOR por página. */
    private class DistribLocator(doc: PDDocument) : PDFTextStripper() {
        val hitYrByPage = HashMap<Int, Float>()
        private var pageHeight = 842f
        init { sortByPosition = true }

        override fun startPage(page: com.tom_roush.pdfbox.pdmodel.PDPage) {
            pageHeight = page.mediaBox.height
            super.startPage(page)
        }
        override fun writeString(text: String, textPositions: List<TextPosition>) {
            if (text.uppercase().contains("DISTRIBUIDOR") &&
                !text.uppercase().contains("DISTRIBUIDORES")) {
                val tp = textPositions.firstOrNull()
                if (tp != null) {
                    // getY() en pdfbox = distancia desde arriba → yr directo
                    val yr = (tp.y / pageHeight).coerceIn(0f, 1f)
                    val pageIdx = currentPageNo - 1   // currentPageNo es 1-based
                    val prev = hitYrByPage[pageIdx]
                    if (prev == null || yr < prev) hitYrByPage[pageIdx] = yr
                }
            }
            super.writeString(text, textPositions)
        }
    }

    fun detect(template: InputStream): Detection {
        PDDocument.load(template).use { doc ->
            val total = doc.numberOfPages

            // 1. Widgets multipágina (campos repetidos en >1 página).
            val fieldPages = HashMap<String, MutableSet<Int>>()
            for (i in 0 until total) {
                val annots = runCatching { doc.getPage(i).annotations }.getOrNull() ?: continue
                for (an in annots) {
                    val dict = an.cosObject
                    val subtype = dict.getNameAsString(com.tom_roush.pdfbox.cos.COSName.SUBTYPE)
                    if (subtype == "Widget") {
                        var name = dict.getString(com.tom_roush.pdfbox.cos.COSName.T)
                        if (name == null) {
                            val parent = dict.getDictionaryObject(com.tom_roush.pdfbox.cos.COSName.PARENT)
                            if (parent is com.tom_roush.pdfbox.cos.COSDictionary)
                                name = parent.getString(com.tom_roush.pdfbox.cos.COSName.T)
                        }
                        if (name != null) fieldPages.getOrPut(name) { mutableSetOf() }.add(i)
                    }
                }
            }
            val multipageFields = fieldPages.filter { it.value.size > 1 }.keys.toList()
            val multipagePages = fieldPages.values.filter { it.size > 1 }.flatten().toSet()
            val candidates = (multipagePages - 0).sorted()   // excluir portada

            // 2. Localizar "EL DISTRIBUIDOR" y su Y por página.
            val locator = DistribLocator(doc)
            locator.getText(doc)   // recorre todas las páginas
            val anchors = locator.hitYrByPage

            // 3. Señal fuerte: candidata + rótulo presente. Fallback: todas las candidatas.
            val strong = candidates.filter { anchors.containsKey(it) }
            val signPages = if (strong.isNotEmpty()) strong else candidates

            return Detection(signPages, anchors, multipageFields)
        }
    }
}
