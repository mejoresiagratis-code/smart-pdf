package com.mejoresiagratis.rellenador.data.pdf

import com.mejoresiagratis.rellenador.data.model.ContractFields
import com.mejoresiagratis.rellenador.data.model.SignatureData
import com.mejoresiagratis.rellenador.data.model.SignatureStamp
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

/**
 * Rellena el AcroForm de 54 páginas de MASORANGE y estampa la firma.
 *
 * CRÍTICO (fiel a la web):
 *  - nombres de campo exactos, dobles espacios incluidos.
 *  - autofill "Responsable Comercial MASORANGE" = "PABLO SALVADOR POVEDA".
 *  - la página 24 (índice 23) NO tiene campos AcroForm: es el hueco de firma del
 *    DISTRIBUIDOR, insertado por heurística (presencia del campo Responsable).
 */
class AcroFormFiller @Inject constructor() {

    data class FillResult(
        val filledCount: Int,
        val missingFields: List<String>,
        val signatureGapPage: Int?          // índice de página del hueco de firma, o null
    )

    /** Índice 0-based de la página de firma del distribuidor (pág. 24). */
    val distributorSignaturePageIndex = 23

    fun listFields(template: InputStream): List<String> {
        PDDocument.load(template).use { doc ->
            val form = doc.documentCatalog.acroForm ?: return emptyList()
            return form.fields.flatMap { collectNames(it) }
        }
    }

    /**
     * Genera el PDF final: rellena campos, estampa firmas y guarda en `output`.
     * @param stamps colocaciones de la firma (si signature != null).
     */
    fun generate(
        template: InputStream,
        values: Map<String, String>,
        signature: SignatureData? = null,
        stamps: List<SignatureStamp> = emptyList(),
        output: OutputStream,
        flatten: Boolean = false,
        checkboxes: Map<String, String> = emptyMap(),
        /** Traducción clave canónica -> nombre real del campo (para PDF del usuario).
         *  Si está vacío, se usan las claves tal cual (contrato por defecto). */
        fieldMapping: Map<String, String> = emptyMap()
    ): FillResult {
        PDDocument.load(template).use { doc ->
            val form = doc.documentCatalog.acroForm
            val missing = mutableListOf<String>()
            var filled = 0
            var gapPage: Int? = null

            if (form != null) {
                form.needAppearances = true
                val effective = values.toMutableMap()
                fun realName(canonical: String) = fieldMapping[canonical] ?: canonical

                // Autorrelleno del responsable comercial (regla heredada de la app web).
                //
                // Antes esto era un `putIfAbsent` SIN CONDICIÓN, y eso mentía: con cualquier PDF
                // que no sea el contrato de Orange el campo no existe en el AcroForm, así que
                // `getField()` devolvía null y la clave acababa SIEMPRE en `missing`. La app
                // informaba de un campo que falta y que nunca debió pedir — ruido justo en lo que
                // hay que observar al conectar el relleno dinámico (fase 5).
                //
                // Ahora se inyecta sólo si la plantilla tiene de verdad ese campo, así que en
                // Orange se comporta exactamente igual que antes y en el resto desaparece.
                //
                // Sigue siendo un `putIfAbsent` y no un `put`: `WizardViewModel` ya pre-rellena
                // este campo con el nombre configurado en Ajustes, y ese valor manda sobre la
                // constante de aquí. Esto es sólo la red por si se llega a generar el PDF por un
                // camino que no pasó por la extracción.
                if (form.getField(realName(ContractFields.RESPONSABLE_KEY)) != null) {
                    effective.putIfAbsent(
                        ContractFields.RESPONSABLE_KEY, ContractFields.RESPONSABLE_VALUE
                    )
                }

                for ((name, value) in effective) {
                    val field = form.getField(realName(name))
                    if (field == null) { missing.add(name); continue }
                    runCatching { field.setValue(value); filled++ }.onFailure { missing.add(name) }
                }
                for ((name, value) in checkboxes) {
                    val field = form.getField(realName(name)) ?: continue
                    runCatching { applyButtonValue(field, value); filled++ }
                        .onFailure { missing.add(name) }
                }
                if (form.getField(ContractFields.RESPONSABLE_KEY) != null &&
                    distributorSignaturePageIndex < doc.numberOfPages) {
                    gapPage = distributorSignaturePageIndex
                }
                if (flatten) form.flatten()
            }

            // Estampar la firma
            if (signature != null && stamps.isNotEmpty()) {
                val img: PDImageXObject =
                    PDImageXObject.createFromByteArray(doc, signature.pngBytes, "firma")
                for (st in stamps) {
                    if (st.pageIndex !in 0 until doc.numberOfPages) continue
                    val page = doc.getPage(st.pageIndex)
                    val pw = page.mediaBox.width
                    val ph = page.mediaBox.height
                    // Caja disponible en el contrato (calibrada contra el hueco real,
                    // NO el tamaño de la firma). La firma se escala para CABER dentro
                    // de esta caja sin deformarse (letterbox), en vez de forzar su
                    // altura a partir del aspect ratio de la imagen de origen — eso
                    // causaba el recorte/ampliación en exceso: una firma con trazo
                    // muy ancho y fino, o muy vertical, deformaba la caja real del
                    // hueco de firma del contrato.
                    val boxW = st.widthRel * pw
                    val boxH = st.heightRel * ph
                    val boxAspect = boxH / boxW              // relación de la CAJA
                    val sigAspect = signature.aspectRatio    // relación de la FIRMA real

                    val w: Float; val h: Float
                    if (sigAspect > boxAspect) {
                        // La firma es más "alta" que la caja → limita por altura.
                        h = boxH
                        w = h / sigAspect
                    } else {
                        // La firma es más "ancha" que la caja (o igual) → limita por ancho.
                        w = boxW
                        h = w * sigAspect
                    }

                    // xRel,yRel = centro de la CAJA; yRel 0 = arriba → convertir a coords PDF
                    // (0 abajo). La firma queda centrada dentro de esa caja aunque sea más
                    // pequeña que ella.
                    val x = st.xRel * pw - w / 2f
                    val y = (1f - st.yRel) * ph - h / 2f
                    PDPageContentStream(
                        doc, page, PDPageContentStream.AppendMode.APPEND, true, true
                    ).use { cs -> cs.drawImage(img, x, y, w, h) }
                }
            }

            doc.save(output)
            return FillResult(filled, missing, gapPage)
        }
    }

