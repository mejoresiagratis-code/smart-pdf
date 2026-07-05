package com.mejoresiagratis.rellenador

import com.mejoresiagratis.rellenador.data.model.Candidate
import com.mejoresiagratis.rellenador.data.model.ContractFields
import com.mejoresiagratis.rellenador.data.remote.AiJsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeStrategyTest {

    @Test fun doubleSpaceFieldNamesPreserved() {
        val razon = ContractFields.CANON.first { it.label == "Razón social" }
        assertEquals("Nombre  Razón Social", razon.key)
        assertTrue("debe conservar el doble espacio", razon.key.contains("  "))
    }

    @Test fun parserExtractsWrappedJson() {
        val raw = "Claro, aquí tienes:\n```json\n{\"sugerencias\":{\"NIE\":\"B24838195\"}}\n``` fin"
        val ex = AiJsonParser.parse(raw)
        assertNotNull(ex)
        assertEquals("B24838195", ex!!.sugerencias["NIE"])
    }

    @Test fun consensusOrdering() {
        val a = Candidate("v1", setOf("Claude"))
        val b = Candidate("v2", setOf("Claude", "Gemini"))
        val sorted = listOf(a, b).sortedByDescending { it.sources.size }
        assertEquals("v2", sorted.first().value)
    }
}
