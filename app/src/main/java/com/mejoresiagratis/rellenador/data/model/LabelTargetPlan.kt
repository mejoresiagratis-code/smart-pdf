package com.mejoresiagratis.rellenador.data.model

/**
 * Un elemento a etiquetar por visión, con su token opaco y a qué apunta de vuelta.
 * [fieldName] para un campo, [columnId] para una columna de tabla; exactamente una de las dos.
 */
data class LabelTarget(
    val token: String,
    val rect: FieldRect,
    val fieldName: String? = null,
    val columnId: String? = null,
) {
    val isColumn: Boolean get() = columnId != null
}

/**
 * Una llamada al motor de visión: la página que se le enseña y los objetivos por los que se le
 * pregunta, ya numerados.
 *
 * [topPt] y [bottomPt] acotan la **banda vertical** de la página que ocupan estos objetivos.
 * Viajan al prompt porque son la diferencia entre que el modelo empiece a leer rótulos por
 * arriba del todo o por donde de verdad está la tanda; ver [LabelTargetPlan].
 */
data class LabelBatch(
    val page: Int,
    val targets: List<LabelTarget>,
    val topPt: Float,
    val bottomPt: Float,
)

/**
 * Tanda 5·4k — decide **qué se le pregunta al motor de visión y en qué tandas**.
 *
 * Vive aquí, en Kotlin puro, y no dentro de `VisionLabelPass` (que importa `android.graphics`)
 * para que se pueda typecheckear y probar en local: es lógica de emparejamiento, y cuando se
 * equivoca no falla nada — sale una etiqueta plausible en el hueco de otro campo y acaba
 * impresa en el contrato.
 *
 * ### El fallo que corrige
 *
 * La 0.10.26 ordenó los objetivos por orden de lectura antes de numerarlos, con este
 * razonamiento: un modelo que ignore las coordenadas y empareje `k0` con el primer rótulo que
 * lee, `k1` con el segundo, etc., acierta si el orden de los tokens ES el de lectura.
 *
 * El razonamiento es correcto y el arreglo estaba incompleto, porque **la página no se pregunta
 * de una vez**: los objetivos se parten en tandas de [MAX_TARGETS_PER_CALL] (el presupuesto de
 * respuesta de `FieldLabeler` son 1500 tokens) y a cada tanda se le manda la **página entera**
 * como imagen. Con la numeración corrida, la segunda tanda del contrato de Aire empezaba en
 * `k24`; y como ahora las tandas son bandas contiguas de la página, un modelo que empareje por
 * índice le pone a la banda de abajo los 24 primeros rótulos de la página. Medido sobre
 * `Contrato_empresas.pdf`: 49 objetivos en la página 1, 56 en la 2 y 31 en la 3 — o sea 3, 3 y
 * 2 tandas. Sólo la primera tanda de cada página quedaba alineada; el resto se desplazaba en
 * bloque, que es exactamente el síntoma que la 0.10.26 venía a arreglar («cada campo recibe el
 * rótulo de uno que está más arriba»).
 *
 * Aquí se cierra por los dos lados:
 *  - **Los tokens se numeran desde 0 en CADA tanda**, así que el índice del token es la posición
 *    dentro de la banda que se pregunta, no dentro de la página.
 *  - **Una tanda no parte una fila impresa** ([ReadingOrder]), para que `CP:` / `Localidad:` /
 *    `Provincia:` no acaben repartidos entre dos llamadas.
 *  - **La banda viaja al prompt** ([LabelBatch.topPt]/[LabelBatch.bottomPt]), para que el modelo
 *    sepa por qué trozo de la página va la lista y las dos estrategias posibles (leer
 *    coordenadas o tirar de índice) vuelvan a dar el mismo resultado.
 */
object LabelTargetPlan {

    /**
     * Objetivos por llamada. `FieldLabeler` limita la respuesta a 1500 tokens; con más de ~25
     * etiquetas el JSON se trunca y el parseo devuelve null, así que se perdería la tanda
     * entera. Es un techo: una tanda puede quedarse corta para no partir una fila.
     */
    const val MAX_TARGETS_PER_CALL = 24

