package com.mejoresiagratis.rellenador.data.remote

import android.util.Log
import com.mejoresiagratis.rellenador.data.model.AiProvider
import com.mejoresiagratis.rellenador.data.model.DocPayload
import com.mejoresiagratis.rellenador.data.model.FormField
import com.mejoresiagratis.rellenador.data.model.FormSchema
import com.mejoresiagratis.rellenador.data.model.FormSection
import com.mejoresiagratis.rellenador.data.model.LabelSource
import com.mejoresiagratis.rellenador.data.model.ProxyRequest
import com.mejoresiagratis.rellenador.data.model.TableColumn
import com.mejoresiagratis.rellenador.data.model.TableRow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import javax.inject.Inject

/**
 * Respuesta del etiquetado. Todo opcional: un motor puede devolver sólo parte.
 *
 * Las claves son los **identificadores que se le mandaron**, no nombres de campo, para no
 * enviarle al motor los nombres reales del AcroForm (que además son basura del tipo
 * `Campo de texto 116` y sólo lo confundirían).
 */
@Serializable
data class FieldLabels(
    val campos: Map<String, String> = emptyMap(),
    val columnas: Map<String, String> = emptyMap(),
    val secciones: Map<String, String> = emptyMap(),
)

/**
 * Fase 3 — **etiquetado por visión**.
 *
 * Muchos campos de los formularios reales no tienen ningún nombre útil: bloques enteros del
 * contrato de Aire se llaman `Campo de texto 116`, `Casilla de verificación 27`. El nombre no
 * dice qué se escribe ahí; sólo la página impresa lo dice. De ahí que el etiquetado tenga que
 * ser por imagen y no por texto — y el Modelo 145, que dio origen al plan, además tiene el
 * texto en mojibake, así que ni leyéndolo serviría.
 *
 * Se apoya en la estructura que ya produjo `FormSchemaBuilder`: se pregunta **por columna**, no
 * por celda. Una tabla de 25 filas × 7 columnas son 175 campos pero sólo **7 preguntas**, y
 * además la etiqueta correcta de una celda es la de su columna. Sin ese paso previo, esta fase
 * costaría cientos de llamadas y daría peores respuestas.
 *
 * El flujo (proxy, orden de motores, tolerancia a caídas) es el mismo que ya usa
 * `SignatureLocator`, que lleva funcionando desde la v0.2.1.
 */
