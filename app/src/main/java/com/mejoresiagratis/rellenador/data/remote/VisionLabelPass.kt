package com.mejoresiagratis.rellenador.data.remote

import android.graphics.Bitmap
import android.util.Base64
import com.mejoresiagratis.rellenador.data.model.AiProvider
import com.mejoresiagratis.rellenador.data.model.FormSchema
import com.mejoresiagratis.rellenador.data.model.LabelTarget
import com.mejoresiagratis.rellenador.data.model.LabelTargetPlan
import com.mejoresiagratis.rellenador.data.model.ThirdPartyDetector
import com.mejoresiagratis.rellenador.data.pdf.PdfPageRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

/**
 * Pasa el etiquetado por visión ([FieldLabeler]) sobre un [FormSchema] entero: recorre las
 * páginas que tienen algo que etiquetar, pregunta una vez por página (o por tanda, si la página
 * tiene muchos huecos) y devuelve el esquema reetiquetado con [SchemaLabeling].
 *
 * Es la pieza que faltaba entre la fase 3 (`FieldLabeler`, que sabe preguntar por UNA página) y
 * el cableado de la 0.10.4 (que sabe construir el esquema y mostrarlo). Sin esto, `FieldLabeler`
 * existía desde la 0.10.1 sin que nada lo llamara.
 *
 * ── El desajuste de identificadores que resuelve ──
 * `FieldLabeler` documenta —y con razón— que los ids que se le mandan al motor **no** son los
 * nombres del AcroForm: mandarle `Campo de texto 116` no le aporta nada y le sugiere que el
 * nombre significa algo. Pero `SchemaLabeling.apply()` busca las etiquetas por
 * `labels.campos[field.name]`. Las dos mitades de la fase 3 no encajaban, y el fallo habría sido
 * **silencioso**: cero etiquetas aplicadas, ningún error.
 *
 * Aquí se cierra ese hueco sin tocar ninguna de las dos: se manda un token opaco por objetivo
 * ([TOKEN_FIELD]/[TOKEN_COLUMN] + índice), y la respuesta se **traduce** de vuelta a nombres de
 * campo e ids de columna antes de dársela a `SchemaLabeling`. Cada pieza sigue haciendo lo que
 * dice su documentación.
 *
 * ── Por qué por página y por tandas ──
 * Una llamada por página con campos, no una por página: el contrato de Orange son 54 páginas con
 * campos en 6, así que preguntar por todas sería 48 llamadas tiradas. Y dentro de una página, los
 * objetivos se parten en tandas de [MAX_TARGETS_PER_CALL]: la respuesta va limitada a 1500 tokens
 * en `FieldLabeler`, y una página del contrato de Aire con 80 huecos no cabe en ese presupuesto —
 * el JSON se truncaría y el parseo devolvería null, perdiendo la página entera.
 */
