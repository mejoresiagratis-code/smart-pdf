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
