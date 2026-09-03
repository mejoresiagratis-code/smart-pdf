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
     * Documentos de identidad de una PERSONA FÍSICA.
     *
     * Son fuente fuerte para el nombre y el NIF del representante (siguen en [STRONG_ID_SOURCES]
     * y ahí se quedan) y fuente dudosa para todo lo demás: el domicilio del reverso de un DNI es
     * el particular de la persona, no el de la empresa, y lo mismo el teléfono y el correo.
     *
     * Verificado con un juego real de documentación de un alta: el domicilio del DNI y el
     * domicilio fiscal de la sociedad estaban en el mismo municipio y la misma provincia, así que
     * **`cpProvinciaMsg` daba verde con el equivocado**. La validación no puede detectar esto;
     * sólo la procedencia.
     *
     * Efecto en Orange, deliberado: cuando la dirección venga ÚNICAMENTE de un documento de
     * identidad (distribuidor autónomo sin censal ni 036 en el lote), pasa de autorrellenarse a
     * pedir una confirmación. No se pierde el valor: sale como alternativa en la hoja de decisión.
     * Es un toque de más a cambio de no escribir el domicilio particular de una persona en el
     * campo de domicilio de una sociedad.
     */
    private val ID_DOCS = setOf("DNI", "NIE / Permiso de residencia", "Pasaporte")

    /**
     * Campos cuyo valor, viniendo de estos documentos, suele pertenecer a un TERCERO y no
     * al distribuidor. No se autorrellenan nunca: se marcan [FieldState.WARN].
     */
    private val RISKY_SOURCES: Map<String, Set<String>> = mapOf(
        CanonicalKeys.IBAN to setOf("Contrato de alquiler", "Factura") + ID_DOCS,
        CanonicalKeys.EMAIL_COMERCIAL to setOf("Contrato de alquiler", "Alta en RETA") + ID_DOCS,
        CanonicalKeys.EMAIL_FACTURACION to setOf("Contrato de alquiler", "Alta en RETA") + ID_DOCS,
        CanonicalKeys.TELEFONO to setOf("Contrato de alquiler") + ID_DOCS,
        // suele firmarlo la gestoría
        CanonicalKeys.REPRESENTANTE_NOMBRE to setOf("Modelo 036"),
        CanonicalKeys.REPRESENTANTE_NIF to setOf("Modelo 036"),
        // El domicilio de un documento de identidad es el PARTICULAR de la persona. Ver ID_DOCS.
        CanonicalKeys.DIRECCION to ID_DOCS,
        CanonicalKeys.CP to ID_DOCS,
        CanonicalKeys.POBLACION to ID_DOCS,
        CanonicalKeys.PROVINCIA to ID_DOCS,
        CanonicalKeys.DIRECCION_2 to ID_DOCS,
        CanonicalKeys.CP_2 to ID_DOCS,
        CanonicalKeys.POBLACION_2 to ID_DOCS,
        CanonicalKeys.PROVINCIA_2 to ID_DOCS,
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
    fun decide(
        fieldKey: String,
        candidates: List<FieldCandidate>,
        canonicalHint: String? = null,
    ): FieldState = when {
        candidates.isEmpty() -> FieldState.EMPTY
        candidates.distinctBy { it.value.trim().lowercase() }.size > 1 -> FieldState.CONFLICT
        isRisky(fieldKey, candidates.first().origin, canonicalHint) -> FieldState.WARN
        else -> FieldState.AI
    }

    /**
     * ¿El valor viene de una fuente que, para ESTE campo, suele ser de un tercero?
     *
     * Tanda 5·3 — [RISKY_SOURCES] se indexaba por los nombres de campo de Orange, y `fieldKey`
     * es el nombre real del campo del PDF que se esté rellenando. Con un PDF que no fuera el de
     * Orange **ninguna clave casaba y esta protección se apagaba en silencio**: el IBAN de un
     * contrato de alquiler (que es el del arrendador, no el del distribuidor) se habría
     * autorrellenado como dato verificado. Ahora la tabla va por clave canónica y quien llama la
     * resuelve con `FieldKeys.canonicalOf()`; el nombre se sigue probando como respaldo para no
     * perder el caso de Orange si llega sin resolver.
     */
    fun isRisky(fieldKey: String, origin: FieldOrigin, canonicalHint: String? = null): Boolean {
        if (origin.risky) return true
        val canonical = canonicalHint ?: BuiltinSchemas.canonicalFor(fieldKey)
        return RISKY_SOURCES[canonical]?.contains(origin.document) == true
    }

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
