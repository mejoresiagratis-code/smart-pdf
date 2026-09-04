package com.mejoresiagratis.rellenador

import com.mejoresiagratis.rellenador.data.model.FieldRect
import com.mejoresiagratis.rellenador.data.model.FormField
import com.mejoresiagratis.rellenador.data.model.FormSchema
import com.mejoresiagratis.rellenador.data.model.FormSection
import com.mejoresiagratis.rellenador.data.model.LabelSource
import com.mejoresiagratis.rellenador.data.model.LabelTargetPlan
import com.mejoresiagratis.rellenador.data.model.SchemaSource
import com.mejoresiagratis.rellenador.data.model.SectionKind
import com.mejoresiagratis.rellenador.data.model.TableColumn
import org.junit.Assert.*
import org.junit.Test

/**
 * Tanda 5·4k — el plan de tandas del etiquetado por visión.
 *
 * Lo que se comprueba aquí es justo lo que la 0.10.26 dejó abierto: la página NO se pregunta de
 * una vez, así que numerar los tokens en orden de lectura **de la página** sólo alinea la
 * primera tanda. Medido sobre `Contrato_empresas.pdf`: 49 objetivos en la página 1, 56 en la 2
 * y 31 en la 3, o sea 3, 3 y 2 tandas — 66 de 136 objetivos con el índice desplazado en bloque.
 */
class LabelTargetPlanTest {

    private fun campo(name: String, page: Int, x: Float, y: Float) = FormField(
        name = name, label = "", page = page, rect = FieldRect(x, y, 80f, 12f)
    )

    private fun schema(vararg sections: FormSection) = FormSchema(
        id = "t", title = "t", source = SchemaSource.LEARNED,
        fingerprint = "t", pageCount = 3, sections = sections.toList(),
    )

    /** Una página con [n] campos, uno por fila, de arriba abajo. */
    private fun pagina(page: Int, n: Int) = FormSection(
        id = "s$page", title = "P$page", kind = SectionKind.SIMPLE,
        fields = (0 until n).map { campo("p$page-c$it", page, x = 50f, y = 40f + it * 20f) },
    )

    @Test
    fun `cada tanda numera sus tokens desde cero`() {
        val batches = LabelTargetPlan.build(schema(pagina(0, 60)))
        assertTrue("se esperaban varias tandas", batches.size > 1)
        for (b in batches) {
            val primero = b.targets.first().token
            assertEquals("toda tanda empieza en 0", "${LabelTargetPlan.TOKEN_FIELD}0", primero)
        }
    }

    @Test
    fun `ninguna tanda pasa del tope y no se pierde ningun objetivo`() {
        val batches = LabelTargetPlan.build(schema(pagina(0, 49), pagina(1, 56), pagina(2, 31)))
        assertEquals(49 + 56 + 31, batches.sumOf { it.targets.size })
        assertTrue(batches.all { it.targets.size <= LabelTargetPlan.MAX_TARGETS_PER_CALL })
    }

    @Test
    fun `una fila impresa no se parte entre dos tandas`() {
        // 30 campos en 3 filas de 10: con tope 24, la tercera fila entera debe irse a la
        // segunda tanda en vez de partirse por el campo 24.
        val fields = (0 until 3).flatMap { fila ->
            (0 until 10).map { col ->
                campo("f$fila-c$col", 0, x = 20f + col * 40f, y = 100f + fila * 30f)
            }
        }
        val batches = LabelTargetPlan.build(
            schema(FormSection(id = "s", title = "s", kind = SectionKind.SIMPLE, fields = fields))
        )
        assertEquals(2, batches.size)
        assertEquals(20, batches[0].targets.size)
        assertEquals(10, batches[1].targets.size)
        // La segunda tanda es exactamente la fila 2, no un trozo de la 1 más un trozo de la 2.
        assertTrue(batches[1].targets.all { it.fieldName!!.startsWith("f2-") })
    }

    @Test
    fun `una fila mas larga que el tope se parte, porque no hay alternativa`() {
        val fields = (0 until 30).map { campo("c$it", 0, x = 10f + it * 15f, y = 100f) }
        val batches = LabelTargetPlan.build(
            schema(FormSection(id = "s", title = "s", kind = SectionKind.SIMPLE, fields = fields))
        )
        assertEquals(2, batches.size)
        assertEquals(30, batches.sumOf { it.targets.size })
    }

    @Test
    fun `la banda acota de verdad los objetivos de la tanda`() {
        val batches = LabelTargetPlan.build(schema(pagina(0, 48)))
        assertEquals(2, batches.size)
        assertTrue("la primera tanda va arriba", batches[0].topPt < batches[1].topPt)
        assertTrue(
            "la banda no puede empezar por encima del objetivo más alto",
            batches[1].topPt >= batches[0].bottomPt - 1f
        )
        for (b in batches) {
            assertEquals(b.targets.minOf { it.rect.y }, b.topPt, 0.01f)
            assertEquals(b.targets.maxOf { it.rect.y + it.rect.height }, b.bottomPt, 0.01f)
        }
    }

    @Test
    fun `se deja fuera lo corregido a mano y lo que no tiene geometria`() {
        val section = FormSection(
            id = "s", title = "s", kind = SectionKind.SIMPLE,
            fields = listOf(
                campo("normal", 0, 10f, 100f),
                campo("aMano", 0, 10f, 130f).copy(labelSource = LabelSource.USUARIO),
                FormField(name = "sinRect", label = "", page = 0, rect = null),
            ),
        )
        val targets = LabelTargetPlan.build(schema(section)).flatMap { it.targets }
        assertEquals(listOf("normal"), targets.mapNotNull { it.fieldName })
    }

    @Test
    fun `las celdas de tabla no se preguntan, sus columnas si`() {
        val section = FormSection(
            id = "tabla", title = "TARIFAS", kind = SectionKind.TABLE,
            fields = listOf(campo("celda", 0, 10f, 300f)),
            columns = listOf(
                TableColumn(id = "col1", label = "", x = 10f, page = 0, rect = FieldRect(10f, 280f, 60f, 12f))
            ),
        )
        val targets = LabelTargetPlan.build(schema(section)).flatMap { it.targets }
        assertEquals(listOf("col1"), targets.mapNotNull { it.columnId })
        assertTrue(targets.none { it.fieldName != null })
        assertTrue(targets.first().token.startsWith(LabelTargetPlan.TOKEN_COLUMN))
    }

    @Test
    fun `un esquema sin geometria no genera ninguna llamada`() {
        val section = FormSection(
            id = "s", title = "s", kind = SectionKind.SIMPLE,
            fields = listOf(FormField(name = "a", label = "a", page = 0, rect = null)),
        )
        assertTrue(LabelTargetPlan.build(schema(section)).isEmpty())
    }
}
