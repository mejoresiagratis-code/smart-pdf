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

            out.sortedWith(
                compareBy<Field> { it.page }
                    .thenBy { (it.y / ROW_TOLERANCE).toInt() }
                    .thenBy { it.x }
            )
        }
    }.getOrDefault(emptyList())

    private companion object {
        /** Campos de la misma fila visual se consideran alineados dentro de este margen. */
        const val ROW_TOLERANCE = 6f
    }
}
