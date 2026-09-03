package com.mejoresiagratis.rellenador

import com.mejoresiagratis.rellenador.data.model.CanonicalKeys
import com.mejoresiagratis.rellenador.data.model.CanonicalSiblings
import com.mejoresiagratis.rellenador.data.model.FieldKind
import com.mejoresiagratis.rellenador.data.model.FormField
import com.mejoresiagratis.rellenador.data.model.FormSchema
import com.mejoresiagratis.rellenador.data.model.FormSection
import com.mejoresiagratis.rellenador.data.model.SchemaSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Tanda 5·4i — `CanonicalSiblings.expand`: reparto de valor entre campos que comparten canónica. */
class CanonicalSiblingsTest {

    private fun f(name: String, canonical: String? = null) =
        FormField(name = name, label = name, kind = FieldKind.TEXT, canonical = canonical)

    private fun schema(vararg fields: FormField) = FormSchema(
        id = "t", title = "t", source = SchemaSource.LEARNED, fingerprint = "fp", pageCount = 1,
        sections = listOf(FormSection(id = "s1", title = "S1", fields = fields.toList())),
    )

    @Test
    fun `sin esquema no amplia nada`() {
        val out = CanonicalSiblings.expand(null, emptyMap(), mapOf("A" to "MOFIZOL, S.L."))
        assertEquals(mapOf("A" to "MOFIZOL, S.L."), out)
    }

    @Test
    fun `sin canonicas repetidas es identidad`() {
        val s = schema(f("A", CanonicalKeys.CP), f("B"))
        val out = CanonicalSiblings.expand(s, emptyMap(), mapOf("A" to "28001"))
        assertEquals(mapOf("A" to "28001"), out)
    }

    @Test
    fun `reparte a un hermano vacio que comparte canonica`() {
        val s = schema(f("A", CanonicalKeys.RAZON_SOCIAL), f("B", CanonicalKeys.RAZON_SOCIAL), f("C"))
        val out = CanonicalSiblings.expand(s, emptyMap(), mapOf("A" to "MOFIZOL, S.L."))
        assertEquals("MOFIZOL, S.L.", out["A"])
        assertEquals("MOFIZOL, S.L.", out["B"])
        assertFalse(out.containsKey("C"))
    }

    @Test
    fun `no pisa un hermano que ya tiene valor distinto`() {
        val s = schema(f("A", CanonicalKeys.RAZON_SOCIAL), f("B", CanonicalKeys.RAZON_SOCIAL))
        val out = CanonicalSiblings.expand(
            s, currentValues = mapOf("B" to "OTRO VALOR"), delta = mapOf("A" to "MOFIZOL, S.L."),
        )
        assertEquals("MOFIZOL, S.L.", out["A"])
        assertFalse(out.containsKey("B"))
    }

    @Test
    fun `un valor en blanco no se reparte`() {
        val s = schema(f("A", CanonicalKeys.RAZON_SOCIAL), f("B", CanonicalKeys.RAZON_SOCIAL))
        val out = CanonicalSiblings.expand(s, emptyMap(), mapOf("A" to ""))
        assertEquals(mapOf("A" to ""), out)
    }

    @Test
    fun `orange no tiene canonicas repetidas`() {
        // Documenta la garantía citada en el comentario de la clase: con el esquema construido
        // como BuiltinSchemas.orangeDistribution() (sin dos campos con la misma canónica),
        // expand() no debe tocar nada — aquí se simula con el mismo patrón (una canónica, un
        // campo) que usa CANON_TO_CANONICAL.
        val s = schema(f("Nombre  Razón Social", CanonicalKeys.RAZON_SOCIAL), f("CP", CanonicalKeys.CP))
        val out = CanonicalSiblings.expand(s, emptyMap(), mapOf("CP" to "28001"))
        assertEquals(mapOf("CP" to "28001"), out)
    }
}
