package com.mejoresiagratis.rellenador.data.model

import com.mejoresiagratis.rellenador.ui.wizard.FieldCandidate
import com.mejoresiagratis.rellenador.ui.wizard.FieldOrigin
import com.mejoresiagratis.rellenador.ui.wizard.FieldState

/**
 * Decide QUÉ se autorrellena y QUÉ exige una decisión del usuario, al fundir la antigua
 * "Revisión IA" dentro del paso de Relleno (v0.8.0).
 *
 * ── Por qué no basta el consenso de motores ──
 * `Candidate.sources` es un conjunto de MOTORES, no de documentos. En producción solo hay
 * dos motores activos (Gemini y Groq), así que el consenso máximo posible es 2. Si un
 * documento que NO pertenece al cliente se cuela en el lote (caso real: el certificado
 * censal de otra persona con el mismo nombre de archivo), AMBOS motores lo leen y
 * extraen el mismo dato equivocado → consenso 2/2, el máximo. Autorrellenar por consenso
 * a secas pintaría ese dato como "verificado" y lo colaría en un contrato firmado.
 *
 * Por eso la política mira además la PROCEDENCIA:
 *  - de qué tipo de documento sale el dato, y si ese tipo es una fuente legítima para
 *    ese campo concreto (un IBAN de un contrato de alquiler es del arrendador, no del
 *    distribuidor);
 *  - si el titular del documento casa con el del resto del lote.
 */
object AutoFillPolicy {

    /**
     * Campos cuyo valor, viniendo de estos documentos, suele pertenecer a un TERCERO y no
     * al distribuidor. No se autorrellenan nunca: se marcan [FieldState.WARN].
     */
    private val RISKY_SOURCES: Map<String, Set<String>> = mapOf(
        "Datos bancarios del DISTRIBUIDOR" to setOf("Contrato de alquiler", "Factura"),
        "Email Comercial" to setOf("Contrato de alquiler", "Alta en RETA"),
        "Email  Facturación" to setOf("Contrato de alquiler", "Alta en RETA"),
        "Teléfono" to setOf("Contrato de alquiler"),
        "Nombre representante" to setOf("Modelo 036"),   // suele firmarlo la gestoría
        "NIF representante" to setOf("Modelo 036"),
    )

    /** Documentos que acreditan identidad/titularidad: fuente fuerte para datos fiscales. */
    private val STRONG_ID_SOURCES = setOf(
        "Tarjeta CIF/NIF", "Certificado de situación censal", "Modelo 036",
        "DNI", "NIE / Permiso de residencia", "Pasaporte", "Escritura de constitución",
    )

    /**
     * Resuelve el estado de un campo a partir de sus candidatos.
     *
     * - 0 candidatos                       → [FieldState.EMPTY]
     * - 2+ valores distintos               → [FieldState.CONFLICT] (el usuario elige)
     * - 1 valor de procedencia dudosa      → [FieldState.WARN]     (el usuario confirma)
     * - 1 valor de procedencia legítima    → [FieldState.AI]       (autorrelleno)
     */
    fun decide(fieldKey: String, candidates: List<FieldCandidate>): FieldState = when {
        candidates.isEmpty() -> FieldState.EMPTY
        candidates.distinctBy { it.value.trim().lowercase() }.size > 1 -> FieldState.CONFLICT
        isRisky(fieldKey, candidates.first().origin) -> FieldState.WARN
        else -> FieldState.AI
    }

    /** ¿El valor viene de una fuente que, para ESTE campo, suele ser de un tercero? */
    fun isRisky(fieldKey: String, origin: FieldOrigin): Boolean =
        origin.risky || RISKY_SOURCES[fieldKey]?.contains(origin.document) == true

    /**
     * Marca como dudosos los candidatos cuyo documento quedó fuera del consenso del lote.
     * [expectedHolders] son los identificadores (NIF/NIE/CIF) que dominan el conjunto de
     * documentos; un documento que aporta otro titular es sospechoso de no ser del cliente.
     */
    fun flagIntruders(
        candidates: List<FieldCandidate>,
        documentHolders: Map<String, String>,
        expectedHolders: Set<String>,
    ): List<FieldCandidate> {
        if (expectedHolders.isEmpty()) return candidates
        return candidates.map { c ->
            val holder = documentHolders[c.origin.document]
            val intruder = holder != null && holder !in expectedHolders
            if (intruder) c.copy(
                origin = c.origin.copy(
                    risky = true,
                    note = listOfNotNull(
                        c.origin.note.takeIf { it.isNotBlank() },
                        "este documento parece de otro titular ($holder)",
                    ).joinToString(" · ")
                )
            ) else c
        }
    }

    /** ¿Es una fuente fuerte de identidad para datos fiscales/identificativos? */
    fun isStrongIdentity(document: String): Boolean = document in STRONG_ID_SOURCES
}
