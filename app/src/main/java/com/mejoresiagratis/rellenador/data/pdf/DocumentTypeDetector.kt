package com.mejoresiagratis.rellenador.data.pdf

import java.text.Normalizer

/**
 * Detecta el TIPO de documento a partir de su CONTENIDO (el texto de las primeras
 * páginas), no del nombre de archivo. Devuelve un nombre legible para la UI ("Certificado
 * de situación censal", "Tarjeta CIF/NIF"…). El nombre del fichero es irrelevante aquí: da
 * igual que llegue como `DOC-20260716-WA0015.PDF` — lo que decide es lo que dice dentro.
 *
 * COBERTURA: PDFs con capa de texto (todo el papeleo oficial generado digitalmente:
 * censal, tarjeta NIF, Modelo 036, certificado bancario, contrato, RETA…). Los documentos
 * SIN texto (fotos de DNI/NIE en jpg/png, o PDFs que son solo un escaneo-imagen) no se
 * pueden tipificar aquí sin OCR → devuelven "Documento". Para tipificarlos también haría
 * falta que la propia IA (visión) devuelva el tipo (fase posterior, requiere tocar el
 * prompt y replicarlo en la web).
 */
object DocumentTypeDetector {

    /** minúsculas, sin acentos, espacios colapsados. */
    private fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    /**
     * Reglas ordenadas: gana la PRIMERA cuyo patrón aparezca en el texto normalizado.
     * El orden importa para evitar cruces (p. ej. el Modelo 036 menciona "tarjeta
     * acreditativa" como una casilla; un contrato o un alta RETA mencionan un IBAN). Por
     * eso los patrones son frases-firma específicas del documento, no palabras sueltas.
     */
    private val RULES: List<Pair<Regex, String>> = listOf(
        Regex("certificado de situacion censal|situacion en el censo de actividades")   to "Certificado de situación censal",
        Regex("modelo 036|modelo 037|declaracion censal de alta")                       to "Modelo 036",
        Regex("tarjeta de identificacion fiscal|comunicacion de tarjeta acreditativa")  to "Tarjeta CIF/NIF",
        Regex("regimen especial de trabajo autonomo")                                   to "Alta en RETA",
        Regex("contrato de arrendamiento|arrendamiento para uso distinto")              to "Contrato de alquiler",
        Regex("escritura de constitucion|numero de protocolo|otorgo ante mi")           to "Escritura de constitución",
        Regex("solicitud de datos codigo cuenta|codigo cuenta \\(iban\\)|\\bes titularidad\\b|bic ?[/(]? ?(codigo )?swift") to "Certificado bancario",
        Regex("permiso de residencia|titre de sejour|tarjeta de identidad de extranjero") to "NIE / Permiso de residencia",
        Regex("documento nacional de identidad")                                        to "DNI",
        Regex("\\bpasaporte\\b|\\bpassport\\b")                                          to "Pasaporte",
    )

    /**
     * Devuelve el TIPO del documento a partir del texto de su contenido.
     * Texto vacío (sin capa de texto) o sin coincidencia → "Documento".
     */
    fun fromContent(text: String?): String {
        val n = norm(text.orEmpty())
        if (n.isBlank()) return "Documento"
        RULES.firstOrNull { it.first.containsMatchIn(n) }?.let { return it.second }
        return "Documento"
    }
}
