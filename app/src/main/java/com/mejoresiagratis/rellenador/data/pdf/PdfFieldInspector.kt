package com.mejoresiagratis.rellenador.data.pdf

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDSignatureField
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
        /**
         * `true` si el campo es un grupo de opción (radio): varias casillas comparten el mismo
         * [name] (es el mecanismo nativo del AcroForm para agrupar radios) y se distinguen por
         * [onState]. Verificado contra `PDButton.isRadioButton()` en el fuente real de
         * `pdfbox-android 2.0.27.0` — no se asume `is PDRadioButton`, porque el flag vive en la
         * clase base y es la forma verificada de comprobarlo (ver nota de `AcroFormFiller` sobre
         * `PDRadioButton.getSelectableValues()`, que no existe en esta versión).
         */
        val isRadio: Boolean = false,
        /**
         * `true` si el campo es `/Sig` del AcroForm — un hueco de firma electrónica, no un
         * campo de texto. Tanda 5·4b (`docs/PLAN_ETIQUETADO_ORGANICO.md` §5, regla 2):
         * `FormSchemaBuilder.toField()` los mapeaba a `FieldKind.TEXT` por el `else` de su
         * `when`, y el usuario podía escribir dentro de un hueco de firma. Verificado con
         * `PDSignatureField` en el fuente real: hereda de `PDTerminalField`, igual que
         * `PDButton`/`PDCheckBox`, así que se detecta con el mismo `is` en `fieldTree`.
         */
        val isSignature: Boolean = false,
        /**
         * Valor de activación de ESTE widget concreto (no del campo entero): la clave de
         * `/AP /N` que no es `Off`. Para un grupo de opción, cada casilla física tiene un
         * `onState` distinto (`PAGO_UNICO`, `FINANCIADO`…) aunque compartan [name]. Nulo para
         * TEXT o si el widget no declara estados (PDF mal formado).
         */
        val onState: String? = null,
    )

    /**
     * Estado de activación propio de un widget de botón (checkbox o radio), leído de su propio
     * diccionario de apariencia `/AP /N`. Es el mismo mecanismo que ya usa `AcroFormFiller`
     * para ESCRIBIR el estado correcto (v0.9.7); aquí se usa para LEER cuál es, widget a widget,
     * cuando varios comparten `name` (radio). Verificado contra el fuente real: `PDAnnotation`
     * expone `getAppearance()` → `PDAppearanceDictionary.getNormalAppearance()` →
     * `PDAppearanceEntry`, y si `isSubDictionary()` sus claves son los estados posibles de ESE
     * widget (incluyendo `Off`).
     */
    private fun widgetOnState(widget: PDAnnotationWidget): String? =
        runCatching {
            widget.appearance?.normalAppearance
                ?.takeIf { it.isSubDictionary }
                ?.subDictionary
                ?.keys
                ?.map { it.name }
                ?.firstOrNull { it != COSName.Off.name }
        }.getOrNull()

    fun inspect(input: InputStream): List<Field> = runCatching {
        PDDocument.load(input).use { doc ->
            val form = doc.documentCatalog.acroForm ?: return emptyList()
            val pages = doc.pages.toList()
            val out = mutableListOf<Field>()

            for (field in form.fieldTree) {
                val isCb = field is PDCheckBox
                // PDCheckBox hereda de PDButton, así que isCb va primero (mismo orden que
                // AcroFormFiller.applyButtonValue): un radio nunca es también checkbox.
                val isRadioField = !isCb && (field as? PDButton)?.let { btn ->
                    runCatching { btn.isRadioButton }.getOrDefault(false)
                } == true
                // Tanda 5·4b — regla de higiene 1 del plan: los pulsadores (`Ff` bit 17, los
                // enlaces «descargar aquí») no tienen valor y se excluyen del esquema entero,
                // no sólo se marcan. Verificado contra `PDButton.isPushButton()` en el fuente
                // real de pdfbox-android 2.0.27.0 (`FLAG_PUSHBUTTON = 1 shl 16`, o sea el
                // bit 17 en numeración de 1). `Botón 2/3/4` de `Contrato_empresas.pdf`.
                val isPushbutton = !isCb && !isRadioField && (field as? PDButton)?.let { btn ->
                    runCatching { btn.isPushButton }.getOrDefault(false)
                } == true
                if (isPushbutton) continue
                val isSignatureField = field is PDSignatureField
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
                        isRadio = isRadioField,
                        isSignature = isSignatureField,
                        onState = if (isCb || isRadioField) widgetOnState(widget) else null,
                    )
                }
            }

            out.groupBy { it.page }
                .toSortedMap()
                .flatMap { (_, fieldsOfPage) -> orderByReadingRows(fieldsOfPage) }
        }
    }.getOrDefault(emptyList())

    /**
     * Número de páginas del documento.
     *
     * Va aparte de [inspect] a propósito: `TemplateFingerprint.of()` necesita el total de
     * páginas del PDF, no el de páginas *que tienen campos*, que es lo único deducible de
     * [inspect]. Un contrato de 54 páginas con campos en 6 daría una huella distinta según cómo
     * se calculara, y entonces el esquema guardado no se reencontraría nunca al volver a subir
     * el mismo PDF — que es justo para lo que sirve la huella.
     *
     * Devuelve 0 si el documento no se puede abrir, mismo criterio que [inspect] (que devuelve
     * lista vacía): quien llama ya tiene que tratar el caso de "este PDF no da nada".
     */
    fun pageCount(input: InputStream): Int = runCatching {
        PDDocument.load(input).use { it.numberOfPages }
    }.getOrDefault(0)

    /**
     * Ordena los campos de UNA página en orden de lectura: filas de arriba abajo y, dentro de
     * cada fila, de izquierda a derecha.
     *
     * Tanda 5·4k — el criterio vive ahora en
     * [com.mejoresiagratis.rellenador.data.model.ReadingOrder], que es Kotlin puro y por tanto
     * typecheckeable y comprobable en local. Aquí sólo queda la llamada.
     *
     * El criterio no cambia: las filas se forman agrupando por el hueco vertical respecto al
     * **ancla** de la fila (el campo más alto), y no troceando el eje Y en tramos fijos. La
     * diferencia importa, y no es teórica: con tramos fijos (`(y / TOL).toInt()`) dos campos
     * separados por una décima de punto caen en tramos distintos si el corte pasa justo entre
     * ellos, y la fila se parte. Pasaba con el SEPA de Aire, en la fila de 11 casillas del BIC:
     * todo el grupo estaba a y≈539 salvo dos campos a y=540,0, y como 539/6=89 pero 540/6=90,
     * esas dos casillas se iban al final de la fila siguiente. Ese mismo troceado se coló otra
     * vez en `VisionLabelPass` en la 0.10.26 — de ahí que ahora haya una sola implementación.
     */
    private fun orderByReadingRows(fieldsOfPage: List<Field>): List<Field> =
        com.mejoresiagratis.rellenador.data.model.ReadingOrder.sorted(
            fieldsOfPage,
            tolerancePt = ROW_TOLERANCE,
            y = { it.y },
            x = { it.x },
        )

    private companion object {
        /** Campos de la misma fila visual se consideran alineados dentro de este margen. */
        const val ROW_TOLERANCE = 6f
    }
}
