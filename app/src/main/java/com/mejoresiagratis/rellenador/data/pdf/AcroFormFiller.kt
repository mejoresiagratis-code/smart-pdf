package com.mejoresiagratis.rellenador.data.pdf

import com.mejoresiagratis.rellenador.data.model.ContractFields
import com.mejoresiagratis.rellenador.data.model.SignatureData
import com.mejoresiagratis.rellenador.data.model.SignatureStamp
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
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
        flatten: Boolean = false
    ): FillResult {
        PDDocument.load(template).use { doc ->
            val form = doc.documentCatalog.acroForm
            val missing = mutableListOf<String>()
            var filled = 0
            var gapPage: Int? = null

            if (form != null) {
                form.needAppearances = true
                val effective = values.toMutableMap()
                effective.putIfAbsent(ContractFields.RESPONSABLE_KEY, ContractFields.RESPONSABLE_VALUE)

                for ((name, value) in effective) {
                    val field = form.getField(name)
                    if (field == null) { missing.add(name); continue }
                    runCatching { field.setValue(value); filled++ }.onFailure { missing.add(name) }
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
                    val w = st.widthRel * pw
                    val h = w * signature.aspectRatio
                    // xRel,yRel = centro; yRel 0 = arriba → convertir a coords PDF (0 abajo)
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
}