class FieldLabeler @Inject constructor(
    private val api: ProxyApi
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Mismo orden que `SignatureLocator`: Groq queda fuera porque no tiene visión real (es un
     * motor de texto que se inventa el JSON), y aquí eso sería peor que no responder — una
     * etiqueta inventada parece correcta y nadie la revisa.
     */
    private val order = listOf(
        AiProvider.MISTRAL, AiProvider.SCALEWAY, AiProvider.CLAUDE, AiProvider.GEMINI, AiProvider.GROK
    )

    /**
     * Pide etiquetas para los elementos de UNA página.
     *
     * @param imageB64 la página renderizada (JPEG en base64).
     * @param targets qué etiquetar, ya con su posición en % de la página.
     * @return las etiquetas obtenidas, o null si ningún motor respondió algo utilizable.
     */
    suspend fun label(
        imageB64: String,
        targets: List<Target>,
        available: List<AiProvider>,
        bandTopPct: Float = 0f,
        bandBottomPct: Float = 100f,
    ): FieldLabels? {
        if (targets.isEmpty()) return null
        val prompt = buildPrompt(targets, bandTopPct, bandBottomPct)

        for (p in order) {
            if (p !in available) continue
            val req = ProxyRequest(
                provider = p.id,
                prompt = prompt,
                task = "locate_signature",   // tarea de VISIÓN del proxy; no hay una específica
                maxTokens = 1500,
                docs = listOf(DocPayload(mime = "image/jpeg", b64 = imageB64)),
            )
            val resp = try {
                api.call(req)
            } catch (e: HttpException) {
                Log.w("FieldLabeler", "${p.displayName} HTTP ${e.code()}")
                continue
            } catch (e: Exception) {
                Log.w("FieldLabeler", "${p.displayName} exception: ${e.message}")
                continue
            }
            if (!resp.ok) continue
            parse(resp.text)?.let { if (it.campos.isNotEmpty() || it.columnas.isNotEmpty()) return it }
        }
        return null
    }

    /** Un elemento a etiquetar, situado en la página en porcentaje (0-100). */
    data class Target(
        val id: String,
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float,
        val isColumn: Boolean = false,
    )

    private fun buildPrompt(
        targets: List<Target>,
        bandTopPct: Float = 0f,
        bandBottomPct: Float = 100f,
    ): String {
        val campos = targets.filter { !it.isColumn }
        val columnas = targets.filter { it.isColumn }

        val lista = buildString {
            for (t in targets) {
                val tipo = if (t.isColumn) "COLUMNA" else "CAMPO"
                append(
                    "- %s %s: x=%.1f y=%.1f w=%.1f h=%.1f\n".format(
                        tipo, t.id, t.x, t.y, t.w, t.h
                    )
                )
            }
        }

        // Interpolación y no `format()`: esto se inserta en una cadena RAW, sobre la que
        // `String.format` no se aplica — dejar `%.0f` ahí lo mandaría literal al motor.
        val desde = bandTopPct.toInt()
        val hasta = bandBottomPct.toInt()
        return """
Eres un asistente que etiqueta los huecos rellenables de un formulario impreso en español.

Te doy la imagen de UNA página y una lista de rectángulos, con posición en PORCENTAJE de la
página (x,y = esquina superior izquierda; 0,0 arriba a la izquierda; 100,100 abajo a la
derecha).

Para cada rectángulo, dime QUÉ SE ESCRIBE AHÍ, leyendo el texto IMPRESO que lo rotula. El
rótulo suele estar justo a la izquierda del hueco, o justo encima. Para una COLUMNA de tabla,
el rótulo es la cabecera de esa columna.

ESTA TANDA cubre SÓLO la banda horizontal de la página entre el $desde% y el $hasta% de su
altura. Los rectángulos van listados en orden de lectura DENTRO de esa banda: de arriba abajo
y, dentro de cada fila, de izquierda a derecha. Ignora los rótulos que queden fuera de la
banda: sus huecos se preguntan en otra tanda.

REGLAS
1. Usa las COORDENADAS de cada rectángulo para localizarlo en la imagen. No supongas que el
   primer rectángulo de la lista corresponde al primer rótulo de la PÁGINA: el primero de la
   lista es el primero de LA BANDA. Un rectángulo mal emparejado mete el dato del cliente en el
   hueco de otro.
2. Usa EXACTAMENTE el texto impreso del formulario, sin reformular ni traducir. Si pone
   "NIF/CIF/NIE", responde "NIF/CIF/NIE".
3. Etiqueta corta: lo que rotula el hueco, sin frases ni instrucciones.
4. Si el hueco pertenece a un bloque de un TERCERO —«titular donante», «cambio de titular»,
   «titular de la línea», «persona de contacto» de otra dirección— incluye esa pertenencia en la
   etiqueta (por ejemplo "Domicilio titular donante", no sólo "Domicilio"). Son datos de OTRA
   persona o empresa y confundirlos con los del cliente estropea el contrato en silencio.
5. Si un rectángulo no tiene un rótulo claro, OMÍTELO. Es preferible que falte una etiqueta a
   que esté inventada: una etiqueta inventada parece correcta y nadie la revisa.
6. No inventes rectángulos ni cambies los identificadores que te doy.

RECTÁNGULOS (${campos.size} campos, ${columnas.size} columnas)
$lista
Responde SOLO con este JSON, sin texto alrededor ni ```:
{"campos":{"<id>":"<etiqueta>"},"columnas":{"<id>":"<etiqueta>"}}
        """.trimIndent()
    }

    private fun parse(raw: String?): FieldLabels? {
        if (raw.isNullOrBlank()) return null
        val a = raw.indexOf('{')
        val b = raw.lastIndexOf('}')
        if (a < 0 || b <= a) return null
        return runCatching { json.decodeFromString<FieldLabels>(raw.substring(a, b + 1)) }.getOrNull()
    }
}

/**
 * Aplica etiquetas a un esquema, devolviendo uno nuevo.
 *
 * Regla que no se negocia: **nunca pisa una etiqueta de [LabelSource.USUARIO]**. Si una persona
 * la corrigió a mano en el editor, su criterio manda sobre cualquier reetiquetado automático
 * posterior — sin esto, volver a analizar el documento borraría el trabajo del usuario en
 * silencio, que es el peor tipo de fallo.
 */
object SchemaLabeling {

    fun apply(schema: FormSchema, labels: FieldLabels): FormSchema =
        schema.copy(sections = schema.sections.map { applyToSection(it, labels) })

    private fun applyToSection(section: FormSection, labels: FieldLabels): FormSection =
        section.copy(
            title = labels.secciones[section.id] ?: section.title,
            fields = section.fields.map { relabel(it, labels.campos[it.name]) },
            columns = section.columns.map { relabelColumn(it, labels.columnas[it.id]) },
            rows = section.rows.map { relabelRow(it, section.columns, labels) },
            blocks = section.blocks.map { block -> block.map { relabel(it, labels.campos[it.name]) } },
        )

    private fun relabel(field: FormField, newLabel: String?): FormField {
        if (newLabel.isNullOrBlank()) return field
        if (field.labelSource == LabelSource.USUARIO) return field
        return field.copy(label = newLabel.trim(), labelSource = LabelSource.VISION)
    }

    private fun relabelColumn(column: TableColumn, newLabel: String?): TableColumn {
        if (newLabel.isNullOrBlank()) return column
        if (column.labelSource == LabelSource.USUARIO) return column
        return column.copy(label = newLabel.trim(), labelSource = LabelSource.VISION)
    }

    /**
     * A las celdas se les pone la etiqueta de SU COLUMNA. Es lo correcto y además lo barato:
     * una tabla de 25×7 son 175 celdas, pero sólo se preguntaron 7 columnas.
     */
    private fun relabelRow(
        row: TableRow,
        columns: List<TableColumn>,
        labels: FieldLabels,
    ): TableRow = row.copy(
        cells = row.cells.mapValues { (columnId, field) ->
            val fromColumn = labels.columnas[columnId]
                ?: columns.firstOrNull { it.id == columnId }?.label
            relabel(field, labels.campos[field.name] ?: fromColumn)
        }
    )
}
