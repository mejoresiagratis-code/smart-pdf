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
    /**
     * Valores confirmados. Desde [VERSION] 2 (tanda 5·3) van por **nombre real** de campo del
     * AcroForm; antes iban por clave de `CANON`. Ver [migrated].
     */
    val campos: Map<String, String> = emptyMap(),
    val fieldMapping: Map<String, String> = emptyMap(),  // si venía de un PDF con mapeo propio

    /**
     * Versión del formato de [campos]. Ausente (0) = perfil guardado antes de la tanda 5·3, con
     * las claves de `CANON`. Los perfiles ya guardados se migran **al leerlos**, no en bloque.
     */
    val version: Int = 0,
) {
    /**
     * Devuelve el perfil con [campos] indexados por nombre real de campo.
     *
     * No hace falta saber a qué PDF pertenecía: **el propio perfil guarda su `fieldMapping`**, así
     * que lleva dentro su tabla de traducción. Un perfil de Orange lo trae vacío y la clave de
     * `CANON` ya es el nombre real, con lo que la conversión es la identidad. Es idempotente, así
     * que releer un perfil ya migrado no lo estropea.
     */
    fun migrated(): ContractProfile {
        if (version >= VERSION) return this
        val keys = FieldKeys(fieldMapping)
        return copy(campos = keys.reindex(campos), version = VERSION)
    }

    companion object {
        /** 2 = [campos] por nombre real de campo (tanda 5·3). */
        const val VERSION = 2
    }
}

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