    private fun collectNames(field: PDField): List<String> = listOf(field.fullyQualifiedName)

    /**
     * Marca o desmarca una casilla resolviendo **el estado de activación real del PDF**, en vez
     * de asumir `/On`.
     *
     * ── Por qué ──
     * El nombre del estado "encendido" de una casilla lo elige quien generó el PDF, y no hay
     * ninguna convención. Verificado sobre los formularios reales de Aire:
     *
     * | PDF          | Estados de activación |
     * |--------------|-----------------------|
     * | Portabilidad | `Sí`                  |
     * | Contrato     | `Sí`, y `0`…`5` en los grupos de opción |
     * | SEPA         | `Opción1`, `Opción2`  |
     *
     * `ContractFields.CHECKBOX_ON` valía `/On`, que no existe en ninguno de ellos: en esos PDFs
     * no se marcaría ni una casilla. Además `PDButton.setValue()` valida contra los estados
     * declarados y lanza si no encaja, así que el fallo era **silencioso** (el `runCatching` de
     * arriba no reportaba nada).
     *
     * ── Cómo ──
     * - Casilla: `check()` / `unCheck()`, que es la propia PDFBox quien resuelve el estado bueno.
     * - Cualquier otro botón (grupos de opción): se busca el valor pedido entre `onValues`,
     *   tolerando que venga con la barra inicial (`/Sí` ↔ `Sí`), que es como se han escrito
     *   históricamente las constantes de esta clase.
     * - Cualquier otro tipo de campo: comportamiento anterior.
     *
     * Se considera "apagar" tanto `Off` como `/Off` y la cadena vacía.
     *
     * Sólo se usa API verificada contra el fuente de `pdfbox-android 2.0.27.0`: `PDCheckBox`
     * expone `check()`/`unCheck()`/`getOnValue()` y `PDButton` expone `getOnValues()`. Un
     * primer intento usó `PDRadioButton.getSelectableValues()`, que **no existe en esta
     * versión** y tumbó el build.
     */
    private fun applyButtonValue(field: PDField, requested: String) {
        val bare = requested.trim().removePrefix("/")
        val wantsOff = bare.isEmpty() || bare.equals(OFF_STATE, ignoreCase = true)

        when (field) {
            // PDCheckBox hereda de PDButton, así que va primero.
            is PDCheckBox -> if (wantsOff) field.unCheck() else field.check()

            is PDButton -> {
                if (wantsOff) {
                    field.setValue(OFF_STATE)
                } else {
                    val onValues = runCatching { field.onValues }.getOrDefault(emptySet())
                    field.setValue(onValues.firstOrNull { it == bare } ?: bare)
                }
            }

            else -> field.setValue(requested)
        }
    }

    private companion object {
        /** Estado "sin marcar", el único que sí fija la especificación del PDF. */
        const val OFF_STATE = "Off"
    }
}
