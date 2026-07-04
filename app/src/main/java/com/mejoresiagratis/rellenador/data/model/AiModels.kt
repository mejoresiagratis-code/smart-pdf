package com.mejoresiagratis.rellenador.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The 9 providers your ai-proxy.php multiplexes. Active vs inactive is a
 * runtime concern (whether a key is stored), not a compile-time one.
 */
enum class AiProvider(val id: String, val displayName: String) {
    CLAUDE("claude", "Claude"),
    GEMINI("gemini", "Gemini"),
    GROQ("groq", "Groq"),
    MISTRAL("mistral", "Mistral"),
    EUROUTER("eurouter", "EUrouter"),
    GROK("grok", "Grok"),
    SCALEWAY("scaleway", "Scaleway"),
    OVHCLOUD("ovhcloud", "OVHcloud"),
    NEBIUS("nebius", "Nebius");

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id }
    }
}

/** Request sent to the PHP proxy. Keys never live in the app if you keep the proxy. */
@Serializable
data class ProxyRequest(
    val provider: String,
    val prompt: String,
    @SerialName("pdf_text") val pdfText: String? = null,
    val model: String? = null
)

@Serializable
data class ProxyResponse(
    val provider: String,
    val ok: Boolean,
    val json: Map<String, String>? = null,
    val error: String? = null
)

/** A single extracted contract field, keyed by the exact AcroForm field name. */
@Serializable
data class ExtractedField(
    val fieldName: String,
    val value: String,
    val confidence: Float = 1f,
    val source: String = ""
)
