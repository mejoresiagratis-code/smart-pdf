package com.mejoresiagratis.rellenador.data.pdf

import java.text.Normalizer

/**
 * Traduce el NOMBRE DE ARCHIVO real de un documento aportado (p. ej.
 * "CERTIFICADO_DE_SITUACION_CENSAL.PDF") a un nombre de TIPO legible para mostrar en
 * la UI ("Certificado de situación censal") en vez del ID crudo del proveedor SAF
 * ("document:17077").
 *
 * IMPORTANTE — esto es SOLO para la UI (diálogo "Analizando con …"). La IA sigue
 * recibiendo el nombre de archivo real como `docNames`, porque el prompt de extracción
 * tiene una "Regla de nombres de archivo" que hace pattern-matching sobre patrones
 * como `censal*`, `036*`, `dni*`, `IMG_...`. Sustituir ese nombre por una etiqueta
 * traducida le quitaría a la IA esa señal. Por eso la traducción se aplica únicamente
 * en la frontera de presentación (WizardViewModel.onProviderStart).
 *
 * Detección basada en el nombre de archivo: es tan buena como lo sea el nombre. Un
 * fichero llamado literalmente "document.PDF" no se puede tipificar por su nombre y
 * cae al genérico "Documento". La detección por contenido (escanear el texto de la
 * primera página buscando "SITUACIÓN CENSAL", "CERTIFICA que la cuenta…", "PERMISO DE
 * RESIDENCIA"…) queda para una fase posterior, y solo cubriría PDFs con capa de texto
 * (no escaneos-imagen sin OCR).
 */
object DocumentTypeDetector {

    /** minúsculas, sin acentos, `_`/`-` → espacio, espacios colapsados. */
    private fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[_\\-]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /**
     * Reglas ordenadas: gana la PRIMERA cuyo patrón aparezca en el nombre normalizado.
     * El orden va de lo más específico a lo más genérico para evitar falsos positivos
     * (p. ej. "certificado bancario" no debe caer en una regla genérica de "certificado").
     */
    private val RULES: List<Pair<Regex, String>> = listOf(
        Regex("situacion censal|\\bcensal\\b")                              to "Certificado de situación censal",
        Regex("modelo *036|\\b036\\b")                                      to "Modelo 036",
        Regex("modelo *037|\\b037\\b")                                      to "Modelo 037",
        Regex("escritura")                                                  to "Escritura de constitución",
        Regex("titularidad|\\biban\\b|bancari|\\bcaixa|\\bbbva|santander|sabadell|bankinter|\\bing\\b|unicaja|abanca|kutxabank|cajamar")
                                                                            to "Certificado bancario",
        Regex("\\breta\\b|autonom|alta.*trabajador|resguardo.*alta")        to "Alta en RETA",
        Regex("arrendamiento|alquiler|\\blocal\\b|\\btienda\\b")            to "Contrato de alquiler",
        Regex("\\biae\\b")                                                  to "Certificado IAE",
        Regex("\\bnie\\b|\\btie\\b|permiso.*residen|residen.*permiso")      to "NIE / Permiso de residencia",
        Regex("pasaporte|passport")                                         to "Pasaporte",
        Regex("\\bdni\\b")                                                  to "DNI",
        Regex("tarjeta.*(cif|nif|fiscal)|\\bcif\\b|\\bnif\\b")              to "Tarjeta CIF/NIF",
        Regex("\\bfactura")                                                 to "Factura",
    )

    /** Nombres que no aportan tipo alguno → se muestran como "Documento". */
    private val GENERIC = Regex(
        "^(document|documento|documento \\d+|scan[ \\w]*|escaneo[ \\w]*|img[ \\d]*|" +
        "imagen[ \\w]*|foto[ \\d]*|file|archivo|adjunto|wa\\d+|whatsapp.*|screenshot.*|" +
        "captura.*|pdf|\\d{6,}|[a-f0-9]{8,})$"
    )

    /**
     * Devuelve un nombre legible del TIPO de documento para la UI.
     * Nunca devuelve el ID crudo SAF; ante la duda devuelve "Documento".
     */
    fun friendlyName(rawName: String?): String {
        val raw = rawName?.trim().orEmpty()
        if (raw.isEmpty()) return "Documento"

        // ID crudo del proveedor SAF (p. ej. "document:17077", "msf:42", "raw:...").
        if (raw.contains(':')) return "Documento"

        val stem = raw.substringBeforeLast('.', raw) // quita la extensión
        val n = norm(stem)
        if (n.isBlank()) return "Documento"

        RULES.firstOrNull { it.first.containsMatchIn(n) }?.let { return it.second }

        // Sin tipo reconocido: si el nombre es genérico o puro número → "Documento".
        if (GENERIC.matches(n) || n.matches(Regex("\\d+"))) return "Documento"

        // Nombre de archivo real no tipificado: límpialo y capitaliza para que al menos
        // se lea el nombre que le puso el usuario (mejor que "document:17077").
        return n.replaceFirstChar { it.uppercase() }
    }
}
