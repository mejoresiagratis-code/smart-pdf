package com.mejoresiagratis.rellenador.data.remote

import com.mejoresiagratis.rellenador.data.model.*
import com.mejoresiagratis.rellenador.data.validation.FieldNormalizer
import javax.inject.Inject

/**
 * Orquesta la extracción multi-motor igual que la app web:
 * por cada documento y cada motor activo, llama al proxy, parsea el JSON y
 * agrega todas las propuestas por campo (sugerencias + alternativas + paquetes),
 * anotando qué motores propusieron cada valor.
 */
class MultiAiExtractor @Inject constructor(
    private val api: ProxyApi
) {
    data class Result(
        val proposals: List<FieldProposal>,
        val tipoIdentificacion: String?,
        val packages: List<Paquete>,
        val enginesOk: Set<String>,
        val errors: List<String>
    )

    suspend fun extract(
        docs: List<DocPayload>,
        enabled: List<AiProvider>,
        geminiMode: String = "g35"
    ): Result {
        val prompt = ExtractionPrompt.build()
        // acumulador: campo -> (valor -> fuentes)
        val agg = LinkedHashMap<String, LinkedHashMap<String, MutableSet<String>>>()
        val errors = mutableListOf<String>()
        val enginesOk = linkedSetOf<String>()
        val allPackages = mutableListOf<Paquete>()
        var tipoId: String? = null

        fun track(key: String, value: String?, engine: String) {
            val v = FieldNormalizer.normVal(key, value)
            if (v.isEmpty()) return
            agg.getOrPut(key) { LinkedHashMap() }.getOrPut(v) { mutableSetOf() }.add(engine)
        }

        docs.forEachIndexed { i, doc ->
            enabled.forEach { provider ->
                val req = ProxyRequest(
                    provider = provider.id, prompt = prompt, task = "extract",
                    maxTokens = 2048, seq = i, geminiMode = geminiMode, docs = listOf(doc)
                )
                val resp = runCatching { api.call(req) }.getOrElse {
                    errors.add("${provider.displayName}: ${it.message}"); return@forEach
                }
                if (!resp.ok) { errors.add("${provider.displayName}: ${resp.error ?: "error"}"); return@forEach }

                var ex = AiJsonParser.parse(resp.text) ?: run {
                    errors.add("${provider.displayName}: respuesta incompleta"); return@forEach
                }
                // Groq (texto plano) especula: quedarse solo con sugerencias.
                if (provider == AiProvider.GROQ) ex = ex.copy(alternativas = emptyMap(), paquetes = emptyList())

                enginesOk.add(provider.displayName)
                tipoId = tipoId ?: ex.tipo_identificacion
                ex.sugerencias.forEach { (k, v) -> track(k, v, provider.displayName) }
                ex.alternativas.forEach { (k, list) -> list.forEach { track(k, it.valor, provider.displayName) } }
                ex.paquetes.forEach { pk ->
                    allPackages.add(pk)
                    pk.datos.forEach { (k, v) -> track(k, v, provider.displayName) }
                }
            }
        }

        val proposals = agg.map { (key, values) ->
            FieldProposal(
                fieldKey = key,
                candidates = values.map { (v, srcs) -> Candidate(v, srcs.toSet()) }
                    .sortedByDescending { it.sources.size }   // consenso primero
            )
        }
        return Result(proposals, tipoId, allPackages, enginesOk, errors)
    }
}
