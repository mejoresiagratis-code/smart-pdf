package com.mejoresiagratis.rellenador

import com.mejoresiagratis.rellenador.data.model.FieldKind
import com.mejoresiagratis.rellenador.data.model.FormField
import com.mejoresiagratis.rellenador.data.model.FormSchema
import com.mejoresiagratis.rellenador.data.model.FormSection
import com.mejoresiagratis.rellenador.data.model.SchemaSource
import com.mejoresiagratis.rellenador.ui.wizard.fillSectionsFrom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tanda 5·4h — el aplanado del esquema no puede repetir un nombre.
 *
 * Un nombre repetido no era sólo cosmético: la clave duplicada rompía `rememberSaveable(key)` y
 * la lista perezosa de `FillStep`, y además dos filas editaban el mismo valor.
 */
class FillSectionsDedupTest {

    private fun f(name: String, kind: FieldKind = FieldKind.TEXT, onState: String? = null) =
        FormField(name = name, label = name, kind = kind, onState = onState)

    private fun schema(vararg secciones: Pair<String, List<FormField>>) = FormSchema(
        id = "t", title = "t", source = SchemaSource.LEARNED, fingerprint = "fp", pageCount = 1,
        sections = secciones.mapIndexed { i, (titulo, campos) ->
            FormSection(id = "s$i", title = titulo, fields = campos)
        },
    )

    @Test
    fun `un nombre repetido entre secciones solo se pinta una vez`() {
        val out = fillSectionsFrom(schema(
            "A" to listOf(f("CP")),
            "B" to listOf(f("CP"), f("Localidad")),
        ))
        assertEquals(listOf("CP"), out[0].keys)
        assertEquals(listOf("Localidad"), out[1].keys)
    }

    @Test
    fun `una seccion que se queda vacia al deduplicar desaparece`() {
        val out = fillSectionsFrom(schema(
            "A" to listOf(f("CP")),
            "B" to listOf(f("CP")),
        ))
        assertEquals(1, out.size)
    }

    @Test
    fun `un grupo de radio es una sola clave`() {
        val out = fillSectionsFrom(schema(
            "A" to listOf(
                f("Opcion", FieldKind.RADIO, "0"),
                f("Opcion", FieldKind.RADIO, "1"),
                f("Opcion", FieldKind.RADIO, "2"),
            ),
        ))
        assertEquals(listOf("Opcion"), out[0].keys)
    }

    @Test
    fun `las firmas no se ofrecen para rellenar`() {
        val out = fillSectionsFrom(schema(
            "A" to listOf(f("Firma", FieldKind.SIGNATURE), f("CP")),
        ))
        assertEquals(listOf("CP"), out[0].keys)
    }

    @Test
    fun `ningun nombre aparece dos veces en el resultado`() {
        val out = fillSectionsFrom(schema(
            "A" to listOf(f("X"), f("Y")),
            "A" to listOf(f("Y"), f("Z")),
            "" to listOf(f("X"), f("W")),
        ))
        val todas = out.flatMap { it.keys }
        assertTrue(todas.size == todas.toSet().size)
    }
}
