package com.mejoresiagratis.rellenador.data.remote

import android.util.Log
import com.mejoresiagratis.rellenador.data.model.AiProvider
import com.mejoresiagratis.rellenador.data.model.DocPayload
import com.mejoresiagratis.rellenador.data.model.ProxyRequest
import com.mejoresiagratis.rellenador.data.model.SignatureBox
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import javax.inject.Inject

/**
 * Localiza la firma manuscrita en una imagen usando la tarea "locate_signature"
 * del proxy. Prueba motores con visión en el orden de preferencia de la web.
 */
class SignatureLocator @Inject constructor(
    private val api: ProxyApi
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val PROMPT =
        "Localiza la FIRMA MANUSCRITA. Responde SOLO JSON: " +
        "{\"x\":<%>,\"y\":<%>,\"w\":<%>,\"h\":<%>} (0-100). " +
        "Si toda la imagen es la firma: {\"x\":0,\"y\":0,\"w\":100,\"h\":100}."

    // Orden fiable (0.2.1): Claude/Gemini pueden estar caídos según el día, y Groq no
    // tiene visión real (motor de texto que "especula" el JSON) — se excluye del todo.
    private val order = listOf(
        AiProvider.MISTRAL, AiProvider.SCALEWAY, AiProvider.CLAUDE, AiProvider.GEMINI, AiProvider.GROK
    )

    private fun parseBox(raw: String?): SignatureBox? {
        if (raw.isNullOrBlank()) return null
        val a = raw.indexOf('{'); val b = raw.lastIndexOf('}')
        if (a < 0 || b <= a) return null
        return runCatching { json.decodeFromString<SignatureBox>(raw.substring(a, b + 1)) }
            .getOrNull()?.takeIf { it.valid }
    }

    /** Devuelve la caja (0..100) o null si ningún motor la localizó. */
    suspend fun locate(imageB64: String, available: List<AiProvider>): SignatureBox? {
        for (p in order) {
            if (p !in available) continue
            val req = ProxyRequest(
                provider = p.id, prompt = PROMPT, task = "locate_signature",
                maxTokens = 300, docs = listOf(DocPayload(mime = "image/jpeg", b64 = imageB64))
            )
            val resp = try {
                api.call(req)
            } catch (e: HttpException) {
                Log.w("SignatureLocator", "${p.displayName} HTTP ${e.code()}: ${e.realErrorMessage() ?: "(sin body legible)"}")
                continue
            } catch (e: Exception) {
                Log.w("SignatureLocator", "${p.displayName} exception: ${e.message}")
                continue
            }
            if (!resp.ok) continue
            parseBox(resp.text)?.let { return it }
        }
        return null
    }
}
