package com.mejoresiagratis.rellenador.data.remote

import android.util.Log
import com.mejoresiagratis.rellenador.data.model.AiProvider
import com.mejoresiagratis.rellenador.data.model.CanonicalCatalog
import com.mejoresiagratis.rellenador.data.model.FieldKind
import com.mejoresiagratis.rellenador.data.model.FormSchema
import com.mejoresiagratis.rellenador.data.model.ProxyRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import javax.inject.Inject

/** Respuesta cruda: `nombre real del campo -> clave canónica`. */
@Serializable
data class CanonicalProposal(
    val enganches: Map<String, String> = emptyMap(),
)

/**
 * Tanda 5·4g — **propuesta de canónicas por IA**.
 *
 * Etiquetar (fase 3) responde «qué se escribe en este hueco» leyendo el rótulo impreso. Esto
 * responde la pregunta siguiente, que es la que faltaba: «y ese hueco, ¿a cuál de MIS datos
 * transversales corresponde?». Sin `FormField.canonical` no hay autorrelleno desde el perfil, ni
 * validación por tipo, ni teclado adecuado (ver `FieldKeys.canonicalOf`).
 *
 * ### Por qué no hace falta tocar el proxy
 *
 * `ai-proxy.php` sólo usa `task` en dos sitios: la lista blanca (`extract` / `locate_signature`) y
 * la comprobación de que haya imágenes, que exige al menos una **para todo menos `extract`**. Esta
 * tarea es de texto puro —nombres y etiquetas, sin páginas renderizadas—, así que entra por
 * `extract` sin desplegar nada. El precedente es `FieldLabeler`, que reutiliza
 * `locate_signature` por el mismo motivo.
 *
 * ### Qué se envía
 *
 * Sólo los **nombres de campo y sus etiquetas** de la plantilla en blanco: los rótulos impresos
 * del formulario, nunca valores. No sale ningún dato de cliente, igual que en el etiquetado.
 *
 * ### Por qué se valida la respuesta y no se aplica sola
 *
 * Una canónica equivocada no falla: mete el dato del cliente en el hueco de otro y el PDF sale
 * mal sin que salte nada. Así que la respuesta se filtra contra el catálogo, se descartan las
 * claves inventadas y los duplicados, y lo que queda **se ofrece** en el editor para que el
 * usuario confirme — la misma regla que el etiquetado: lo que corrija manda sobre la IA.
 */