    const val TOKEN_FIELD = "k"
    const val TOKEN_COLUMN = "t"

    /**
     * Qué preguntar, en tandas, por página y en orden de lectura.
     *
     * Se dejan fuera, a propósito:
     * - **las celdas de tabla**: su etiqueta es la de su columna, y `SchemaLabeling` ya la
     *   propaga. Preguntar celda a celda serían 175 preguntas en vez de 7 en una tabla de 25×7,
     *   y con peor respuesta.
     * - **lo corregido a mano** (`LabelSource.USUARIO`): `SchemaLabeling` no lo pisaría de todos
     *   modos, así que preguntarlo sería gastar una llamada para tirar la respuesta.
     * - **lo que no tiene geometría** (`rect == null`): sin rectángulo no hay nada que situar en
     *   la página. Pasa con los esquemas `BUILTIN` y con los guardados antes de la 0.10.4.
     */
    fun build(schema: FormSchema, maxPerCall: Int = MAX_TARGETS_PER_CALL): List<LabelBatch> {
        val byPage = sortedMapOf<Int, MutableList<LabelTarget>>()

        fun addField(f: FormField) {
            val r = f.rect ?: return
            if (f.labelSource == LabelSource.USUARIO) return
            byPage.getOrPut(f.page) { mutableListOf() } += LabelTarget(
                token = "", rect = r, fieldName = f.name
            )
        }

        for (section in schema.sections) {
            when (section.kind) {
                SectionKind.SIMPLE -> section.fields.forEach(::addField)
                SectionKind.REPEATED_BLOCK -> section.blocks.flatten().forEach(::addField)
                SectionKind.TABLE -> Unit   // las celdas heredan de su columna; ver arriba
            }
            // Las columnas se preguntan siempre, sea cual sea el tipo de sección: sólo las tienen
            // las de tabla, pero filtrar por `kind` aquí sería una suposición de más.
            for (col in section.columns) {
                val r = col.rect ?: continue
                if (col.labelSource == LabelSource.USUARIO) continue
                byPage.getOrPut(col.page) { mutableListOf() } += LabelTarget(
                    token = "", rect = r, columnId = col.id
                )
            }
        }

        val out = mutableListOf<LabelBatch>()
        for ((page, aims) in byPage) {
            val rows = ReadingOrder.rows(aims, y = { it.rect.y }, x = { it.rect.x })
            for (group in packRows(rows, maxPerCall)) {
                var n = 0
                val numbered = group.map { aim ->
                    val prefix = if (aim.isColumn) TOKEN_COLUMN else TOKEN_FIELD
                    aim.copy(token = "$prefix${n++}")
                }
                out += LabelBatch(
                    page = page,
                    targets = numbered,
                    topPt = numbered.minOf { it.rect.y },
                    bottomPt = numbered.maxOf { it.rect.y + it.rect.height },
                )
            }
        }
        return out
    }

    /**
     * Reparte filas completas en tandas de como mucho [maxPerCall] objetivos.
     *
     * Una fila más larga que el tope se parte —no hay alternativa, y pasa de verdad: la fila de
     * casillas de «Provisión» de Portabilidad tiene 5 columnas × 25 filas y el SEPA tiene filas
     * de 24 casillas de IBAN—, pero sólo en ese caso.
     */
    private fun packRows(
        rows: List<List<LabelTarget>>,
        maxPerCall: Int,
    ): List<List<LabelTarget>> {
        val tope = if (maxPerCall < 1) 1 else maxPerCall
        val out = mutableListOf<List<LabelTarget>>()
        var current = mutableListOf<LabelTarget>()
        for (row in rows) {
            if (row.size > tope) {
                if (current.isNotEmpty()) { out += current; current = mutableListOf() }
                row.chunked(tope).forEach { out += it }
                continue
            }
            if (current.size + row.size > tope) {
                out += current
                current = mutableListOf()
            }
            current += row
        }
        if (current.isNotEmpty()) out += current
        return out
    }
}
