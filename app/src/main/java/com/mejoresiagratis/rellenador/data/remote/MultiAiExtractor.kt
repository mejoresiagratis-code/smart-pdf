package com.mejoresiagratis.rellenador.data.remote

import com.mejoresiagratis.rellenador.data.model.AiProvider
import com.mejoresiagratis.rellenador.data.model.ExtractedField
import com.mejoresiagratis.rellenador.data.model.ProxyRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

/**
 * Fan-out extraction across enabled providers, then merge by field name.
 * Merge strategy = highest confidence wins; ties broken by provider order.
 * This is the Kotlin analogue of the browser-side multi-motor loop.
 */
class MultiAiExtractor @Inject constructor(
    private val api: ProxyApi
) {
    suspend fun extract(
        pdfText: String,
        prompt: String,
        enabled: List<AiProvider>
    ): Map<String, ExtractedField> = coroutineScope {
        val results = enabled.map { provider ->
            async {
                runCatching {
                    api.extract(
                        ProxyRequest(
                            provider = provider.id,
                            prompt = prompt,
                            pdfText = pdfText
                        )
                    )
                }.getOrNull()
            }
        }.mapNotNull { it.await() }

        val merged = LinkedHashMap<String, ExtractedField>()
        for (res in results) {
            if (!res.ok || res.json == null) continue
            for ((name, value) in res.json) {
                val candidate = ExtractedField(name, value, source = res.provider)
                val existing = merged[name]
                if (existing == null || candidate.confidence > existing.confidence) {
                    merged[name] = candidate
                }
            }
        }
        merged
    }
}
