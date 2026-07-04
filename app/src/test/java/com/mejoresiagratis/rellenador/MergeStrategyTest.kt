package com.mejoresiagratis.rellenador

import com.mejoresiagratis.rellenador.data.model.ExtractedField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the exact-name contract for the fragile double-space fields. */
class MergeStrategyTest {
    @Test fun doubleSpaceFieldNamesPreserved() {
        val f = ExtractedField("Nombre  Razón Social", "ACME SL")
        assertEquals("Nombre  Razón Social", f.fieldName)
        assertTrue("must keep the double space", f.fieldName.contains("  "))
    }
}
