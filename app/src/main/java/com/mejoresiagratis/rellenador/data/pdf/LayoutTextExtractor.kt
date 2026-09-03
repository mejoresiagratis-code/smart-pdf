package com.mejoresiagratis.rellenador.data.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.InputStream
import javax.inject.Inject

/**
 * Texto del PDF con posición y tamaño de fuente, palabra a palabra. Pieza nueva de la
 * tanda 5·4b (`docs/PLAN_ETIQUETADO_ORGANICO.md` §3): sin esto, `FormSchemaBuilder` sólo tiene
 * los nombres de campo del AcroForm, que en el 52% de los widgets de `Contrato_empresas.pdf`
 * son autogenerados (`Campo de texto 116`) y no dicen nada.
 *
 * ── Verificado contra el fuente real de `pdfbox-android 2.0.27.0` antes de escribir esto ──
 * (regla de `CONTINUIDAD.md` §6: dos builds se rompieron por métodos inventados de memoria).
 *
 * - `PDFTextStripper.writeString(String, List<TextPosition>)` existe tal cual y se invoca **una
 *   vez por palabra** (`writeLine()` llama `writeString(word.getText(), word.getTextPositions())`
 *   por cada `WordSeparator`), así que sobrescribirlo da directamente la granularidad de
 *   palabra que hace falta para acotar una etiqueta por el borde de un campo — no hay que
 *   trocear una línea completa a mano.
 * - `TextPosition.getX()`/`getY()` son **origen arriba-izquierda**, adjuntando la nota exacta
 *   del fuente: «adjusted based on page rotation so that the upper left is 0,0, which is unlike
 *   PDF coordinates, which start at the bottom left». Es la MISMA convención que ya usa
 *   `PdfFieldInspector.Field` (`y = pageHeight - rect.upperRightY`), así que un [Word] y un
 *   [PdfFieldInspector.Field] se comparan sin convertir nada — a propósito, para no repetir el
 *   error de mezclar sistemas de coordenadas sin darse cuenta.
 * - `TextPosition.getDir()` da la rotación del propio glifo (0/90/180/270), independiente de la
 *   rotación de página. Se usa para **excluir el texto rotado del margen** (regla 4 del §5 del
 *   plan): el pie legal vertical de "Aire Networks del Mediterráneo, S.L.U. CIF…" de las
 *   páginas 2 y 3. Comprobado en la práctica al preparar esta tanda: sin este filtro, el texto
 *   rotado se mete en la agrupación por fila y contamina líneas que no tienen nada que ver.
 * - `getFontSizeInPt()` da el tamaño en puntos, no en unidades de la matriz de texto —
 *   necesario para el umbral de "≥ 8 pt" de las anclas del §3.1, que si se comparara contra
 *   `getFontSize()` (tamaño sin escalar) daría un número sin relación con los puntos del PDF.
 *
 * No se usa `setSortByPosition(true)` por línea completa ni se reconstruyen párrafos: cada
 * llamada a [extract] procesa una página con `startPage`/`endPage` fijados a esa misma página,
 * así que ya no hace falta reordenar entre páginas — el índice 0-based de [Word.page] es el que
 * espera `PdfFieldInspector.Field.page`.
 */
class LayoutTextExtractor @Inject constructor() {

    /** Una palabra del PDF, con su caja delimitadora en la misma convención que [PdfFieldInspector.Field]. */
    data class Word(
        val page: Int,       // 0-indexed, igual que PdfFieldInspector.Field.page
        val text: String,
        val x: Float,        // borde izquierdo, origen arriba-izquierda
        val y: Float,        // borde superior del primer carácter, origen arriba-izquierda
        val endX: Float,     // borde derecho
        val fontSize: Float, // en puntos (getFontSizeInPt)
    )

    fun extract(input: InputStream): List<Word> = runCatching {
        PDDocument.load(input).use { doc ->
            val words = mutableListOf<Word>()
            for (pageIndex in 0 until doc.numberOfPages) {
                val pageWords = mutableListOf<Word>()
                val stripper = object : PDFTextStripper() {
                    override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                        // Sólo texto horizontal: el rotado (pie legal de márgenes) se descarta
                        // aquí, no en quien consuma [Word] — así ningún llamador tiene que
                        // acordarse de filtrarlo.
                        val upright = textPositions.filter { it.dir == 0f }
                        if (upright.isEmpty()) return
                        val first = upright.first()
                        val last = upright.last()
                        pageWords += Word(
                            page = pageIndex,
                            text = upright.joinToString("") { it.unicode },
                            x = first.x,
                            y = first.y,
                            endX = last.x + last.width,
                            fontSize = first.fontSizeInPt,
                        )
                    }
                }
                stripper.sortByPosition = true
                stripper.startPage = pageIndex + 1  // 1-indexed en la API de PDFTextStripper
                stripper.endPage = pageIndex + 1
                stripper.getText(doc)
                words += pageWords
            }
            words
        }
    }.getOrDefault(emptyList())
}
