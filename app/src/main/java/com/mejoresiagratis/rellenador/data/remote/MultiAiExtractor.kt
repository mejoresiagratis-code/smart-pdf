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
        geminiMode: String = "g35",
        earlyStop: Boolean = true
    ): Result {
        val prompt = ExtractionPrompt.build()
        // acumulador: campo -> (valor -> fuentes)
        val agg = LinkedHashMap<String, LinkedHashMap<String, MutableSet<String>>>()
        val errors = mutableListOf<String>()
        val enginesOk = linkedSetOf<String>()
        val allPackages = mutableListOf<Paquete>()
        val tipoVotes = LinkedHashMap<String, Int>()   // votación de tipo (fiel a la web)

        fun track(key: String, value: String?, engine: String) {
            val v = FieldNormalizer.normVal(key, value)
            if (v.isEmpty()) return
            agg.getOrPut(key) { LinkedHashMap() }.getOrPut(v) { mutableSetOf() }.add(engine)
        }

        // Campos de texto canónicos que cuentan para "cobertura" (excluye fechas y responsable).
        val coverageKeys = com.mejoresiagratis.rellenador.data.model.ContractFields.CANON
            .map { it.key }
            .filterNot { it in setOf("Fecha", "de", "año") }

        fun allCovered(): Boolean = coverageKeys.all { agg[it]?.isNotEmpty() == true }

        run docsLoop@{
            docs.forEachIndexed { i, doc ->
                for (provider in enabled) {
                    val req = ProxyRequest(
                        provider = provider.id, prompt = prompt, task = "extract",
                        maxTokens = 2048, seq = i, geminiMode = geminiMode, docs = listOf(doc)
                    )
                    val resp = try {
                        api.call(req)
                    } catch (e: Exception) {
                        errors.add("${provider.displayName}: ${e.message}"); null
                    }
                    if (resp == null) continue
                    if (!resp.ok) { errors.add("${provider.displayName}: ${resp.error ?: "error"}"); continue }

                    val parsed = AiJsonParser.parse(resp.text)
                    if (parsed == null) {
                        errors.add("${provider.displayName}: respuesta incompleta"); continue
                    }
                    // Groq (texto plano) especula: quedarse solo con sugerencias.
                    val ex = if (provider == AiProvider.GROQ)
                        parsed.copy(alternativas = emptyMap(), paquetes = emptyList()) else parsed

                    enginesOk.add(provider.displayName)
                    ex.tipo_identificacion?.let { tipoVotes[it] = (tipoVotes[it] ?: 0) + 1 }
                    ex.sugerencias.forEach { (k, v) -> track(k, v, provider.displayName) }
                    ex.alternativas.forEach { (k, list) -> list.forEach { track(k, it.valor, provider.displayName) } }
                    ex.paquetes.forEach { pk ->
                        allPackages.add(pk)
                        pk.datos.forEach { (k, v) -> track(k, v, provider.displayName) }
                    }

                    // Corte inteligente (fiel a allFieldsCovered de la web): si ya está todo
                    // cubierto, no seguir gastando llamadas a más motores/documentos.
                    if (earlyStop && allCovered()) return@docsLoop
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
        val tipoId = tipoVotes.maxByOrNull { it.value }?.key   // mayoría
        return Result(proposals, tipoId, allPackages, enginesOk, errors)
    }
}
