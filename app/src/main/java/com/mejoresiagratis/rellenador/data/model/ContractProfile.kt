package com.mejoresiagratis.rellenador.data.model

import kotlinx.serialization.Serializable

/**
 * Objeto "perfil" reutilizado para exportar/importar, historial y detección de
 * plantilla — fiel a buildProfileObject() de la web. Contiene los campos
 * confirmados (fieldValues) y metadatos para poder aplicarlo a otro contrato.
 */
@Serializable
data class ContractProfile(
    val tipo: String = "perfil-rellenador-pdv",   // marca de fichero propio (fiel a _tipo)
    val label: String = "",
    val guardado: String = "",                     // fecha ISO
    val fingerprint: String = "",
    val campos: Map<String, String> = emptyMap(),
    val fieldMapping: Map<String, String> = emptyMap()   // si venía de un PDF con mapeo propio
)

/** Entrada de historial: el perfil + un id y contador de campos. */
@Serializable
data class HistoryEntry(
    val id: String,
    val profile: ContractProfile,
    val count: Int
)

object TemplateFingerprint {
    private fun norm(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Huella de la plantilla: nº de páginas + nombres de campo normalizados y ordenados.
     *  Fiel a templateFingerprint() de la web. */
    fun of(totalPages: Int, fieldNames: List<String>): String {
        val names = fieldNames.map { norm(it) }.sorted()
        return "$totalPages|${names.joinToString(",")}"
    }
}
