package com.mejoresiagratis.rellenador

import com.mejoresiagratis.rellenador.data.model.ContractFields
import com.mejoresiagratis.rellenador.data.model.FieldKind
import com.mejoresiagratis.rellenador.data.model.FormField
import com.mejoresiagratis.rellenador.data.model.FormSchema
import com.mejoresiagratis.rellenador.data.model.FormSection
import com.mejoresiagratis.rellenador.data.model.SchemaSource
import com.mejoresiagratis.rellenador.data.model.onlyButtons
import org.junit.Assert.*
import org.junit.Test

/**
 * Tanda 5·4k — el filtro de los mapas de casillas **fijos** contra el esquema activo.
 *
 * El caso real: en el PDF generado del QA de Aire salió impreso «NIF: Off». `CHECKBOX_NIF` vale
 * literalmente `"NIF"`, que es el nombre de la casilla de tipo de identificación de **Orange**;
 * el contrato de empresas de **Aire** tiene un campo de TEXTO llamado también `NIF` (el del
 * representante). Comprobado con `pypdf` sobre `Contrato_empresas.pdf`: de los tres nombres de
 * Orange (`NIF`, `CIF`, `undefined`) es el único que colisiona.
 *
 * Como estos mapas se suman DESPUÉS de `routeFieldValues()`, se saltaban el reparto por
 * `FieldKind` entero y `AcroFormFiller.applyButtonValue` acababa en su rama
 * `else -> field.setValue(requested)`, escribiendo la cadena `Off` dentro de un campo de texto.
 */
class FixedButtonsTest {

    private fun schema(vararg fields: FormField) = FormSchema(
        id = "t", title = "t", source = SchemaSource.LEARNED, fingerprint = "t", pageCount = 1,
        sections = listOf(FormSection(id = "s", title = "s", fields = fields.toList())),
    )

    @Test
    fun `no escribe Off en el campo de texto NIF del contrato de Aire`() {
        val aire = schema(
            FormField(name = "NIF", label = "NIF", kind = FieldKind.TEXT),
            FormField(name = "Casilla de verificación 56", label = "ALTA", kind = FieldKind.CHECKBOX),
        )
        val fijos = ContractFields.checkboxStateFor("CIF") +
            mapOf("Casilla de verificación 56" to ContractFields.CHECKBOX_ON)

        val got = onlyButtons(fijos, aire)

        assertFalse("el NIF de texto no puede recibir un estado de casilla", got.containsKey("NIF"))
        assertEquals(ContractFields.CHECKBOX_ON, got["Casilla de verificación 56"])
    }

    @Test
    fun `en Orange las tres casillas siguen pasando`() {
        val orange = schema(
            FormField(name = ContractFields.CHECKBOX_CIF, label = "CIF", kind = FieldKind.CHECKBOX),
            FormField(name = ContractFields.CHECKBOX_NIF, label = "NIF", kind = FieldKind.CHECKBOX),
            FormField(name = ContractFields.CHECKBOX_NIE, label = "NIE", kind = FieldKind.CHECKBOX),
        )
        val fijos = ContractFields.checkboxStateFor("CIF")
        assertEquals(fijos, onlyButtons(fijos, orange))
    }

    @Test
    fun `un nombre que el esquema no conoce se deja pasar`() {
        val got = onlyButtons(mapOf("desconocido" to "On"), schema())
        assertEquals(mapOf("desconocido" to "On"), got)
    }

    @Test
    fun `sin esquema no se filtra nada`() {
        val fijos = ContractFields.checkboxStateFor("NIF")
        assertEquals(fijos, onlyButtons(fijos, null))
    }

    @Test
    fun `un hueco de firma tampoco recibe un estado de casilla`() {
        val s = schema(FormField(name = "Signature1", label = "Firma", kind = FieldKind.SIGNATURE))
        assertTrue(onlyButtons(mapOf("Signature1" to "On"), s).isEmpty())
    }
}
