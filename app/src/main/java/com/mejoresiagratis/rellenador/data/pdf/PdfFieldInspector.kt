package com.mejoresiagratis.rellenador.data.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import java.io.InputStream
import javax.inject.Inject

/**
 * Lee los campos de un AcroForm **en el orden en que se rellenan**: por página, y dentro
 * de cada página de arriba abajo y de izquierda a derecha (fase 1 del roadmap
 * multi-formulario).
 *
 * ── Por qué hace falta ──
 * `AcroFormFiller.listFields()` devuelve solo los nombres, en el orden interno del PDF,
 * que no tiene por qué parecerse al visual. Para que la pantalla de mapeo se lea como el
 * formulario impreso —y para que la IA pueda relacionar cada campo con el rótulo que tiene
 * al lado— hace falta la posición.
 *
 * El eje Y de PDF crece hacia ARRIBA; aquí se invierte para poder ordenar de arriba abajo
 * como se lee. La tolerancia de fila evita que dos campos de la misma línea se ordenen mal
 * por unos pocos puntos de diferencia vertical: pasa constantemente en los formularios de
 * la AEAT, donde las casillas de una misma fila no están perfectamente alineadas.
 *
 * VERIFICADO sobre `Modelo_145_rellenable.pdf` (60 campos): el orden resultante coincide
 * con el del formulario impreso, sección por sección.
 */
class PdfFieldInspector @Inject constructor() {

    data class Field(
        val name: String,
        val page: Int,          // 0-indexed
        /** Coordenadas con origen ARRIBA-izquierda, en puntos. */
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val isCheckbox: Boolean,
    )

    fun inspect(input: InputStream): List<Field> = runCatching {
        PDDocument.load(input).use { doc ->
            val form = doc.documentCatalog.acroForm ?: return emptyList()
            val pages = doc.pages.toList()
            val out = mutableListOf<Field>()

            for (field in form.fieldTree) {
                val isCb = field is PDCheckBox
                val name = field.fullyQualifiedName ?: continue
                for (widget in field.widgets) {
                    val rect = widget.rectangle ?: continue
                    val pageIdx = pages.indexOfFirst { it == widget.page }
                        .takeIf { it >= 0 } ?: continue
                    val pageHeight = pages[pageIdx].mediaBox.height
                    out += Field(
                        name = name,
                        page = pageIdx,
                        x = rect.lowerLeftX,
                        y = pageHeight - rect.upperRightY,  // 0 = borde superior
                        width = rect.width,
                        height = rect.height,
                        isCheckbox = isCb,
                    )
                }
            }

            out.groupBy { it.page }
                .toSortedMap()
                .flatMap { (_, fieldsOfPage) -> orderByReadingRows(fieldsOfPage) }
        }
    }.getOrDefault(emptyList())

    /**
     * Ordena los campos de UNA página en orden de lectura: filas de arriba abajo y, dentro de
     * cada fila, de izquierda a derecha.
     *
     * Las filas se forman **agrupando por el hueco vertical entre campos consecutivos**, no
     * troceando el eje Y en tramos fijos. La diferencia importa: con tramos fijos
     * (`(y / TOL).toInt()`), dos campos separados por una décima de punto caen en tramos
     * distintos si el corte del tramo pasa justo entre ellos, y la fila se parte.
     *
     * No es teórico — pasaba con el SEPA de Aire, en la fila de 11 casillas del BIC: todo el
     * grupo estaba a y≈539,x salvo dos campos a y=540,0, y como 539/6=89 pero 540/6=90, esas
     * dos casillas se iban al final de la fila siguiente. El orden de lectura salía
     * `…18, 19, 22, 23 … 28` y luego `20, 29`, con un span vertical real de 1,1 pt entre
     * todas ellas (muy por debajo de la tolerancia de 6 pt, que era justo lo que debía
     * haberlas mantenido juntas).
     *
     * Aquí el criterio es el que se pretendía desde el principio: un campo entra en la fila en
     * curso si está a menos de [ROW_TOLERANCE] del ANCLA de esa fila (el primer campo, el más
     * alto). Se compara contra el ancla y no contra el campo anterior para que una escalera de
     * campos con saltos pequeños no acabe fundiendo media página en una sola fila.
     */
    private fun orderByReadingRows(fieldsOfPage: List<Field>): List<Field> {
        val byTop = fieldsOfPage.sortedBy { it.y }
        val ordered = mutableListOf<Field>()
        var row = mutableListOf<Field>()
        var anchorY = Float.NaN

        for (f in byTop) {
            if (row.isEmpty() || f.y - anchorY <= ROW_TOLERANCE) {
                if (row.isEmpty()) anchorY = f.y
                row += f
            } else {
                ordered += row.sortedBy { it.x }
                row = mutableListOf(f)
                anchorY = f.y
            }
        }
        if (row.isNotEmpty()) ordered += row.sortedBy { it.x }
        return ordered
    }

    private companion object {
        /** Campos de la misma fila visual se consideran alineados dentro de este margen. */
        const val ROW_TOLERANCE = 6f
    }
}
