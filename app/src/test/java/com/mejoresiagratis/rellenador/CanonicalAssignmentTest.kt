package com.mejoresiagratis.rellenador

import com.mejoresiagratis.rellenador.data.model.CanonicalCatalog
import com.mejoresiagratis.rellenador.data.model.CanonicalKeys
import com.mejoresiagratis.rellenador.data.model.FieldKind
import com.mejoresiagratis.rellenador.data.model.FormField
import com.mejoresiagratis.rellenador.data.model.FormSchema
import com.mejoresiagratis.rellenador.data.model.FormSection
import com.mejoresiagratis.rellenador.data.model.LabelSource
import com.mejoresiagratis.rellenador.data.model.SchemaEditing
import com.mejoresiagratis.rellenador.data.model.SchemaSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tanda 5·4f — asignación de canónicas: `SchemaEditing.setCanonical` y `CanonicalCatalog`. */
class CanonicalAssignmentTest {

    private fun f(name: String, label: String = name, canonical: String? = null) =
        FormField(name = name, label = label, kind = FieldKind.TEXT, canonical = canonical)

    private fun schema(vararg fields: FormField) = FormSchema(
        id = "t", title = "t", source = SchemaSource.LEARNED, fingerprint = "fp", pageCount = 1,
        sections = listOf(FormSection(id = "s1", title = "S1", fields = fields.toList())),
    )

    private fun FormSchema.field(name: String) = allFields().first { it.name == name }

    @Test
    fun `asignar una canonica la escribe y marca la edicion como del usuario`() {
        val s = SchemaEditing.setCanonical(schema(f("Campo de texto 116")), "Campo de texto 116", CanonicalKeys.CP)
        assertEquals(CanonicalKeys.CP, s.field("Campo de texto 116").canonical)
        assertEquals(LabelSource.USUARIO, s.field("Campo de texto 116").labelSource)
    }

    @Test
    fun `pasar null quita la asignacion`() {
        val s = SchemaEditing.setCanonical(
            schema(f("A", canonical = CanonicalKeys.CP)), "A", null,
        )
        assertNull(s.field("A").canonical)
    }

    @Test
    fun `una canonica no puede quedar en dos campos a la vez`() {
        // El caso real: la IA engancha «CP:» y luego el usuario decide que el bueno es el otro.
        val s = SchemaEditing.setCanonical(
            schema(f("A", canonical = CanonicalKeys.CP), f("B")), "B", CanonicalKeys.CP,
        )
        assertEquals(CanonicalKeys.CP, s.field("B").canonical)
        assertNull(s.field("A").canonical)
    }

    @Test
    fun `el representante gana sobre la identificacion de la empresa`() {
        assertEquals(CanonicalKeys.REPRESENTANTE_NIF, CanonicalCatalog.proposeFor("NIF del representante"))
        assertEquals(CanonicalKeys.IDENTIFICACION, CanonicalCatalog.proposeFor("CIF/NIF"))
    }

    @Test
    fun `la direccion de instalacion no se confunde con la fiscal`() {
        assertEquals(CanonicalKeys.CP, CanonicalCatalog.proposeFor("CP:"))
        assertEquals(CanonicalKeys.CP_2, CanonicalCatalog.proposeFor("CP instalación"))
        assertEquals(CanonicalKeys.DIRECCION_2, CanonicalCatalog.proposeFor("Dirección de instalación"))
        assertEquals(CanonicalKeys.PROVINCIA, CanonicalCatalog.proposeFor("Provincia:"))
    }

    @Test
    fun `las tildes y los dos puntos no impiden reconocer la etiqueta`() {
        assertEquals(CanonicalKeys.POBLACION, CanonicalCatalog.proposeFor("Localidad:"))
        assertEquals(CanonicalKeys.POBLACION, CanonicalCatalog.proposeFor("POBLACIÓN"))
    }

    @Test
    fun `ante la duda no se propone nada`() {
        assertNull(CanonicalCatalog.proposeFor("Campo de texto 116"))
        assertNull(CanonicalCatalog.proposeFor("Sólo tráfico nacional"))
        assertNull(CanonicalCatalog.proposeFor(""))
    }

    @Test
    fun `el catalogo no tiene claves repetidas`() {
        assertEquals(CanonicalCatalog.ALL.size, CanonicalCatalog.ALL.map { it.key }.toSet().size)
    }
}
