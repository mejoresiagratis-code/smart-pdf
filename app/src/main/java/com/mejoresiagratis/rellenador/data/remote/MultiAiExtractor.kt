package com.mejoresiagratis.rellenador.data.remote

import android.util.Log
import com.mejoresiagratis.rellenador.data.model.*
import com.mejoresiagratis.rellenador.data.validation.FieldNormalizer
import kotlinx.coroutines.delay
import retrofit2.HttpException
import javax.inject.Inject

/**
 * Orquesta la extracción multi-motor igual que la app web, con dos protecciones
 * adicionales que la web no necesitaba porque allí cada motor se llama en un
 * botón separado:
 *
 *  1. **Short-circuit por motor caído**. Si un motor devuelve 4xx (que no sea
 *     429), o lanza excepción, o el proxy responde `ok=false`, se marca como
 *     muerto para el resto de la sesión de extracción y no se vuelve a llamar
 *     en los siguientes documentos. Esto evita repetir un fallo N veces y
 *     quemar la cuota `RATE_MAX=30/10min` del proxy PHP.
 *
 *  2. **Backoff cooperativo en 429**. Si algún motor devuelve 429, se hace una
 *     pausa antes de seguir con el resto de motores, dejando que la ventana
 *     del rate limiter del proxy se libere un poco.
 *
 * Además, los motores se llaman en un orden que prioriza los que suelen ir
 * bien (Groq, Mistral, Claude) para maximizar cobertura antes de que un motor
 * problemático (que devuelve 500/404) intente y consuma tiempo/cupo.
 */
class MultiAiExtractor @Inject constructor(
    private val api: ProxyApi
) {
    data class Result(
        val proposals: List<FieldProposal>,
        val tipoIdentificacion: String?,
        val packages: List<Paquete>,
        val enginesOk: Set<String>,
        /** Compatibilidad: lista plana de "Motor: estado". */
        val errors: List<String>,
        /** Mismo contenido que `errors`, como mapa para el panel de detalles del UI. */
        val engineFailures: Map<String, String>
    )

    /** Orden de prioridad. Los motores más fiables primero. */
    private fun sortForCall(enabled: List<AiProvider>): List<AiProvider> {
        val priority = listOf(
            AiProvider.GROQ,      // texto plano, rápido, rara vez falla
            AiProvider.MISTRAL,   // visión estable
            AiProvider.CLAUDE,    // visión buena pero cara — cortar antes si falla
            AiProvider.GEMINI,    // visión buena pero 3.x da problemas de thinking
            AiProvider.SCALEWAY,  // EU, estable en general
            AiProvider.EUROUTER,  // ha dado 404 en el pasado, dejarlo tarde
            AiProvider.OVH,
            AiProvider.NEBIUS,
            AiProvider.GROK
        )
        val known = priority.filter { it in enabled }
        val rest = enabled.filter { it !in known }
        return known + rest
    }

    suspend fun extract(
        docs: List<DocPayload>,
        enabled: List<AiProvider>,
        geminiMode: String = "g35",
        earlyStop: Boolean = true
    ): Result {
        val prompt = ExtractionPrompt.build()
        // acumulador: campo -> (valor -> fuentes)
        val agg = LinkedHashMap<String, LinkedHashMap<String, MutableSet<String>>>()
        val perProviderStatus = LinkedHashMap<String, String>()  // último estado por motor
        val enginesOk = linkedSetOf<String>()
        val allPackages = mutableListOf<Paquete>()
        val tipoVotes = LinkedHashMap<String, Int>()   // votación de tipo (fiel a la web)

        // Motores marcados como caídos en esta sesión: no se vuelven a llamar.
        val dead = mutableSetOf<AiProvider>()

        fun track(key: String, value: String?, engine: String) {
            val v = FieldNormalizer.normVal(key, value)
            if (v.isEmpty()) return
            agg.getOrPut(key) { LinkedHashMap() }.getOrPut(v) { mutableSetOf() }.add(engine)
        }

        // Campos de texto canónicos que cuentan para "cobertura" (excluye fechas y responsable).
        val coverageKeys = ContractFields.CANON
            .map { it.key }
            .filterNot { it in setOf("Fecha", "de", "año") }

        fun allCovered(): Boolean = coverageKeys.all { agg[it]?.isNotEmpty() == true }

        val orderedProviders = sortForCall(enabled)

        run docsLoop@{
            docs.forEachIndexed { i, doc ->
                for (provider in orderedProviders) {
                    if (provider in dead) continue

                    val req = ProxyRequest(
                        provider = provider.id, prompt = prompt, task = "extract",
                        maxTokens = 2048, seq = i, geminiMode = geminiMode, docs = listOf(doc)
                    )

                    val resp = try {
                        api.call(req)
                    } catch (e: HttpException) {
                        val code = e.code()
                        val label = provider.displayName
                        Log.w("MultiAiExtractor", "$label HTTP $code doc=$i", e)
                        when {
                            code == 429 -> {
                                // Cuota del proxy o del upstream. Pausa cooperativa antes
                                // de seguir; NO marcamos el motor como muerto porque
                                // podría recuperarse tras la pausa.
                                perProviderStatus[label] = "HTTP 429 (rate limit)"
                                delay(2500L)
                            }
                            code in 400..499 -> {
                                // 400/401/403/404: el motor no puede responder a esta
                                // petición. Marcar como muerto — no reintentar.
                                perProviderStatus[label] = "HTTP $code (no disponible)"
                                dead += provider
                            }
                            code in 500..599 -> {
                                // 500/502/503: fallo del upstream. En la web se reintenta,
                                // pero aquí (batería, cuota) preferimos cortar.
                                perProviderStatus[label] = "HTTP $code (error del motor)"
                                dead += provider
                            }
                            else -> {
                                perProviderStatus[label] = "HTTP $code"
                                dead += provider
                            }
                        }
                        null
                    } catch (e: Exception) {
                        val label = provider.displayName
                        Log.w("MultiAiExtractor", "$label exception doc=$i", e)
                        perProviderStatus[label] = "sin red / timeout"
                        dead += provider
                        null
                    }

                    if (resp == null) continue

                    if (!resp.ok) {
                        val label = provider.displayName
                        val msg = resp.error?.take(80) ?: "error"
                        Log.w("MultiAiExtractor", "$label ok=false: $msg")
                        perProviderStatus[label] = "proxy: $msg"
                        dead += provider   // no reintentar este motor
                        continue
                    }

                    val parsed = AiJsonParser.parse(resp.text)
                    if (parsed == null) {
                        val label = provider.displayName
                        perProviderStatus[label] = "respuesta incompleta"
                        // No lo marcamos como muerto porque podría acertar en otro doc,
                        // pero contamos la incidencia.
                        continue
                    }

                    // Groq (texto plano) especula: quedarse solo con sugerencias.
                    val ex = if (provider == AiProvider.GROQ)
                        parsed.copy(alternativas = emptyMap(), paquetes = emptyList()) else parsed

                    enginesOk.add(provider.displayName)
                    perProviderStatus.remove(provider.displayName)  // limpiar si acertó
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

                // Si TODOS los motores están muertos, no tiene sentido seguir con más docs.
                if (dead.size >= orderedProviders.size) return@docsLoop
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

        // Errores agrupados: una línea por motor con problema, en vez de N líneas repetidas.
        val errors = perProviderStatus.map { (motor, estado) -> "$motor: $estado" }

        return Result(
            proposals = proposals,
            tipoIdentificacion = tipoId,
            packages = allPackages,
            enginesOk = enginesOk,
            errors = errors,
            engineFailures = perProviderStatus.toMap()
        )
    }
}
