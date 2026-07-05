package com.mejoresiagratis.rellenador.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Los 9 motores que multiplexa ai-proxy.php. */
enum class AiProvider(val id: String, val displayName: String, val eu: Boolean = false) {
    CLAUDE("claude", "Claude"),
    GEMINI("gemini", "Gemini"),
    GROQ("groq", "Groq"),
    GROK("grok", "Grok"),
    MISTRAL("mistral", "Mistral", eu = true),
    SCALEWAY("scaleway", "Scaleway", eu = true),
    OVH("ovh", "OVHcloud", eu = true),
    NEBIUS("nebius", "Nebius", eu = true),
    EUROUTER("eurouter", "EUrouter", eu = true);

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id }
    }
}

/** Un documento a analizar: imagen (foto/escaneo) o PDF, en base64; o texto plano. */
@Serializable
data class DocPayload(
    val mime: String,              // "image/jpeg", "application/pdf", "text/plain"
    val b64: String? = null,
    val text: String? = null
)

/** Petición al proxy — coincide EXACTAMENTE con ai-proxy.php. */
@Serializable
data class ProxyRequest(
    val provider: String,
    val prompt: String,
    val task: String = "extract",           // "extract" | "locate_signature"
    @SerialName("max_tokens") val maxTokens: Int = 2048,
    val seq: Int = 0,
    @SerialName("gemini_mode") val geminiMode: String = "g35",
    val docs: List<DocPayload> = emptyList()
)

/** Respuesta del proxy: {ok, provider, text} o {ok:false, error}. */
@Serializable
data class ProxyResponse(
    val ok: Boolean,
    val provider: String? = null,
    val text: String? = null,
    val error: String? = null
)

/** GET del proxy: qué motores tienen clave en servidor. */
@Serializable
data class ProxyProviders(
    val ok: Boolean = false,
    val providers: Map<String, Boolean> = emptyMap()
)
