package com.mejoresiagratis.rellenador.data.remote

import com.mejoresiagratis.rellenador.data.model.*
import com.mejoresiagratis.rellenador.data.validation.FieldNormalizer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import javax.inject.Inject

/**
 * Extrae el mensaje real de ai-proxy.php (que a su vez reenvía el de Google/Anthropic/etc.,
 * ej. "Gemini: <mensaje>") del body de un HttpException. El proxy SIEMPRE manda
 * {"ok":false,"error":"..."} como JSON en el body, incluso en 400/404/500 — pero Retrofit,
 * al ver un código no-2xx, no lo deserializa como ProxyResponse; hay que leerlo a mano de
 * errorBody(). Sin esto, el banner de error solo puede mostrar "HTTP 500" pelado (el
 * e.message por defecto de Retrofit), perdiendo el motivo real (quota, modelo, argumento
 * inválido, etc.) que Google/Anthropic sí mandaron.
 */
private val diagJson = Json { ignoreUnknownKeys = true; isLenient = true }
fun HttpException.realErrorMessage(): String? {
    val raw = runCatching { response()?.errorBody()?.string() }.getOrNull()
        ?.takeIf { it.isNotBlank() } ?: return null
    val parsed = runCatching {
        diagJson.parseToJsonElement(raw).jsonObject["error"]?.jsonPrimitive?.content
    }.getOrNull()
    return parsed ?: raw.take(200)   // si no es el JSON esperado, mostrar el body crudo (recortado)
}

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

    /** Orden fiable (0.2.0): prioriza los motores que suelen ir bien. */
    private val priorityOrder = listOf(
        AiProvider.GROQ, AiProvider.MISTRAL, AiProvider.CLAUDE,
        AiProvider.GEMINI, AiProvider.SCALEWAY, AiProvider.EUROUTER
    )

    // Debe coincidir con MAX_DOCS en ai-proxy.php (actualmente 20). Si un archivo tiene
    // más páginas que esto, se trocea en sub-lotes en vez de mandarlas todas juntas —
    // el proxy trunca en SILENCIO cualquier exceso (array_slice), así que superarlo sin
    // trocear perdería páginas sin ningún aviso. Ver comentario en ai-proxy.php.
    private val MAX_PAGES_PER_CALL = 20

    suspend fun extract(
        // Un grupo = todas las páginas/payloads de UN mismo archivo aportado por el
        // usuario. jul 2026: antes se aplanaba todo a una lista de páginas sueltas y se
        // hacía UNA llamada por página — para un PDF de 13 páginas, hasta 13x más
        // peticiones de red por motor que la app web (que manda el PDF entero como un
        // único "doc" por llamada). Ahora se manda TODO un archivo en una sola llamada
        // por motor (varias imágenes en el mismo `docs` de ProxyRequest, que el proxy ya
        // soportaba desde siempre), troceando solo si excede MAX_PAGES_PER_CALL.
        docGroups: List<List<DocPayload>>,
        enabled: List<AiProvider>,
        geminiMode: String = "g35",
        earlyStop: Boolean = true,
        // Un nombre por GRUPO (archivo), no por página — ya no hace falta desambiguar
        // "(pág. N/M)" porque cada llamada ya cubre el archivo entero.
        docNames: List<String> = emptyList(),
        // Tanda 2 — callbacks opcionales para reflejar en la UI qué motor está
        // trabajando ahora mismo (chip activo + MotorLoadingIndicator). Defaults
        // no-op: no cambia el comportamiento de ningún llamador existente.
        onProviderStart: (docLabel: String, provider: AiProvider) -> Unit = { _, _ -> },
        onProviderFinish: (AiProvider) -> Unit = {},
        // Progreso agregado (documento × motor) para la barra de la Propuesta 2+3:
        // `current` es 1-based, `total` es el techo teórico (docs × motores activos) —
        // con earlyStop puede quedarse corto del final, lo cual es correcto: si se para
        // antes es porque ya no hacía falta seguir, no porque algo fallara.
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Result {
        // Trocea cualquier archivo que exceda MAX_PAGES_PER_CALL en sub-lotes, cada uno
        // con su propia etiqueta "nombre (parte X/Y)" — caso raro (contratos/escrituras
        // de más de 20 páginas), pero sin esto se perderían páginas en silencio.
        val chunkedGroups = mutableListOf<List<DocPayload>>()
        val chunkedNames = mutableListOf<String>()
        docGroups.forEachIndexed { gi, group ->
            val label = docNames.getOrNull(gi) ?: "documento ${gi + 1}"
            if (group.size <= MAX_PAGES_PER_CALL) {
                chunkedGroups.add(group); chunkedNames.add(label)
            } else {
                val parts = group.chunked(MAX_PAGES_PER_CALL)
                parts.forEachIndexed { pi, part ->
                    chunkedGroups.add(part)
                    chunkedNames.add("$label (parte ${pi + 1}/${parts.size})")
                }
            }
        }
        // Nombres de archivo del conjunto para el contexto del prompt (CIF+DNI en
        // documentos separados, etc.) — ya no hace falta desambiguar sufijos de página,
        // cada nombre en docNames ya corresponde 1:1 a un archivo real.
        val prompt = ExtractionPrompt.build(contextDocNames = docNames.distinct())
        // acumulador: campo -> (valor -> fuentes)
        val agg = LinkedHashMap<String, LinkedHashMap<String, MutableSet<String>>>()
        val perProviderStatus = LinkedHashMap<String, String>()  // último estado por motor (agrupado)
        val enginesOk = linkedSetOf<String>()
        val allPackages = mutableListOf<Paquete>()
        val tipoVotes = LinkedHashMap<String, Int>()   // votación de tipo (fiel a la web)
        val dead = mutableSetOf<AiProvider>()          // motores que ya fallaron esta sesión (short-circuit)

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

        // Orden fiable: los de priorityOrder primero (en ese orden), el resto detrás.
        val orderedEnabled = enabled.sortedBy { p ->
            priorityOrder.indexOf(p).let { if (it < 0) priorityOrder.size else it }
        }
        val totalSteps = chunkedGroups.size * orderedEnabled.size
        var stepCounter = 0

        run docsLoop@{
            chunkedGroups.forEachIndexed { i, group ->
                val docLabel = chunkedNames.getOrNull(i) ?: "documento ${i + 1}"
                for (provider in orderedEnabled) {
                    if (provider in dead) continue   // short-circuit: no reintentar un motor ya roto
                    onProviderStart(docLabel, provider)
                    val req = ProxyRequest(
                        provider = provider.id, prompt = prompt, task = "extract",
                        maxTokens = 4096, seq = i, geminiMode = geminiMode, docs = group
                    )
                    val resp = try {
                        api.call(req)
                    } catch (e: HttpException) {
                        val real = e.realErrorMessage()
                        perProviderStatus[provider.displayName] = "HTTP ${e.code()}" + (real?.let { " — $it" } ?: "")
                        // Nota: antes había un delay(2500) aquí para el caso 429, pero
                        // dead.add() se ejecuta justo arriba y es permanente para el resto
                        // de esta extracción (no hay reintento posterior a este motor en
                        // ningún documento siguiente) — el delay no servía para nada salvo
                        // alargar la espera del usuario sin ningún beneficio. Eliminado.
                        dead.add(provider)
                        null
                    } catch (e: Exception) {
                        perProviderStatus[provider.displayName] = "${e.message}"
                        dead.add(provider)
                        null
                    } finally {
                        onProviderFinish(provider)
                        onProgress(++stepCounter, totalSteps)
                    }
                    if (resp == null) continue
                    if (!resp.ok) {
                        perProviderStatus[provider.displayName] = resp.error ?: "error"
                        dead.add(provider)
                        continue
                    }

                    val parsed = AiJsonParser.parse(resp.text)
                    if (parsed == null) {
                        // Diagnóstico real (mismo principio que realErrorMessage() para
                        // errores HTTP): mostrar un fragmento de lo que el motor mandó de
                        // verdad, en vez de solo "no se pudo parsear". Sin esto, "respuesta
                        // incompleta" no dice si el motor cortó el JSON a medias, devolvió
                        // texto plano, un mensaje de error propio, o algo con formato
                        // inesperado — cada causa necesita un fix distinto.
                        val snippet = resp.text?.trim()?.take(180)?.replace("\n", " ") ?: "(vacío)"
                        perProviderStatus[provider.displayName] = "respuesta no parseable — \"$snippet\""
                        continue
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
        // Errores agrupados por motor (una línea por motor, no una por documento).
        val errors = perProviderStatus.map { (name, status) -> "$name: $status" }
        return Result(proposals, tipoId, allPackages, enginesOk, errors)
    }
}
