package com.mejoresiagratis.rellenador

import com.mejoresiagratis.rellenador.data.model.FormSchema
import com.mejoresiagratis.rellenador.data.model.LabelSource
import com.mejoresiagratis.rellenador.data.model.SectionKind
import com.mejoresiagratis.rellenador.data.pdf.FormSchemaBuilder
import com.mejoresiagratis.rellenador.data.pdf.LayoutTextExtractor
import com.mejoresiagratis.rellenador.data.pdf.PdfFieldInspector
import org.junit.Assert.*
import org.junit.Test

/**
 * Tanda 5·4b — etiquetado orgánico (`docs/PLAN_ETIQUETADO_ORGANICO.md`).
 *
 * La miniatura reproduce la estructura del `Contrato_empresas.pdf` real, con coordenadas en la
 * convención de `PdfFieldInspector.Field` (origen arriba-izquierda): una cabecera anterior a
 * cualquier ancla, el bloque `DATOS DEL CLIENTE` (ancla sin casilla) y la banda
 * `TELEFONÍA FIJA SERVICIOS DE VOZ` (ancla CON casilla) con su tabla de tarifa.
 *
 * No sustituye a la verificación contra el PDF real —hecha con `pypdf`/`pdfplumber` al preparar
 * el plan y repetida al implementar—, sino que atrapa regresiones de la lógica del constructor
 * sin depender de Android, de pdfbox ni de subir a Actions. Al escribirlas destaparon dos fallos
 * reales antes del primer push; ver el `CHANGELOG` de la 0.10.13.
 */
class FormSchemaBuilderAnchorTest {

    private fun field(
        name: String,
        page: Int = 0,
        x: Float,
        y: Float,
        width: Float = 100f,
        height: Float = 12f,
        isCheckbox: Boolean = false,
    ) = PdfFieldInspector.Field(
        name = name, page = page, x = x, y = y, width = width, height = height,
        isCheckbox = isCheckbox,
    )

    private fun word(text: String, x: Float, y: Float, endX: Float, fontSize: Float, page: Int = 0) =
        LayoutTextExtractor.Word(page = page, text = text, x = x, y = y, endX = endX, fontSize = fontSize)

    private val fields = listOf(
        // Cabecera: anterior a cualquier ancla.
        field("Casilla de verificación 56", x = 200f, y = 15f, isCheckbox = true),
        field("Casilla de verificación 57", x = 200f, y = 28f, isCheckbox = true),
        field("Casilla de verificación 58", x = 200f, y = 41f, isCheckbox = true),
        field("NOMBRE", x = 350f, y = 15f),
        field("TFNO.", x = 350f, y = 28f),
        // DATOS DEL CLIENTE: ancla sin casilla.
        field("Nombre  Razón Social", x = 120f, y = 100f),
        field("NIF/CIF/NIE", x = 400f, y = 100f),
        // TELEFONÍA FIJA: ancla con casilla-interruptor a su izquierda.
        field("Botón de opción 5", x = 30f, y = 243f, width = 12f, height = 12f, isCheckbox = true),
        // Tabla de tarifa: 4 filas × 3 columnas.
        field("Campo de texto 1", x = 50f, y = 260f),
        field("TF cantidad 01", x = 200f, y = 260f),
        field("TF cuotalta 01", x = 350f, y = 260f),
        field("Campo de texto 2", x = 50f, y = 275f),
        field("TF cantidad 02", x = 200f, y = 275f),
        field("TF cuotalta 02", x = 350f, y = 275f),
        field("Campo de texto 3", x = 50f, y = 290f),
        field("TF cantidad 03", x = 200f, y = 290f),
        field("TF cuotalta 03", x = 350f, y = 290f),
        field("Campo de texto 4", x = 50f, y = 305f),
        field("TF cantidad 04", x = 200f, y = 305f),
        field("TF cuotalta 04", x = 350f, y = 305f),
    )

    private val words = listOf(
        // Cabecera de página: dentro del margen superior, nunca es ancla.
        word("CONTRATO EMPRESAS", x = 35f, y = 10f, endX = 200f, fontSize = 11f),
        // Ancla sin casilla + rótulo a la izquierda del primer campo.
        word("DATOS DEL CLIENTE", x = 26f, y = 94f, endX = 150f, fontSize = 8f),
        word("NOMBRE", x = 20f, y = 100f, endX = 60f, fontSize = 7f),
        word("O", x = 62f, y = 100f, endX = 70f, fontSize = 7f),
        word("RAZÓN", x = 72f, y = 100f, endX = 105f, fontSize = 7f),
        word("SOCIAL:", x = 107f, y = 100f, endX = 119f, fontSize = 7f),
        // Ancla con casilla-interruptor pegada a la izquierda.
        word("TELEFONÍA", x = 45.6f, y = 244f, endX = 100f, fontSize = 12f),
        word("FIJA", x = 102f, y = 244f, endX = 120f, fontSize = 12f),
        word("SERVICIOS", x = 122f, y = 244f, endX = 170f, fontSize = 12f),
        word("DE", x = 172f, y = 244f, endX = 180f, fontSize = 12f),
        word("VOZ", x = 182f, y = 244f, endX = 200f, fontSize = 12f),
        // Cabecera de columna, encima de la primera celda de esa columna.
        word("Cantidad", x = 195f, y = 250f, endX = 230f, fontSize = 7f),
        // Repetido una vez por página: en la lista negra, nunca es ancla.
        word("DOCUMENTACIÓN", x = 37f, y = 320f, endX = 100f, fontSize = 9f),
    )

