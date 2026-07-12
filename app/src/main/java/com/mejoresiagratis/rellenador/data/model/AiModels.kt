package com.mejoresiagratis.rellenador.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Los 9 motores que multiplexa ai-proxy.php.
 *
 * Tanda 2 — identidad visual por proveedor:
 * - `brandColor`: color oficial de marca (Long ARGB) para el chip, halo del logo y
 *   glyph fallback. Son colores públicos de brand guidelines, no reproducción de logo.
 * - `initial`: 1–2 caracteres para el glyph fallback cuando el drawable oficial no
 *   está disponible (o cuando se prefiere identidad neutra).
 * - `drawableName`: nombre del recurso PNG/SVG en `res/drawable` (sin extensión ni
 *   prefijo `R.drawable.`). El drawable existe siempre como placeholder desde el ZIP
 *   de la Tanda 2 y se puede sustituir por el logo oficial más tarde sin tocar código.
 */
enum class AiProvider(
    val id: String,
    val displayName: String,
    val eu: Boolean = false,
    val brandColor: Long,
    val initial: String,
    val drawableName: String
) {
    CLAUDE  ("claude",   "Claude",    brandColor = 0xFFCC785C, initial = "C",  drawableName = "ic_provider_claude"),
    GEMINI  ("gemini",   "Gemini",    brandColor = 0xFF1A73E8, initial = "G",  drawableName = "ic_provider_gemini"),
    GROQ    ("groq",     "Groq",      brandColor = 0xFFF55036, initial = "Gq", drawableName = "ic_provider_groq"),
    GROK    ("grok",     "Grok",      brandColor = 0xFF000000, initial = "X",  drawableName = "ic_provider_grok"),
    MISTRAL ("mistral",  "Mistral",   eu = true, brandColor = 0xFFFA500A, initial = "M",  drawableName = "ic_provider_mistral"),
    SCALEWAY("scaleway", "Scaleway",  eu = true, brandColor = 0xFF4F0599, initial = "S",  drawableName = "ic_provider_scaleway"),
    OVH     ("ovh",      "OVHcloud",  eu = true, brandColor = 0xFF123F6D, initial = "O",  drawableName = "ic_provider_ovh"),
    NEBIUS  ("nebius",   "Nebius",    eu = true, brandColor = 0xFF16A34A, initial = "N",  drawableName = "ic_provider_nebius"),
    EUROUTER("eurouter", "EUrouter",  eu = true, brandColor = 0xFF003399, initial = "EU", drawableName = "ic_provider_eurouter");

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
