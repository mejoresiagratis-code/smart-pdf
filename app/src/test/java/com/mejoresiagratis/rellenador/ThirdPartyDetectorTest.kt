package com.mejoresiagratis.rellenador

import com.mejoresiagratis.rellenador.data.model.FieldKind
import com.mejoresiagratis.rellenador.data.model.FormField
import com.mejoresiagratis.rellenador.data.model.FormSchema
import com.mejoresiagratis.rellenador.data.model.FormSection
import com.mejoresiagratis.rellenador.data.model.SchemaSource
import com.mejoresiagratis.rellenador.data.model.ThirdPartyDetector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tanda 5·4j — `ThirdPartyDetector`. Los títulos de las pruebas salen del contrato de Aire real
 * (el QA con datos de MOFIZOL, donde el CIF del cliente acabó impreso como el del donante).
 */
class ThirdPartyDetectorTest {

    private fun f(name: String) = FormField(name = name, label = name, kind = FieldKind.TEXT)

    private fun schema(sectionTitle: String, vararg fields: FormField) = FormSchema(
        id = "t", title = "t", source = SchemaSource.LEARNED, fingerprint = "fp", pageCount = 1,
        sections = listOf(FormSection(id = "s1", title = sectionTitle, fields = fields.toList())),
    )

    @Test
    fun `los bloques de tercero del contrato de Aire se reconocen`() {
        assertTrue(ThirdPartyDetector.isThirdParty("CAMBIO TITULAR"))
        assertTrue(ThirdPartyDetector.isThirdParty("CAPTURA DE FIBRA CON CAMBIO DE TITULARIDAD"))
        assertTrue(ThirdPartyDetector.isThirdParty("Razón social / Nombre y apellidos titular donante"))
        assertTrue(ThirdPartyDetector.isThirdParty("FIRMA TITULAR DONANTE"))
    }

    @Test
    fun `las tildes y las mayusculas no impiden reconocerlo`() {
        assertTrue(ThirdPartyDetector.isThirdParty("cambio de titularidad"))
        assertTrue(ThirdPartyDetector.isThirdParty("Titular de la Línea"))
    }

    @Test
    fun `las secciones del titular NO se marcan`() {
        assertFalse(ThirdPartyDetector.isThirdParty("DATOS DEL CLIENTE"))
        assertFalse(ThirdPartyDetector.isThirdParty("PRODUCTOS Y SERVICIOS CONTRATADOS"))
        assertFalse(ThirdPartyDetector.isThirdParty("TELEFONÍA FIJA SERVICIOS DE VOZ"))
        assertFalse(ThirdPartyDetector.isThirdParty(""))
    }

    @Test
    fun `marcar propaga la bandera a todos los campos de la seccion`() {
        val s = ThirdPartyDetector.mark(schema("CAMBIO TITULAR", f("A"), f("B")))
        assertTrue(s.allFields().all { it.thirdParty })
    }

    @Test
    fun `una seccion del titular se queda sin marcar`() {
        val s = ThirdPartyDetector.mark(schema("DATOS DEL CLIENTE", f("A"), f("B")))
        assertTrue(s.allFields().none { it.thirdParty })
    }
}
