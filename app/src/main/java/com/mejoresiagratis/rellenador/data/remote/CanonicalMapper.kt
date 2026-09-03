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
 * claves inventadas o preguntadas a un campo que no era, y lo que queda **se ofrece** en el
 * editor para que el usuario confirme — la misma regla que el etiquetado: lo que corrija manda
 * sobre la IA.
 *
 * ### Tanda 5·4i — ya no es 1:1
 *
 * Hasta aquí, una canónica sólo podía proponerse para un campo: `disponibles` excluía las que
 * ya estuvieran usadas en el esquema, y `sanitize()` descartaba cualquier segunda propuesta con
 * la misma clave dentro de la misma respuesta. Eso era conservador a propósito mientras
 * `SchemaEditing.setCanonical` tampoco permitía que dos campos compartieran canónica — pero
 * desde que sí lo permite (5·4i, mitad 1), esa conservadurismo dejaba fuera justo el caso que
 * motivó la tanda: un contrato con el nombre del cliente repetido en tres páginas no proponía
 * nada para la 2ª y 3ª, que quedaban «sin vincular» esperando a que el usuario las enganchara a
 * mano una a una. Ahora se ofrece el catálogo entero (también las canónicas ya usadas) y no se
 * descarta ningún duplicado: si la IA reconoce el mismo dato en varios huecos del PDF, los
 * engancha todos. El riesgo que esto reabre —confundir al TITULAR con un TERCERO que comparte
 * rótulo (el titular donante de una portabilidad, el representante y la empresa)— se cierra en
 * el propio prompt (regla 5), no en el filtro: aquí sólo se sigue comprobando que la clave exista
 * y que el campo sea uno de los preguntados.
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
     * @return `nombre real -> clave canónica`, ya validado. Puede repetir una clave en varios
     *   nombres si la IA reconoce el mismo dato en más de un hueco (tanda 5·4i). Vacío si ningún
     *   motor respondió algo utilizable.
     */
    suspend fun propose(schema: FormSchema, available: List<AiProvider>): Map<String, String> {
        // Sólo campos de TEXTO: una casilla o un radio representan una elección, no el CP del
        // cliente. Y sólo los que están sin asignar, para no pisar decisiones ya tomadas.
        val pendientes = schema.allFields()
            .filter { it.kind == FieldKind.TEXT && it.canonical == null && it.label.isNotBlank() }
            .distinctBy { it.name }
        if (pendientes.isEmpty()) return emptyMap()

        // Tanda 5·4i — antes se excluían las canónicas `ocupadas` (ya asignadas a otro campo) de
        // lo que se ofrecía: un segundo campo con el MISMO dato nunca podía enterarse de que esa
        // opción existía. Se ofrece el catálogo entero para que la IA pueda reconocer el mismo
        // dato en varios huecos, no sólo en el primero.
        val disponibles = CanonicalCatalog.ALL

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
            // `ProxyResponse.text` es nullable: un motor puede responder ok sin cuerpo.
            val limpio = resp.text
                ?.let { parse(it) }
                ?.let { sanitize(it, pendientes.map { f -> f.name }) }
            if (!limpio.isNullOrEmpty()) return limpio
        }
        return emptyMap()
    }

    /**
     * Filtra la respuesta del motor. Es la parte que no se puede saltar: un motor puede devolver
     * claves que no existen, o campos que no se le preguntaron.
     *
     * Tanda 5·4i — ya NO descarta duplicados: si la IA propone la misma clave para dos campos,
     * ambos se conservan (era «gana el primero» hasta esta tanda). El filtro que evita
     * confundir al titular con un tercero vive en el prompt (regla 5), no aquí.
     */
    internal fun sanitize(
        raw: CanonicalProposal,
        preguntados: List<String>,
    ): Map<String, String> {
        val validas = CanonicalCatalog.ALL.map { it.key }.toSet()
        val nombres = preguntados.toSet()
        val out = LinkedHashMap<String, String>()
        for ((name, canonical) in raw.enganches) {
            val c = canonical.trim()
            if (name !in nombres) continue      // campo que no se preguntó
            if (c !in validas) continue         // clave inventada
            out[name] = c
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
2. Si el MISMO dato aparece repetido en varios huecos del formulario (el nombre del cliente en
   dos páginas, la misma dirección partida en columnas idénticas de dos tablas), usa la MISMA
   clave en todos esos huecos. No es un error compartirla — es lo correcto.
3. Un contrato tiene DOS direcciones: la fiscal o social de la empresa, y la de instalación o
   suministro del servicio. No las confundas: fíjate en el rótulo y en el título de la sección.
4. Distingue a la EMPRESA de su REPRESENTANTE. "NIF" a secas suele ser de la empresa;
   "NIF del firmante" o "DNI del apoderado" es del representante.
5. NUNCA uses la misma clave para el TITULAR del contrato y un TERCERO (el titular DONANTE de
   una portabilidad, un representante frente a la empresa que representa, un fiador, un
   coarrendatario…) aunque el rótulo impreso sea idéntico ("Nombre y apellidos" aparece en
   ambos). Son personas o entidades DISTINTAS aunque el hueco se llame igual: fíjate en el
   título de la sección o en palabras como "donante", "cedente", "representante", "apoderado".
6. Si un hueco no corresponde claramente a ningún dato de la lista, OMÍTELO. Es mejor dejarlo
   fuera que emparejarlo mal: un dato en el hueco equivocado no da ningún error, sale impreso en
   el contrato final y nadie lo ve.

Responde SÓLO con este JSON, sin texto alrededor ni ```:
{"enganches": {"nombre tecnico del hueco": "clave_del_dato"}}
""".trimIndent()
    }
}
