package com.mejoresiagratis.rellenador.data.remote

import com.mejoresiagratis.rellenador.data.model.AiExtraction
import kotlinx.serialization.json.Json

/**
 * La IA a veces envuelve el JSON en texto o ```; extraemos el primer objeto {...}
 * y lo parseamos con tolerancia, igual que parseAIJson() en la web.
 */
object AiJsonParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    fun parse(raw: String?): AiExtraction? {
        if (raw.isNullOrBlank()) return null
        val a = raw.indexOf('{')
        val b = raw.lastIndexOf('}')
        if (a < 0 || b <= a) return null
        return runCatching { json.decodeFromString<AiExtraction>(raw.substring(a, b + 1)) }.getOrNull()
    }
}
