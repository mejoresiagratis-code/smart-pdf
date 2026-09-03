package com.mejoresiagratis.rellenador

import com.mejoresiagratis.rellenador.data.model.FieldKeys
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tanda 5·4e — `FieldKeys.labelOf()` con las etiquetas del esquema.
 *
 * El fallo que cierra esta tanda: `labelOf` sólo resolvía por la vía canónica, así que en un PDF
 * ajeno (donde `canonical` es null) devolvía el nombre real y la etiqueta corregida a mano en el
 * editor no llegaba nunca al paso de Relleno.
 */
class FieldKeysLabelTest {

    @Test
    fun `la etiqueta del esquema se usa cuando no hay canonica`() {
        val keys = FieldKeys(
            mapping = emptyMap(),
            labels = mapOf("Casilla de verificación 59" to "Sólo tráfico nacional"),
        )
        assertEquals("Sólo tráfico nacional", keys.labelOf("Casilla de verificación 59"))
    }

    @Test
    fun `la etiqueta del esquema manda sobre la canonica`() {
        // La corrección del usuario (LabelSource.USUARIO) no la pisa ningún automatismo.
        val keys = FieldKeys(
            mapping = mapOf("NIE" to "NIF/CIF"),
            labels = mapOf("NIF/CIF" to "NIF de la empresa"),
        )
        assertEquals("NIF de la empresa", keys.labelOf("NIF/CIF"))
    }

    @Test
    fun `una etiqueta vacia no tapa la via canonica`() {
        val keys = FieldKeys(mapping = emptyMap(), labels = mapOf("NIE" to "   "))
        // "NIE" es clave de CANON, así que resuelve por `ContractFields.labelFor`.
        assertEquals(FieldKeys().labelOf("NIE"), keys.labelOf("NIE"))
    }

    @Test
    fun `sin etiquetas el comportamiento es el de antes`() {
        val antes = FieldKeys(mapping = emptyMap())
        assertEquals("Campo de texto 116", antes.labelOf("Campo de texto 116"))
        assertEquals(antes.labelOf("NIE"), FieldKeys().labelOf("NIE"))
    }

    @Test
    fun `un campo desconocido sigue cayendo a su nombre real`() {
        val keys = FieldKeys(mapping = emptyMap(), labels = mapOf("Otro" to "Etiqueta"))
        assertEquals("Campo de texto 116", keys.labelOf("Campo de texto 116"))
    }
}
