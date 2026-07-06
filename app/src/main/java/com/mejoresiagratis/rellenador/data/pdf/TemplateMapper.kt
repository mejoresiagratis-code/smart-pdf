package com.mejoresiagratis.rellenador.data.pdf

import com.mejoresiagratis.rellenador.data.model.ContractFields
import java.text.Normalizer
import javax.inject.Inject

/**
 * Empareja los nombres reales de campo de un PDF aportado por el usuario con las
 * claves canónicas de la app. Réplica de detectTemplate() de la web: normaliza
 * (acentos, mayúsculas, espacios múltiples) y puntúa la similitud.
 */
class TemplateMapper @Inject constructor() {

    data class Suggestion(
        val realField: String,        // nombre real en el PDF del usuario
        val canonicalKey: String?,    // clave canónica sugerida (o null si no hay match)
        val confidence: Float         // 0..1
    )

    /** norm(): minúsculas, sin acentos, espacios colapsados — como en la web. */
    fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")      // quita diacríticos
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Similitud simple: exacta normalizada > contiene > tokens en común. */
    private fun score(a: String, b: String): Float {
        val na = norm(a); val nb = norm(b)
        if (na == nb) return 1f
        if (na.isEmpty() || nb.isEmpty()) return 0f
        if (na.contains(nb) || nb.contains(na)) return 0.8f
        val ta = na.split(" ").toSet(); val tb = nb.split(" ").toSet()
        val inter = ta.intersect(tb).size
        val union = ta.union(tb).size
        return if (union == 0) 0f else 0.7f * inter / union
    }

    /**
     * Sugiere el mapeo para cada campo real del PDF. Un campo canónico no se
     * asigna dos veces (se queda con el de mayor confianza).
     */
    fun suggest(realFields: List<String>): List<Suggestion> {
        val canon = ContractFields.CANON.map { it.key }
        val used = mutableSetOf<String>()
        // ordenar por mejor match global para asignación greedy estable
        val ranked = realFields.map { rf ->
            val best = canon.map { ck -> ck to score(rf, ck) }.maxByOrNull { it.second }
            Triple(rf, best?.first, best?.second ?: 0f)
        }.sortedByDescending { it.third }

        val result = LinkedHashMap<String, Suggestion>()
        for ((rf, ck, conf) in ranked) {
            if (ck != null && conf >= 0.5f && ck !in used) {
                used.add(ck)
                result[rf] = Suggestion(rf, ck, conf)
            } else {
                result[rf] = Suggestion(rf, null, 0f)
            }
        }
        // preservar el orden original de realFields
        return realFields.map { result[it] ?: Suggestion(it, null, 0f) }
    }
}