    private fun buildWithAnchors(): FormSchema = FormSchemaBuilder().build(
        fields = fields,
        fingerprint = "test",
        pageCount = 1,
        title = "Contrato_empresas.pdf",
        layoutWords = words,
    )

    private fun buildLegacy(): FormSchema = FormSchemaBuilder().build(
        fields = fields,
        fingerprint = "test-legacy",
        pageCount = 1,
        title = "Contrato_empresas.pdf",
        // Sin layoutWords: camino de respaldo, el algoritmo literal de la 5·4.
    )

    @Test fun sectionTitlesComeFromThePdfText() {
        val titles = buildWithAnchors().sections.map { it.title }
        assertFalse("ninguna sección debe llamarse «Página N»", titles.any { it.startsWith("Página ") })
        assertFalse("ninguna sección debe llamarse «Tabla N»", titles.any { it.startsWith("Tabla ") })
        assertTrue(titles.contains("DATOS DEL CLIENTE"))
        assertTrue(titles.contains("TELEFONÍA FIJA SERVICIOS DE VOZ"))
        assertTrue("lo anterior a la primera ancla va a «Cabecera»", titles.contains("Cabecera"))
    }

    /** El fallo de orden que dejó la 5·4: DATOS DEL CLIENTE salía en tercera posición. */
    @Test fun looseFieldsKeepTheirPdfOrderAgainstTables() {
        val titles = buildWithAnchors().sections.map { it.title }
        val cabecera = titles.indexOf("Cabecera")
        val cliente = titles.indexOf("DATOS DEL CLIENTE")
        val telefonia = titles.indexOf("TELEFONÍA FIJA SERVICIOS DE VOZ")
        assertTrue(cabecera in 0 until cliente)
        assertTrue(cliente in 0 until telefonia)
        assertTrue("DATOS DEL CLIENTE no puede salir la tercera", cliente < 2)
    }

    @Test fun bandCheckboxBecomesTheSectionEnabler() {
        val schema = buildWithAnchors()
        val cliente = schema.sections.first { it.title == "DATOS DEL CLIENTE" }
        assertNull("sin casilla al lado no hay interruptor", cliente.enablerField)

        val banda = schema.sections.filter { it.title == "TELEFONÍA FIJA SERVICIOS DE VOZ" }
        assertTrue(banda.isNotEmpty())
        assertEquals(
            "una sola sección de la banda lleva el interruptor",
            1,
            banda.count { it.enablerField == "Botón de opción 5" },
        )
        assertFalse(
            "el interruptor no puede salir además como campo suelto",
            banda.any { sec -> sec.fields.any { it.name == "Botón de opción 5" } },
        )
    }

    @Test fun tableStaysInsideItsBandAndInheritsColumnHeader() {
        val banda = buildWithAnchors().sections.filter { it.title == "TELEFONÍA FIJA SERVICIOS DE VOZ" }
        val tabla = banda.firstOrNull { it.kind == SectionKind.TABLE }
        assertNotNull("la tabla cae dentro de su banda", tabla)
        assertEquals(3, tabla!!.columns.size)
        assertEquals(4, tabla.rows.size)
        assertTrue(
            "la columna hereda la cabecera de texto, no «Columna N»",
            tabla.columns.any { it.label == "Cantidad" },
        )
    }

    @Test fun looseFieldGetsGeometricLabelInsteadOfTechnicalName() {
        val cliente = buildWithAnchors().sections.first { it.title == "DATOS DEL CLIENTE" }
        val nombre = cliente.fields.first { it.name == "Nombre  Razón Social" }
        assertEquals("NOMBRE O RAZÓN SOCIAL", nombre.label)
    }

    /** Sin texto de layout, el comportamiento de la 5·4 tiene que quedar intacto. */
    @Test fun fallbackPathIsUnchanged() {
        val legacy = buildLegacy()
        assertTrue(legacy.sections.map { it.title }.contains("Página 1"))
        assertTrue(legacy.sections.all { it.enablerField == null })
        assertEquals(0, legacy.builderVersion)
    }

    @Test fun schemaRecordsWhichBuilderMadeIt() {
        assertEquals(FormSchema.BUILDER_VERSION, buildWithAnchors().builderVersion)
    }

    /** Regeneración perezosa: sólo si es de una versión vieja Y nadie ha editado a mano. */
    @Test fun staleSchemaIsRebuildableOnlyWithoutUserEdits() {
        val legacy = buildLegacy()
        assertTrue("un esquema viejo sin ediciones se regenera", legacy.isStaleBuild())
        assertFalse("uno ya construido con anclas, no", buildWithAnchors().isStaleBuild())

        val editableIdx = legacy.sections.indexOfFirst { it.fields.isNotEmpty() }
        assertTrue(editableIdx >= 0)
        val touched = legacy.copy(
            sections = legacy.sections.mapIndexed { i, sec ->
                if (i != editableIdx) sec else sec.copy(
                    fields = sec.fields.mapIndexed { j, f ->
                        if (j != 0) f else f.copy(
                            label = "Etiqueta puesta a mano",
                            labelSource = LabelSource.USUARIO,
                        )
                    }
                )
            }
        )
        assertTrue(touched.hasUserLabels())
        assertFalse("con trabajo manual no se regenera nunca", touched.isStaleBuild())
    }
}
