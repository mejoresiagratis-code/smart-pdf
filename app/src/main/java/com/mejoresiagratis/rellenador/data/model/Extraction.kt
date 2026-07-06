package com.mejoresiagratis.rellenador.data.model

import kotlinx.serialization.Serializable

/** Respuesta JSON que la IA devuelve dentro de proxy.text. Fiel a la app web. */
@Serializable
data class AiExtraction(
    val sugerencias: Map<String, String> = emptyMap(),
    val tipo_identificacion: String? = null,           // "CIF" | "NIF" | "NIE"
    val alternativas: Map<String, List<AltValue>> = emptyMap(),
    val paquetes: List<Paquete> = emptyList()
)

@Serializable
data class AltValue(
    val valor: String,
    val fuente: String = "",
    val nota: String = ""
)

@Serializable
data class Paquete(
    val tipo: String,                 // direccion | direccion_comercio | empresa | persona | banco
    val etiqueta: String = "",
    val fuente: String = "",
    val datos: Map<String, String> = emptyMap()
)

/**
 * Una propuesta agregada por campo, con todos los valores candidatos y qué
 * motores los propusieron. Alimenta el toast de confirmación por campo.
 */
data class FieldProposal(
    val fieldKey: String,
    val candidates: List<Candidate> = emptyList()
)

data class Candidate(
    val value: String,
    val sources: Set<String> = emptySet(),   // motores que lo propusieron (Claude, Gemini…)
    val note: String = ""
)

/** Campo canónico del contrato con su etiqueta legible (los 15 de la web). */
data class CanonField(val key: String, val label: String)

object ContractFields {
    /** Claves canónicas EXACTAS — los dobles espacios importan. */
    val CANON: List<CanonField> = listOf(
        CanonField("Nombre  Razón Social", "Razón social"),
        CanonField("Nombre Comercial", "Nombre comercial"),
        CanonField("NIE", "CIF/NIF/NIE de la empresa"),
        CanonField("Nombre representante", "Nombre del representante"),
        CanonField("NIF representante", "NIF del representante"),
        CanonField("Dirección", "Dirección (fiscal)"),
        CanonField("CP", "Código postal"),
        CanonField("Población", "Población"),
        CanonField("Provincia", "Provincia"),
        CanonField("Dirección_2", "Dirección comercio/PdV"),
        CanonField("CP_2", "CP comercio"),
        CanonField("Población_2", "Población comercio"),
        CanonField("Provincia_2", "Provincia comercio"),
        CanonField("Teléfono", "Teléfono"),
        CanonField("Email", "Email"),
        CanonField("Datos bancarios del DISTRIBUIDOR", "IBAN"),
        CanonField("Fecha", "Fecha · día"),
        CanonField("de", "Fecha · mes"),
        CanonField("año", "Fecha · año"),
    )

    /** Valor fijo autorrellenado (regla de la app web). */
    const val RESPONSABLE_KEY = "Responsable Comercial MASORANGE"
    const val RESPONSABLE_VALUE = "PABLO SALVADOR POVEDA"

    fun labelFor(key: String): String =
        CANON.firstOrNull { it.key == key }?.label ?: key
}

/**
 * Aplica un paquete a los campos, replicando applyPaquete() de la web.
 * Las claves del paquete vienen SIN sufijo (Dirección/CP/Población/Provincia).
 * `targetBlock2 = true` las envía al bloque de comercio (_2); false al fiscal.
 * Devuelve el mapa de (claveCanónica -> valor) a fusionar en fieldValues.
 */
object PackageApplier {
    /** Claves de dirección que admiten el sufijo _2. */
    private val ADDRESS_KEYS = setOf("Dirección", "CP", "Población", "Provincia")

    fun apply(paquete: Paquete, targetBlock2: Boolean): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for ((key, value) in paquete.datos) {
            if (value.isBlank()) continue
            val targetKey = if (targetBlock2 && key in ADDRESS_KEYS) "${key}_2" else key
            out[targetKey] = value
        }
        return out
    }

    /** ¿Este paquete puede ir al bloque _2? (solo direcciones). */
    fun canTargetBlock2(paquete: Paquete): Boolean =
        paquete.tipo == "direccion" || paquete.tipo == "direccion_comercio"
}