class VisionLabelPass @Inject constructor(
    private val labeler: FieldLabeler,
) {

    /** Progreso para la interfaz: páginas con objetivos ya procesadas de un total. */
    data class Progress(val done: Int, val total: Int)

    data class Result(
        val schema: FormSchema,
        /** Etiquetas realmente aplicadas (campos + columnas). 0 = ningún motor respondió. */
        val labelled: Int,
        /** Páginas que tenían objetivos y sobre las que se preguntó. */
        val pages: Int,
    )

    /**
     * @param file el PDF en un fichero local (`PdfRenderer` no acepta un `Uri` de SAF).
     * @param available motores con visión disponibles; el orden lo decide [FieldLabeler].
     */
    suspend fun run(
        schema: FormSchema,
        file: File,
        available: List<AiProvider>,
        onProgress: (Progress) -> Unit = {},
    ): Result = withContext(Dispatchers.IO) {
        if (available.isEmpty()) return@withContext Result(schema, labelled = 0, pages = 0)

        val batches = LabelTargetPlan.build(schema)
        if (batches.isEmpty()) return@withContext Result(schema, labelled = 0, pages = 0)

        var merged = FieldLabels()
        var done = 0
        val pages = batches.map { it.page }.distinct()
        val total = pages.size
        onProgress(Progress(0, total))

        PdfPageRenderer(file).use { renderer ->
            for (page in pages) {
                // Una página fuera de rango no es motivo para abortar el resto: puede pasar si el
                // esquema viene guardado de un PDF con distinto número de páginas.
                if (page !in 0 until renderer.pageCount) {
                    done++
                    onProgress(Progress(done, total))
                    continue
                }

                val (wPt, hPt) = renderer.pageSize(page)
                if (wPt <= 0 || hPt <= 0) {
                    done++
                    onProgress(Progress(done, total))
                    continue
                }

                // La imagen se renderiza UNA vez por página aunque la página se pregunte en
                // varias tandas: es lo caro de esta pasada.
                val b64 = renderer.render(page, RENDER_WIDTH_PX).toJpegBase64()

                for (batch in batches) {
                    if (batch.page != page) continue
                    val asTargets = batch.targets.map { it.toLabelerTarget(wPt, hPt) }
                    // Tanda 5·4k — la banda que cubre la tanda viaja al prompt. Sin ella, un
                    // motor que empareje por índice le pone a la segunda tanda los rótulos con
                    // los que empieza la página, que es el desplazamiento que la 0.10.26 dejó
                    // sin cerrar (ver `LabelTargetPlan`).
                    val labels = runCatching {
                        labeler.label(
                            imageB64 = b64,
                            targets = asTargets,
                            available = available,
                            bandTopPct = batch.topPt / hPt * 100f,
                            bandBottomPct = batch.bottomPt / hPt * 100f,
                        )
                    }.getOrNull()
                    if (labels != null) merged = merged.mergeTranslating(labels, batch.targets)
                }

                done++
                onProgress(Progress(done, total))
            }
        }

        // Tanda 5·4j — las secciones de tercero se marcan DESPUÉS de etiquetar, porque es el
        // etiquetado el que da título legible a las secciones ("CAMBIO TITULAR", "CAPTURA DE
        // FIBRA CON CAMBIO DE TITULARIDAD"), y `ThirdPartyDetector` decide por ese título.
        val applied = ThirdPartyDetector.mark(SchemaLabeling.apply(schema, merged))
        Result(
            schema = applied,
            labelled = merged.campos.size + merged.columnas.size,
            pages = total,
        )
    }

    // ── Objetivos ────────────────────────────────────────────────────────────

    /**
     * Tanda 5·4k — qué se pregunta y en qué tandas lo decide [LabelTargetPlan], en
     * `data/model`, que es Kotlin puro y por tanto typecheckeable y comprobable en local. Aquí
     * sólo queda la traducción de un objetivo a las coordenadas en porcentaje que promete el
     * prompt de [FieldLabeler].
     *
     * Antes esta clase tenía su propio `collectTargets` con su propia idea del orden de lectura
     * (`(y / 12f).toInt()`), que era además la variante rota: trocear el eje Y en tramos fijos
     * parte una fila impresa en cuanto el corte del tramo cae entre dos de sus campos, y eso ya
     * estaba diagnosticado y corregido en `PdfFieldInspector.orderByReadingRows`. Ahora las dos
     * usan [com.mejoresiagratis.rellenador.data.model.ReadingOrder].
     */
    private fun LabelTarget.toLabelerTarget(pageWidthPt: Int, pageHeightPt: Int) =
        FieldLabeler.Target(
            id = token,
            // A porcentaje de página, que es lo que el prompt de `FieldLabeler` promete al motor.
            // `FieldRect` ya viene con origen arriba-izquierda, igual que el sistema del prompt,
            // así que no hay que invertir nada.
            x = rect.x / pageWidthPt * 100f,
            y = rect.y / pageHeightPt * 100f,
            w = rect.width / pageWidthPt * 100f,
            h = rect.height / pageHeightPt * 100f,
            isColumn = isColumn,
        )

    // ── Traducción de vuelta ─────────────────────────────────────────────────

    /**
     * Convierte las etiquetas que vienen indexadas por token en etiquetas indexadas por nombre de
     * campo / id de columna, que es lo que `SchemaLabeling` sabe buscar, y las acumula.
     *
     * Un token que no esté en la tanda se descarta: el motor tiene prohibido inventar
     * identificadores (regla 4 del prompt), y si lo hace igualmente no se le hace caso.
     */
    private fun FieldLabels.mergeTranslating(
        fresh: FieldLabels,
        aims: List<LabelTarget>,
    ): FieldLabels {
        val byToken = aims.associateBy { it.token }
        val campos = campos.toMutableMap()
        val columnas = columnas.toMutableMap()

        for ((token, label) in fresh.campos) {
            if (label.isBlank()) continue
            byToken[token]?.fieldName?.let { campos[it] = label }
        }
        for ((token, label) in fresh.columnas) {
            if (label.isBlank()) continue
            val aim = byToken[token] ?: continue
            // Un motor puede colocar una columna en "campos" o al revés; se acepta por donde
            // venga y se coloca según lo que ESTE lado sabe que era el objetivo.
            aim.columnId?.let { columnas[it] = label }
            aim.fieldName?.let { campos[it] = label }
        }
        for ((token, label) in fresh.campos) {
            if (label.isBlank()) continue
            byToken[token]?.columnId?.let { columnas[it] = label }
        }

        return FieldLabels(campos = campos, columnas = columnas, secciones = secciones)
    }

    private fun Bitmap.toJpegBase64(): String {
        val out = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private companion object {
        /**
         * Ancho de render. Suficiente para que el rótulo impreso al lado de un hueco sea legible,
         * y por debajo del techo de ~2000 px de lado largo que ya se aplica en el localizador de
         * firma (por encima, varios motores devuelven 400/500).
         */
        const val RENDER_WIDTH_PX = 1400

        const val JPEG_QUALITY = 85

        // El tope de objetivos por llamada y los prefijos de token viven en `LabelTargetPlan`
        // desde la 5·4k, junto a la lógica que los usa.
    }
}
