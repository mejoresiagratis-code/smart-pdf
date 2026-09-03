package com.mejoresiagratis.rellenador

import com.mejoresiagratis.rellenador.data.model.FieldKind
import com.mejoresiagratis.rellenador.data.model.FormField
import com.mejoresiagratis.rellenador.data.model.FormSchema
import com.mejoresiagratis.rellenador.data.model.FormSection
import com.mejoresiagratis.rellenador.data.model.SchemaSource
import com.mejoresiagratis.rellenador.data.model.routeFieldValues
import org.junit.Assert.*
import org.junit.Test

/**
 * Tanda 5·4d — reparto de valores por `FieldKind` (`ValueRouting.kt`).
 *
 * Los estados de activación de las casillas y radios son los **reales** del
 * `Contrato_empresas.pdf` de Aire (`/Sí`, `/0`..`/5`, `/Opción1`), que es donde el camino
 * anterior —todo por el mapa de texto— se rompía en silencio.
 *
 * La comprobación que más importa es la última: que el contrato de Orange, cuyo esquema es
 * `BUILTIN` y declara todo como `TEXT`, produzca **exactamente** los mismos dos mapas que antes
 * de esta tanda.
 */
class ValueRoutingTest {

    private fun f(
        name: String,
        kind: FieldKind = FieldKind.TEXT,
        onState: String? = null,
        optionLabel: String? = null,
    ) = FormField(name = name, label = name, kind = kind, onState = onState, optionLabel = optionLabel)

    private fun schema(vararg fields: FormField, source: SchemaSource = SchemaSource.LEARNED) =
        FormSchema(
            id = "test", title = "test", source = source, fingerprint = "fp", pageCount = 1,
            sections = listOf(FormSection(id = "s1", title = "S1", fields = fields.toList())),
        )

    @Test
    fun `el texto sigue yendo por el mapa de texto`() {
        val s = schema(f("Nombre o razón social"))
        val r = routeFieldValues(mapOf("Nombre o razón social" to "ACME SL"), s)
        assertEquals(mapOf("Nombre o razón social" to "ACME SL"), r.text)
        assertTrue(r.buttons.isEmpty())
    }

    @Test
    fun `una casilla marcada usa su estado real y no On`() {
        val s = schema(f("Casilla de verificación 12", FieldKind.CHECKBOX, onState = "Sí"))
        val r = routeFieldValues(mapOf("Casilla de verificación 12" to "On"), s)
        assertTrue(r.text.isEmpty())
        assertEquals(mapOf("Casilla de verificación 12" to "Sí"), r.buttons)
    }

    @Test
    fun `una casilla vacia o Off se apaga`() {
        val s = schema(f("Casilla de verificación 12", FieldKind.CHECKBOX, onState = "Sí"))
        assertEquals("Off", routeFieldValues(mapOf("Casilla de verificación 12" to ""), s).buttons.values.single())
        assertEquals("Off", routeFieldValues(mapOf("Casilla de verificación 12" to "Off"), s).buttons.values.single())
    }

    @Test
    fun `el estado cero no se confunde con apagado`() {
        val s = schema(f("Botón de opción 10", FieldKind.RADIO, onState = "0", optionLabel = "PAGO ÚNICO"))
        val r = routeFieldValues(mapOf("Botón de opción 10" to "0"), s)
        assertEquals("0", r.buttons.values.single())
    }

    @Test
    fun `un grupo de radio resuelve por etiqueta de opcion`() {
        // Las seis opciones comparten el nombre del AcroForm: es un único campo.
        val s = schema(
            f("Botón de opción 10", FieldKind.RADIO, onState = "0", optionLabel = "PAGO ÚNICO"),
            f("Botón de opción 10", FieldKind.RADIO, onState = "1", optionLabel = "12 MESES"),
            f("Botón de opción 10", FieldKind.RADIO, onState = "2", optionLabel = "24 MESES"),
        )
        val r = routeFieldValues(mapOf("Botón de opción 10" to "24 MESES"), s)
        assertEquals(mapOf("Botón de opción 10" to "2"), r.buttons)
    }

    @Test
    fun `el estado con barra inicial se acepta`() {
        val s = schema(f("Radio", FieldKind.RADIO, onState = "Opción1", optionLabel = "A"))
        assertEquals("Opción1", routeFieldValues(mapOf("Radio" to "/Opción1"), s).buttons.values.single())
    }

    @Test
    fun `los campos de firma no se escriben por ninguna via`() {
        val s = schema(f("Firma cliente", FieldKind.SIGNATURE))
        val r = routeFieldValues(mapOf("Firma cliente" to "cualquier cosa"), s)
        assertTrue(r.text.isEmpty())
        assertTrue(r.buttons.isEmpty())
        assertEquals(listOf("Firma cliente"), r.skippedSignatures)
    }

    @Test
    fun `una clave que el esquema no conoce sale por texto`() {
        val s = schema(f("Conocido"))
        val r = routeFieldValues(mapOf("Responsable" to "Pablo"), s)
        assertEquals(mapOf("Responsable" to "Pablo"), r.text)
    }

    @Test
    fun `sin esquema activo no se clasifica nada`() {
        val values = mapOf("a" to "1", "b" to "2")
        val r = routeFieldValues(values, null)
        assertEquals(values, r.text)
        assertTrue(r.buttons.isEmpty())
    }

    @Test
    fun `un esquema BUILTIN todo TEXT no cambia nada`() {
        val values = mapOf(
            "Nombre o razón social" to "ACME SL",
            "NIF/CIF/NIE" to "B12345678",
            "Domicilio" to "Calle Mayor 1",
        )
        val s = schema(
            f("Nombre o razón social"), f("NIF/CIF/NIE"), f("Domicilio"),
            source = SchemaSource.BUILTIN,
        )
        val r = routeFieldValues(values, s)
        assertEquals(values, r.text)
        assertTrue(r.buttons.isEmpty())
        assertTrue(r.skippedSignatures.isEmpty())
    }
}
