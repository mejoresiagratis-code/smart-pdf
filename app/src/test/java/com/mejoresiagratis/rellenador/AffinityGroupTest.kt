package com.mejoresiagratis.rellenador

import com.mejoresiagratis.rellenador.data.model.AffinityGroup
import com.mejoresiagratis.rellenador.data.model.CanonicalKeys
import com.mejoresiagratis.rellenador.data.model.FieldKind
import com.mejoresiagratis.rellenador.data.model.FormField
import com.mejoresiagratis.rellenador.data.model.FormSchema
import com.mejoresiagratis.rellenador.data.model.FormSection
import com.mejoresiagratis.rellenador.data.model.SchemaSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tanda 5·4i — `AffinityGroup.candidatesFor`: detección de huecos candidatos a compartir dato. */
class AffinityGroupTest {

    private fun f(
        name: String,
        label: String = name,
        kind: FieldKind = FieldKind.TEXT,
        canonical: String? = null,
        thirdParty: Boolean = false,
    ) = FormField(name = name, label = label, kind = kind, canonical = canonical, thirdParty = thirdParty)

    private fun schema(vararg fields: FormField) = FormSchema(
        id = "t", title = "t", source = SchemaSource.LEARNED, fingerprint = "fp", pageCount = 1,
        sections = listOf(FormSection(id = "s1", title = "S1", fields = fields.toList())),
    )

    @Test
    fun `misma etiqueta impresa normalizada es candidata`() {
        val filled = f("Campo 1", label = "Nombre  o  Razón Social")
        val vacio = f("Campo 9", label = "NOMBRE O RAZÓN SOCIAL")
        val s = schema(filled, vacio)
        val out = AffinityGroup.candidatesFor(s, filled, emptyFieldNames = setOf("Campo 9"))
        assertEquals(listOf("Campo 9"), out.map { it.name })
    }

    @Test
    fun `misma canonica ya asignada es candidata aunque la etiqueta difiera`() {
        val filled = f("Campo 1", label = "Nombre fiscal", canonical = CanonicalKeys.RAZON_SOCIAL)
        val vacio = f("Campo 2", label = "Razón social del titular", canonical = CanonicalKeys.RAZON_SOCIAL)
        val s = schema(filled, vacio)
        val out = AffinityGroup.candidatesFor(s, filled, emptyFieldNames = setOf("Campo 2"))
        assertEquals(listOf("Campo 2"), out.map { it.name })
    }

    @Test
    fun `direccion y direccion 2 del contrato de Orange NO son candidatas`() {
        // El caso explícito que motivó blindar la heurística: mismo `name` base, etiquetas
        // impresas distintas (fiscal vs instalación) -> no deben salir como afines.
        val fiscal = f("Dirección", label = "Dirección fiscal")
        val instalacion = f("Dirección_2", label = "Dirección de instalación")
        val s = schema(fiscal, instalacion)
        val out = AffinityGroup.candidatesFor(s, fiscal, emptyFieldNames = setOf("Dirección_2"))
        assertTrue(out.isEmpty())
    }

    @Test
    fun `un campo de un tercero no es candidato aunque el rotulo coincida`() {
        val titular = f("Campo 1", label = "Nombre y apellidos", thirdParty = false)
        val donante = f("Campo 5", label = "Nombre y apellidos", thirdParty = true)
        val s = schema(titular, donante)
        val out = AffinityGroup.candidatesFor(s, titular, emptyFieldNames = setOf("Campo 5"))
        assertTrue(out.isEmpty())
    }

    @Test
    fun `un campo con valor no se ofrece porque no esta en emptyFieldNames`() {
        val filled = f("Campo 1", label = "Nombre")
        val relleno = f("Campo 2", label = "NOMBRE")
        val s = schema(filled, relleno)
        val out = AffinityGroup.candidatesFor(s, filled, emptyFieldNames = emptySet())
        assertTrue(out.isEmpty())
    }

    @Test
    fun `casillas y radios no son candidatos`() {
        val filled = f("Campo 1", label = "Nombre")
        val checkbox = f("Campo 2", label = "Nombre", kind = FieldKind.CHECKBOX)
        val s = schema(filled, checkbox)
        val out = AffinityGroup.candidatesFor(s, filled, emptyFieldNames = setOf("Campo 2"))
        assertTrue(out.isEmpty())
    }
}
