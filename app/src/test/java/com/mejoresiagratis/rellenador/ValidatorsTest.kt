package com.mejoresiagratis.rellenador

import com.mejoresiagratis.rellenador.data.validation.FieldNormalizer
import com.mejoresiagratis.rellenador.data.validation.FieldValidator
import com.mejoresiagratis.rellenador.data.validation.SpanishValidators
import org.junit.Assert.*
import org.junit.Test

class ValidatorsTest {

    @Test fun dni() {
        assertTrue(SpanishValidators.validDNI("12345678Z"))
        assertFalse(SpanishValidators.validDNI("12345678A"))
        assertTrue(SpanishValidators.validDNI("12345678-Z"))   // con separador
    }

    @Test fun nie() {
        assertTrue(SpanishValidators.validNIE("X1234567L"))
        assertFalse(SpanishValidators.validNIE("X1234567Z"))
    }

    @Test fun cif() {
        // A82528548 = Xfera Móviles, del propio contrato
        assertTrue(SpanishValidators.validCIF("A82528548"))
        assertFalse(SpanishValidators.validCIF("B12345678"))
    }

    @Test fun iban() {
        assertTrue(SpanishValidators.validIBAN("ES9121000418450200051332"))
        assertFalse(SpanishValidators.validIBAN("ES0000000000000000000000"))
        assertTrue(SpanishValidators.validIBAN("ES91 2100 0418 4502 0005 1332")) // con espacios
    }

    @Test fun phone() {
        assertTrue(SpanishValidators.validPhone("612345678"))
        assertFalse(SpanishValidators.validPhone("512345678"))
    }

    @Test fun normalizeIban() {
        assertEquals("ES9121000418450200051332",
            FieldNormalizer.normVal("Datos bancarios del DISTRIBUIDOR", "es91-2100-0418-4502-0005-1332"))
    }

    @Test fun normalizeName() {
        assertEquals("Juan Pérez García",
            FieldNormalizer.normVal("Nombre representante", "Pérez García, Juan"))
    }

    @Test fun cpPadding() {
        assertEquals("01234", FieldNormalizer.normVal("CP", "1234"))
    }

    @Test fun cpProvinciaCoherence() {
        // 46xxx = Valencia
        assertNull(FieldNormalizer.cpProvinciaMsg("46001", "Valencia"))
        assertNotNull(FieldNormalizer.cpProvinciaMsg("46001", "Madrid"))
    }

    @Test fun fieldValidatorCifWithType() {
        val r = FieldValidator.validate("NIE", "A82528548", tipoId = "CIF")
        assertTrue(r?.ok == true)
    }
}