class CanonicalMapper @Inject constructor(
    private val api: ProxyApi
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Mismo orden que el resto de tareas de texto. Groq entra aquí (a diferencia de
     * `FieldLabeler`) porque esto **no es visión**: es clasificar cadenas, que es justo lo que
     * sabe hacer. Y aunque se invente una clave, el filtro contra el catálogo la tira.
     */
    private val order = listOf(
        AiProvider.MISTRAL, AiProvider.SCALEWAY, AiProvider.CLAUDE, AiProvider.GEMINI,
        AiProvider.GROK, AiProvider.GROQ,
    )

    /**
     * Propone canónicas para los campos de texto del esquema que aún no la tengan.
     *
     * @return `nombre real -> clave canónica`, ya validado y sin duplicados. Vacío si ningún
     *   motor respondió algo utilizable.
     */
    suspend fun propose(schema: FormSchema, available: List<AiProvider>): Map<String, String> {
        // Sólo campos de TEXTO: una casilla o un radio representan una elección, no el CP del
        // cliente. Y sólo los que están sin asignar, para no pisar decisiones ya tomadas.
        val pendientes = schema.allFields()
            .filter { it.kind == FieldKind.TEXT && it.canonical == null && it.label.isNotBlank() }
            .distinctBy { it.name }
        if (pendientes.isEmpty()) return emptyMap()

        // Las que ya están cogidas no se vuelven a ofrecer: una canónica es exclusiva de un campo.
        val ocupadas = schema.allFields().mapNotNull { it.canonical }.toSet()
        val disponibles = CanonicalCatalog.ALL.filter { it.key !in ocupadas }
        if (disponibles.isEmpty()) return emptyMap()

        val prompt = buildPrompt(pendientes.map { it.name to it.label }, disponibles)

        for (p in order) {
            if (p !in available) continue
            val req = ProxyRequest(
                provider = p.id,
                prompt = prompt,
                task = "extract",   // tarea de TEXTO del proxy; es la única que admite 0 imágenes
                maxTokens = 2048,
            )
            val resp = try {
                api.call(req)
            } catch (e: HttpException) {
                Log.w("CanonicalMapper", "${p.displayName} HTTP ${e.code()}")
                continue
            } catch (e: Exception) {
                Log.w("CanonicalMapper", "${p.displayName} exception: ${e.message}")
                continue
            }
            if (!resp.ok) continue
            val limpio = parse(resp.text)?.let { sanitize(it, pendientes.map { f -> f.name }, ocupadas) }
            if (!limpio.isNullOrEmpty()) return limpio
        }
        return emptyMap()
    }

    /**
     * Filtra la respuesta del motor. Es la parte que no se puede saltar: un motor puede devolver
     * claves que no existen, campos que no se le preguntaron, o la misma canónica para dos
     * campos.
     *
     * En caso de duplicado gana el primero y los demás se descartan, en vez de elegir al azar:
     * lo que quede sin proponer el usuario lo asigna a mano, y eso es preferible a un enganche
     * silenciosamente equivocado.
     */
    internal fun sanitize(
        raw: CanonicalProposal,
        preguntados: List<String>,
        ocupadas: Set<String>,
    ): Map<String, String> {
        val validas = CanonicalCatalog.ALL.map { it.key }.toSet()
        val nombres = preguntados.toSet()
        val usadas = ocupadas.toMutableSet()
        val out = LinkedHashMap<String, String>()
        for ((name, canonical) in raw.enganches) {
            val c = canonical.trim()
            if (name !in nombres) continue      // campo que no se preguntó
            if (c !in validas) continue         // clave inventada
            if (c in usadas) continue           // ya ocupada, aquí o por otra propuesta
            out[name] = c
            usadas += c
        }
        return out
    }

    private fun parse(text: String): CanonicalProposal? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            json.decodeFromString<CanonicalProposal>(text.substring(start, end + 1))
        } catch (e: Exception) {
            Log.w("CanonicalMapper", "JSON ilegible: ${e.message}")
            null
        }
    }

    private fun buildPrompt(
        campos: List<Pair<String, String>>,
        disponibles: List<CanonicalCatalog.Entry>,
    ): String {
        val listaCampos = campos.joinToString("\n") { (name, label) -> "- \"$name\": $label" }
        val listaCanon = disponibles.joinToString("\n") { "- ${it.key}: ${it.label} (${it.group})" }

        return """
Eres un asistente que empareja los huecos de un contrato español con los datos de un cliente.

Te doy dos listas. La primera son huecos rellenables de un formulario EN BLANCO, con su nombre
técnico y el rótulo impreso que los acompaña. La segunda son los datos que la aplicación ya tiene
guardados del cliente.

HUECOS DEL FORMULARIO
$listaCampos

DATOS DISPONIBLES
$listaCanon

Para cada hueco, dime qué dato de la segunda lista le corresponde.

REGLAS
1. Usa EXACTAMENTE la clave de la segunda lista (la parte antes de los dos puntos). No inventes
   claves ni las traduzcas.
2. Cada dato se usa COMO MÁXIMO UNA VEZ. Si dudas entre dos huecos, elige uno solo.
3. Un contrato tiene DOS direcciones: la fiscal o social de la empresa, y la de instalación o
   suministro del servicio. No las confundas: fíjate en el rótulo y en el título de la sección.
4. Distingue a la EMPRESA de su REPRESENTANTE. "NIF" a secas suele ser de la empresa;
   "NIF del firmante" o "DNI del apoderado" es del representante.
5. Si un hueco no corresponde claramente a ningún dato de la lista, OMÍTELO. Es mejor dejarlo
   fuera que emparejarlo mal: un dato en el hueco equivocado no da ningún error, sale impreso en
   el contrato final y nadie lo ve.

Responde SÓLO con este JSON, sin texto alrededor ni ```:
{"enganches": {"nombre tecnico del hueco": "clave_del_dato"}}
""".trimIndent()
    }
}
