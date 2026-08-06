package com.mejoresiagratis.rellenador.data.model

/**
 * Traduce el error crudo de una API a una causa que el usuario entienda (v0.9.0).
 *
 * Los mensajes que devuelven los proveedores son inservibles para un comercial en la
 * calle: `HTTP 429 — {"error":{"code":429,"message":"Resource has been exhausted
 * (e.g. check quota)."}}`. Lo único que necesita saber es si esperar, si tocar una clave
 * o si el documento era demasiado grande.
 *
 * Portado de `shortCause()` de la app web, que ya resolvía esto igual.
 */
enum class EngineFailure(val label: String, val hint: String) {

    QUOTA(
        "límite o cuota alcanzada",
        "Vuelve a intentarlo en unos minutos o usa otro motor."
    ),
    AUTH(
        "clave no válida o sin permiso",
        "Revisa la clave de ese motor en la configuración del proxy."
    ),
    TOO_LARGE(
        "documento demasiado grande",
        "Ese motor no admite un documento de ese tamaño; los demás sí lo han analizado."
    ),
    NETWORK(
        "problema de red",
        "Comprueba la conexión. Si estás en WiFi, prueba con datos móviles."
    ),
    PARSE(
        "respuesta incompleta",
        "El motor cortó la respuesta. Suele arreglarse repitiendo el análisis."
    ),
    UNAVAILABLE(
        "servicio no disponible",
        "Es un fallo temporal del proveedor, no de la app."
    ),
    OTHER(
        "error",
        "Si se repite, prueba a analizar con otro motor."
    );

    companion object {
        /**
         * Clasifica por el mensaje crudo. El orden importa: se comprueban primero las
         * causas específicas, porque un mensaje de cuota también suele traer un código
         * numérico que encajaría en varias reglas.
         */
        fun from(raw: String): EngineFailure {
            val m = raw.lowercase()
            return when {
                Regex("quota|rate.?limit|429|resource has been exhausted|exhausted").containsMatchIn(m) -> QUOTA
                Regex("\\b401\\b|\\b403\\b|unauthor|api key|invalid.*key|permission|credential").containsMatchIn(m) -> AUTH
                Regex("\\b413\\b|too large|payload|entity too large").containsMatchIn(m) -> TOO_LARGE
                Regex("timeout|timed out|network|unable to resolve|failed to connect|econn|unknownhost").containsMatchIn(m) -> NETWORK
                Regex("json|parse|syntax|incompleta|truncat|unexpected end").containsMatchIn(m) -> PARSE
                Regex("\\b50[0234]\\b|unavailable|overload|internal error").containsMatchIn(m) -> UNAVAILABLE
                else -> OTHER
            }
        }
    }
}

/**
 * Un motor que no participó, con su causa ya interpretada.
 * `detail` conserva el mensaje crudo por si hace falta diagnosticar de verdad.
 */
data class EngineIssue(
    val engine: String,
    val failure: EngineFailure,
    val detail: String,
) {
    /** Línea corta para la lista: «Gemini · límite o cuota alcanzada». */
    val summary: String get() = "$engine · ${failure.label}"
}
