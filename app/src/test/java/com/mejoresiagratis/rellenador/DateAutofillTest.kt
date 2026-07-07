package com.mejoresiagratis.rellenador

import com.mejoresiagratis.rellenador.data.model.DateAutofill
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class DateAutofillTest {

    @Test fun fillsEmptyDateFields() {
        val result = DateAutofill.values(emptyMap())
        assertTrue(result.containsKey("Fecha"))
        assertTrue(result.containsKey("de"))
        assertTrue(result.containsKey("año"))
    }

    @Test fun yearIsLastDigitOnly() {
        val result = DateAutofill.values(emptyMap())
        assertEquals(1, result["año"]?.length)
        val expected = Calendar.getInstance().get(Calendar.YEAR).toString().takeLast(1)
        assertEquals(expected, result["año"])
    }

    @Test fun monthIsSpanishName() {
        val result = DateAutofill.values(emptyMap())
        val meses = setOf("enero","febrero","marzo","abril","mayo","junio",
            "julio","agosto","septiembre","octubre","noviembre","diciembre")
        assertTrue(result["de"] in meses)
    }

    @Test fun doesNotOverwriteExistingDates() {
        val result = DateAutofill.values(mapOf("Fecha" to "10", "de" to "marzo", "año" to "9"))
        assertFalse(result.containsKey("Fecha"))   // ya tenía valor
        assertFalse(result.containsKey("de"))
        assertFalse(result.containsKey("año"))
    }
}
