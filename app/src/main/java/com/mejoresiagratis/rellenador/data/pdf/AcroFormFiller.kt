package com.mejoresiagratis.rellenador.data.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

/**
 * Fills the 54-page Orange/MASORANGE AcroForm.
 *
 * CRITICAL — field names are exact and fragile (from the web version):
 *  - double-spaces matter: "Nombre  Razón Social", "Email  Facturación"
 *  - accents matter here (unlike the JS norm() path). Match the raw AcroForm name.
 *  - autofill "Responsable Comercial MASORANGE" = "PABLO SALVADOR POVEDA"
 *  - page 24 has NO AcroForm fields: signature gap handled separately by a
 *    named-field heuristic, NOT by structural detection.
 */
class AcroFormFiller @Inject constructor() {

    data class FillResult(
        val filledCount: Int,
        val missingFields: List<String>,
        val signatureGapDetected: Boolean
    )

    fun listFields(template: InputStream): List<String> {
        PDDocument.load(template).use { doc ->
            val form = doc.documentCatalog.acroForm ?: return emptyList()
            return form.fields.flatMap { collectNames(it) }
        }
    }

    fun fill(
        template: InputStream,
        values: Map<String, String>,
        output: OutputStream,
        flatten: Boolean = false
    ): FillResult {
        PDDocument.load(template).use { doc ->
            val form = doc.documentCatalog.acroForm
                ?: return FillResult(0, values.keys.toList(), false)
            form.needAppearances = true

            var filled = 0
            val missing = mutableListOf<String>()
            val effective = values.toMutableMap()

            // Auto-fill rule from the web app.
            effective.putIfAbsent(
                "Responsable Comercial MASORANGE",
                "PABLO SALVADOR POVEDA"
            )

            for ((name, value) in effective) {
                val field = form.getField(name)
                if (field == null) { missing.add(name); continue }
                runCatching { field.setValue(value); filled++ }
                    .onFailure { missing.add(name) }
            }

            // Heuristic: presence of the MASORANGE responsible field implies the
            // page-24 signature gap that has no AcroForm fields.
            val signatureGap = form.getField("Responsable Comercial MASORANGE") != null

            if (flatten) form.flatten()
            doc.save(output)
            return FillResult(filled, missing, signatureGap)
        }
    }

    private fun collectNames(field: PDField): List<String> =
        listOf(field.fullyQualifiedName)
}
