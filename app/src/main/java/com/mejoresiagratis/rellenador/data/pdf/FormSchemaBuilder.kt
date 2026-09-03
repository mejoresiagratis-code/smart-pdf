package com.mejoresiagratis.rellenador.data.pdf

import com.mejoresiagratis.rellenador.data.model.FieldKind
import com.mejoresiagratis.rellenador.data.model.FieldRect
import com.mejoresiagratis.rellenador.data.model.FormField
import com.mejoresiagratis.rellenador.data.model.FormSchema
import com.mejoresiagratis.rellenador.data.model.FormSection
import com.mejoresiagratis.rellenador.data.model.LabelSource
import com.mejoresiagratis.rellenador.data.model.SchemaSource
import com.mejoresiagratis.rellenador.data.model.SectionKind
import com.mejoresiagratis.rellenador.data.model.TableColumn
import com.mejoresiagratis.rellenador.data.model.TableRow
import com.mejoresiagratis.rellenador.data.model.ValueOrigin
import javax.inject.Inject

/**
 * Convierte la inspección de un PDF ([PdfFieldInspector.Field]) en un [FormSchema].
 *
 * Es la pieza que faltaba entre la fase 1 (leer los campos) y la fase 3 (etiquetarlos): sin
 * estructura no hay nada que etiquetar, porque una tabla de 12 filas no se pregunta 12 veces.
 *
 * ── La estructura está en la geometría, nunca en el nombre ──
 * En los formularios reales de Aire, dentro de una MISMA fila conviven nombres con sentido y
 * nombres autogenerados. La fila 1 de la tabla de Telefonía Fija del contrato es literalmente
 * `Campo de texto 116 | Campo de texto 128 | Campo de texto 140 | TF cantidad 01 | …`, donde
 * los tres primeros son «Servicio contratado», «Permanencia» y «Penalización». Agrupar por
 * nombre es imposible; por posición es trivial.
 *
 * Eso mismo resuelve gratis los **checkboxes de fila dibujados en otro recuadro**: en
 * Portabilidad, las 100 casillas de «Provisión» se llaman `Check Box4.0`,
 * `Check Box4.4.5.10.5`… y aun así caen en su fila porque comparten `y` con ella.
 *
 * ── Cómo decide qué es tabla ──
 * 1. Agrupa los campos en filas visuales (misma lógica de [PdfFieldInspector]: por hueco).
 * 2. Una `x` es **columna** si se repite en al menos [MIN_ROWS_FOR_COLUMN] filas distintas.
 * 3. Una fila es **de tabla** si al menos [MIN_COLS_FOR_ROW] de sus campos caen en columnas.
 * 4. Filas de tabla consecutivas que comparten columnas se agrupan en una sección `TABLE`.
 *    Una sola fila suelta no hace tabla.
 *
 * ── Verificado contra los cuatro formularios de Aire ──
 * | PDF | Campos | Detecta |
 * |---|---|---|
 * | Contrato empresas | 488 | 5 tablas (13×7, 13×7, 9×8, 10×7, 10×9) + 47 filas sueltas |
 * | Portabilidad fija | 202 | 1 tabla de **25×7** — las 3 columnas de texto **y** las 4 de casillas |
 * | Conectividad | 141 | 1 tabla de 10 filas |
 * | SEPA | 20 | **0 tablas**, es un formulario plano |
 *
 * Las tablas de 13 filas del contrato son las 12 de tarifa más la fila TOTAL, que se detecta
 * aparte por el sufijo del nombre. Que el SEPA dé cero es tan importante como los aciertos: la
 * fila de 11 casillas del BIC **no** es una tabla, es un valor troceado, y el algoritmo no la
 * confunde.
 *
 * En Conectividad sale una fila con el doble de celdas de lo esperado. No es un fallo del
 * algoritmo: es el defecto conocido de ese PDF, donde las filas 07 y 08 están superpuestas en
 * la misma coordenada (ver `docs/ANALISIS_FORMULARIOS_AIRE.md`). El constructor lo refleja en
 * vez de taparlo, que es lo que se quiere para poder avisar.
 *
 * ── Tanda 5·4b — títulos y plegado por ancla (`docs/PLAN_ETIQUETADO_ORGANICO.md`) ──
 * La 5·4 conseguía que las secciones salieran del `FormSchema`, pero se llamaban «Página 1» y
 * «Tabla 3», y un `flushLooseBefore` que sólo vuelca los sueltos de páginas *anteriores* dejaba
 * los de la página en curso siempre detrás de sus tablas (DATOS DEL CLIENTE, arriba del todo,
 * salía en tercera posición). Esta tanda:
 *  · añade [LayoutTextExtractor] como fuente de texto real del PDF;
 *  · detecta anclas de sección (títulos en mayúscula, o casillas de banda) con [detectAnchors];
 *  · define la sección por el **intervalo entre dos anclas** ([buildSectionsByAnchor]), lo que
 *    hace desaparecer el bug de orden de camino: ya no hay "sueltos de la página" que volcar al
 *    final, porque cada fila cae directamente en el hueco de su ancla;
 *  · dentro de esa banda, promueve la casilla que la encabeza a [FormSection.enablerField];
 *  · etiqueta los campos sueltos por geometría ([geometricLabel]) y las columnas de tabla por
 *    la cabecera de su columna ([columnHeaderLabel]), antes de recurrir a la IA.
 *
 * **Compatibilidad**: si no se pasa `layoutWords` (o el PDF no tiene texto de capa, sólo
 * campos), [detectAnchors] devuelve la lista vacía y [build] usa [buildSectionsByPage], que es
 * el algoritmo **literal** de la 5·4, sin tocar una línea — es el único llamador (`WizardViewModel`,
 * fuera del alcance de esta tanda) que no pasa `layoutWords` todavía, y su comportamiento no
 * puede cambiar por esto.
 */
class FormSchemaBuilder @Inject constructor() {

    fun build(
        fields: List<PdfFieldInspector.Field>,
        fingerprint: String,
        pageCount: Int,
        title: String = "Formulario",
        /**
         * Texto del PDF con posición, de [LayoutTextExtractor]. Vacío por compatibilidad: sin
         * él, [build] se comporta exactamente como antes de la 5·4b (ver [buildSectionsByPage]).
         */
        layoutWords: List<LayoutTextExtractor.Word> = emptyList(),
    ): FormSchema {
        if (fields.isEmpty()) {
            return FormSchema(
                id = "learned:$fingerprint",
                title = title,
                source = SchemaSource.LEARNED,
                fingerprint = fingerprint,
                pageCount = pageCount,
            )
        }

        // Tanda 5·4 — promoción de radios disfrazados a checkbox, decidida por GRUPO.
        //
        // El AcroForm de Aire declara 13 grupos con el flag de radio, pero verificado sobre
        // `Contrato_empresas.pdf` con `pypdf`: sólo `Botón de opción 10` es un radio de verdad
        // (6 widgets con estados `/0`..`/5` — la fila de RED INTELIGENTE). Los otros 12 son un
        // widget con un solo estado, o sea una casilla suelta con el flag mal puesto. La
        // comprobación de tipo compatible del `docs/PLAN_FASE_5.md` §6.5, sin esta promoción,
        // rechazaría 12 asignaciones legítimas en el mapeo.
        //
        // Se hace por `name`: dos widgets del mismo grupo tienen el mismo `name` en el AcroForm.
        // El único radio de verdad tiene >1 widget o >1 estado on distinto de Off; los
        // disfrazados, exactamente uno de cada.
        val radioGroupSize: Map<String, Int> = fields
            .filter { it.isRadio }
            .groupBy { it.name }
            .mapValues { (_, widgets) ->
                maxOf(widgets.size, widgets.mapNotNull { it.onState }.toSet().size)
            }

        fun promoted(f: PdfFieldInspector.Field): PdfFieldInspector.Field =
            if (f.isRadio && (radioGroupSize[f.name] ?: 0) <= 1) {
                // Copia como casilla: pierde `isRadio`, gana `isCheckbox`. Todo lo demás intacto.
                f.copy(isRadio = false, isCheckbox = true)
            } else f

        val promotedFields = fields.map(::promoted)
        val rows = groupIntoRows(promotedFields)
        val columnXs = detectColumnXs(rows)
        val anchors = detectAnchors(layoutWords, promotedFields)
        val usedAnchors = anchors.isNotEmpty()

        val sections = if (!usedAnchors) {
            buildSectionsByPage(rows, columnXs)
        } else {
            buildSectionsByAnchor(rows, columnXs, anchors, layoutWords)
        }

        return FormSchema(
            id = "learned:$fingerprint",
            title = title,
            source = SchemaSource.LEARNED,
            fingerprint = fingerprint,
            pageCount = pageCount,
            sections = sections,
            // Sólo se declara la versión nueva si se ha construido DE VERDAD con anclas. El
            // camino de respaldo produce la estructura vieja, así que se queda en 0 y podrá
            // regenerarse más adelante desde un camino que sí pase `layoutWords` (ver
            // `FormSchema.isStaleBuild`). Marcarlo como 1 aquí lo dejaría congelado para siempre.
            builderVersion = if (usedAnchors) FormSchema.BUILDER_VERSION else 0,
        )
    }

    // ── Geometría ────────────────────────────────────────────────────────────

    /** Redondea la x para que campos casi alineados cuenten como la misma columna. */
    private fun xKey(x: Float): Int = Math.round(x / X_TOLERANCE)

    /**
     * Agrupa en filas visuales por **hueco** respecto al ancla de la fila, no troceando el eje
     * Y en tramos fijos — es el mismo criterio que [PdfFieldInspector], y por el mismo motivo:
     * con tramos fijos, dos campos a décimas de punto se separan si el corte pasa entre ellos.
     */
    private fun groupIntoRows(
        fields: List<PdfFieldInspector.Field>
    ): List<List<PdfFieldInspector.Field>> {
        val out = mutableListOf<List<PdfFieldInspector.Field>>()
        for (page in fields.map { it.page }.distinct().sorted()) {
            val byTop = fields.filter { it.page == page }.sortedBy { it.y }
            var row = mutableListOf<PdfFieldInspector.Field>()
            var anchorY = 0f
            for (f in byTop) {
                if (row.isEmpty()) {
                    anchorY = f.y
                    row += f
                } else if (f.y - anchorY <= ROW_TOLERANCE) {
                    row += f
                } else {
                    out += row.sortedBy { it.x }
                    row = mutableListOf(f)
                    anchorY = f.y
                }
            }
            if (row.isNotEmpty()) out += row.sortedBy { it.x }
        }
        return out
    }

    /** Una `x` es columna si aparece en al menos [MIN_ROWS_FOR_COLUMN] filas distintas. */
    private fun detectColumnXs(rows: List<List<PdfFieldInspector.Field>>): Set<Int> {
        val rowsPerX = mutableMapOf<Int, Int>()
        for (row in rows) {
            for (key in row.map { xKey(it.x) }.distinct()) {
                rowsPerX[key] = (rowsPerX[key] ?: 0) + 1
            }
        }
        return rowsPerX.filterValues { it >= MIN_ROWS_FOR_COLUMN }.keys
    }

    private fun sharesColumns(
        a: List<PdfFieldInspector.Field>,
        b: List<PdfFieldInspector.Field>,
        columnXs: Set<Int>,
    ): Boolean {
        val ka = a.map { xKey(it.x) }.filter { it in columnXs }.toSet()
        val kb = b.map { xKey(it.x) }.filter { it in columnXs }.toSet()
        return ka.intersect(kb).size >= MIN_COLS_FOR_ROW
    }

    private fun isTableRow(row: List<PdfFieldInspector.Field>, columnXs: Set<Int>): Boolean =
        row.count { xKey(it.x) in columnXs } >= MIN_COLS_FOR_ROW

    // ── Camino de respaldo: algoritmo literal de la 5·4 (sin `layoutWords`) ────

    /**
     * El algoritmo de la 0.10.10, sin cambios. Se conserva para el único llamador que hoy no
     * pasa `layoutWords` (`WizardViewModel`, fuera del alcance de la 5·4b): su comportamiento
     * no puede depender de esta tanda. Lleva el bug de orden documentado en
     * `docs/PLAN_ETIQUETADO_ORGANICO.md` §1 — a propósito, porque arreglarlo aquí sería
     * cambiarle el comportamiento a ese llamador sin haberlo tocado.
     */
    private fun buildSectionsByPage(
        rows: List<List<PdfFieldInspector.Field>>,
        columnXs: Set<Int>,
    ): List<FormSection> {
        val sections = mutableListOf<FormSection>()
        var pending = mutableListOf<List<PdfFieldInspector.Field>>()
        val loosePerPage = linkedMapOf<Int, MutableList<PdfFieldInspector.Field>>()

        fun addLoose(row: List<PdfFieldInspector.Field>) {
            if (row.isEmpty()) return
            val bucket = loosePerPage.getOrPut(row.first().page) { mutableListOf() }
            bucket += row
        }

        fun flushLooseBefore(pageOfNext: Int?) {
            val closed = loosePerPage.keys.filter { pageOfNext == null || it < pageOfNext }
            for (page in closed) {
                val loose = loosePerPage.remove(page) ?: continue
                if (loose.isEmpty()) continue
                sections += FormSection(
                    id = "pag_${page}",
                    title = "Página ${page + 1}",
                    kind = SectionKind.SIMPLE,
                    fields = loose.sortedWith(compareBy({ it.y }, { it.x }))
                        .mapIndexed { i, f -> toField(f, i) },
                )
            }
        }

        fun flushTable() {
            if (pending.size >= MIN_ROWS_FOR_TABLE) {
                flushLooseBefore(pending.first().first().page)
                sections += tableSection(pending, columnXs, sections.size)
            } else {
                pending.forEach { addLoose(it) }
            }
            pending = mutableListOf()
        }

        for (row in rows) {
            if (!isTableRow(row, columnXs)) {
                flushTable()
                addLoose(row)
            } else if (pending.isEmpty() || sharesColumns(pending.last(), row, columnXs)) {
                pending += row
            } else {
                flushTable()
                pending += row
            }
        }
        flushTable()
        flushLooseBefore(pageOfNext = null)

        return sections
    }

    // ── Camino nuevo: por ancla de sección (tanda 5·4b) ────────────────────────

    /** Una ancla de sección: dónde empieza, cómo se llama y qué casilla la activa (si tiene). */
    private data class Anchor(
        val page: Int,
        val y: Float,
        val title: String,
        val enablerField: String?,
    )

    /** Una línea de texto reconstruida a partir de las palabras de [LayoutTextExtractor]. */
    private data class TextLine(
        val page: Int,
        val y: Float,
        val fontSize: Float,
        val x0: Float,
        val text: String,
    )

    /**
     * Reconstruye líneas agrupando palabras por página, `y` y tamaño de fuente — **no** sólo
     * por `y`: un título de banda y la frase descriptiva que lo acompaña en la misma fila
     * (`CENTRALITA VIRTUAL Aire Suite, Waicom Cloud…`) comparten `y` pero no tamaño, y sin
     * separar por tamaño la mayúscula del título se ensucia con la minúscula de la frase.
     * Medido al preparar esta tanda contra `Contrato_empresas.pdf` con `pdfplumber`.
     */
    private fun buildLines(words: List<LayoutTextExtractor.Word>): List<TextLine> {
        data class Key(val page: Int, val yBucket: Int, val sizeBucket: Int)
        return words
            .groupBy { Key(it.page, Math.round(it.y), Math.round(it.fontSize * 10)) }
            .map { (_, ws) ->
                val sorted = ws.sortedBy { it.x }
                TextLine(
                    page = sorted.first().page,
                    y = sorted.first().y,
                    fontSize = sorted.first().fontSize,
                    x0 = sorted.first().x,
                    text = sorted.joinToString(" ") { it.text }.trim(),
                )
            }
    }

    private fun isAllUpper(text: String): Boolean {
        val letters = text.filter { it.isLetter() }
        return letters.isNotEmpty() && letters.all { it.isUpperCase() }
    }

    /**
     * Detecta las anclas de sección: líneas de ≥ [ANCHOR_MIN_FONT_SIZE] pt, que arrancan en el
     * margen izquierdo (`x0 < `[ANCHOR_MAX_X]) y van en mayúsculas. Verificado con `pdfplumber`
     * sobre `Contrato_empresas.pdf`: de 11 anclas buenas, la regla detecta 10 tal cual; la
     * undécima (`Resumen de todos los servicios contratados`, mayúscula/minúscula mixta, sin
     * casilla al lado) queda **fuera a propósito** — decidido con Pablo: sus campos no están en
     * el alcance del alta y caen en la sección anterior sin romper nada.
     *
     * Se excluyen dos cosas que no son banda:
     *  · [ANCHOR_BLACKLIST] — la etiqueta `DOCUMENTACIÓN`, que se repite una vez por página.
     *  · la cabecera de página (logo + franja superior): cualquier línea a menos de
     *    [MASTHEAD_MARGIN] pt del borde superior. Es una regla de posición, no de texto
     *    literal, para que generalice a los otros PDFs de Aire sin tener que listar sus
     *    literales («CONTRATO EMPRESAS», «CONTRATO DE SERVICIOS»…).
     *
     * Para cada ancla, busca una casilla CHECKBOX pegada a su izquierda (hueco horizontal <
     * [ANCHOR_CHECKBOX_GAP_MAX] pt, centros verticales a menos de
     * [ANCHOR_CHECKBOX_Y_TOLERANCE] pt): si la encuentra, esa ancla lleva `enablerField`. Las 8
     * bandas de `Contrato_empresas.pdf` (§2.1 y §4 del plan) salen así, sin listar sus nombres
     * de campo a mano.
     */
    private fun detectAnchors(
        words: List<LayoutTextExtractor.Word>,
        fields: List<PdfFieldInspector.Field>,
    ): List<Anchor> {
        if (words.isEmpty()) return emptyList()

        val candidates = buildLines(words).filter { line ->
            line.fontSize >= ANCHOR_MIN_FONT_SIZE &&
                line.x0 < ANCHOR_MAX_X &&
                line.y >= MASTHEAD_MARGIN &&
                line.text.length > 3 &&
                isAllUpper(line.text) &&
                line.text !in ANCHOR_BLACKLIST
        }

        return candidates
            .sortedWith(compareBy({ it.page }, { it.y }))
            .map { line ->
                val enabler = fields.firstOrNull { f ->
                    f.isCheckbox &&
                        f.page == line.page &&
                        Math.abs((f.y + f.height / 2f) - line.y) < ANCHOR_CHECKBOX_Y_TOLERANCE &&
                        (line.x0 - (f.x + f.width)) in 0f..ANCHOR_CHECKBOX_GAP_MAX
                }
                // El borde superior de la casilla suele quedar un poco por ENCIMA del texto del
                // título (se centran verticalmente el uno con el otro), así que el propio widget
                // podía caer fuera del intervalo de su banda por un margen de un dígito. Se usa
                // el más alto de los dos como frontera real de la sección, para que la casilla
                // quede dentro de su propia banda y no en la anterior. Detectado con una prueba
                // de comportamiento, no a ojo: sin este ajuste, la casilla-interruptor se colaba
                // como campo suelto de la sección de arriba Y como `enablerField` de la de abajo.
                val y = if (enabler != null) minOf(line.y, enabler.y) else line.y
                Anchor(page = line.page, y = y, title = line.text, enablerField = enabler?.name)
            }
    }

    /**
     * Índice del ancla vigente para una posición `(page, y)`, o `-1` si es anterior a la
     * primera ancla del documento (la cabecera del contrato: casillas CLIENTE, DISTRIBUIDOR,
     * TEKI — todo lo que hay antes de `DATOS DEL CLIENTE`, ver §4 del plan). Asume [anchors]
     * ordenadas por `(page, y)` ascendente, que es como las devuelve [detectAnchors].
     */
    private fun anchorIndexFor(anchors: List<Anchor>, page: Int, y: Float): Int {
        var idx = -1
        for ((i, a) in anchors.withIndex()) {
            if (a.page < page || (a.page == page && a.y <= y)) idx = i else break
        }
        return idx
    }

    /**
     * Construye las secciones por el **intervalo entre anclas**: una fila pertenece a la
     * sección de la última ancla que quede en o antes de su `(page, y)`. Esto es lo que hace
     * desaparecer el bug de orden de la 5·4 (§1 del plan) sin tocarlo directamente — ya no hay
     * "sueltos de la página en curso" que reservar para el final, porque cada fila entra
     * directamente en el hueco de su ancla en el momento en que se procesa.
     *
     * Dentro de cada intervalo se reutiliza la MISMA clasificación tabla/suelto que
     * [buildSectionsByPage] (por columnas globales), así que una banda puede producir más de
     * una `FormSection` (p.ej. TELEFONÍA FIJA: la casilla "Sólo tráfico nacional" suelta + la
     * tabla de tarifa). Las dos comparten título; el `enablerField` sólo lo lleva la primera
     * que se emite para esa ancla, para no repetirlo.
     */
    private fun buildSectionsByAnchor(
        rows: List<List<PdfFieldInspector.Field>>,
        columnXs: Set<Int>,
        anchors: List<Anchor>,
        words: List<LayoutTextExtractor.Word>,
    ): List<FormSection> {
        val sections = mutableListOf<FormSection>()
        var sectionCounter = 0

        var pending = mutableListOf<List<PdfFieldInspector.Field>>()
        var loose = mutableListOf<PdfFieldInspector.Field>()
        var currentAnchorIdx = -2   // centinela: fuerza el primer cambio de banda
        var enablerUsedForBucket = false

        fun titleFor(idx: Int) = if (idx < 0) "Cabecera" else anchors[idx].title
        fun enablerFor(idx: Int) = if (idx < 0) null else anchors[idx].enablerField

        fun flushSimple() {
            val enabler = if (!enablerUsedForBucket) enablerFor(currentAnchorIdx) else null
            // La casilla que activa la banda no es un campo suelto más de su propia sección:
            // pasa a `enablerField` y desaparece de la lista de campos (§2.1 del plan).
            val filtered = loose.filter { it.name != enabler }
            if (filtered.isEmpty() && enabler == null) {
                loose = mutableListOf()
                return
            }
            sections += FormSection(
                id = "sec_${sectionCounter++}",
                title = titleFor(currentAnchorIdx),
                kind = SectionKind.SIMPLE,
                fields = filtered.mapIndexed { i, f ->
                    toField(f, i, rowOf(rows, f), words)
                },
                enablerField = enabler,
            )
            if (enabler != null) enablerUsedForBucket = true
            loose = mutableListOf()
        }

        fun flushTableBucket() {
            if (pending.size >= MIN_ROWS_FOR_TABLE) {
                val enabler = if (!enablerUsedForBucket) enablerFor(currentAnchorIdx) else null
                sections += tableSection(pending, columnXs, sectionCounter++, words).copy(
                    title = titleFor(currentAnchorIdx),
                    enablerField = enabler,
                )
                if (enabler != null) enablerUsedForBucket = true
            } else {
                pending.forEach { row -> loose += row }
            }
            pending = mutableListOf()
        }

        for (row in rows) {
            val repField = row.first()
            val idx = anchorIndexFor(anchors, repField.page, repField.y)
            if (idx != currentAnchorIdx) {
                flushTableBucket()
                flushSimple()
                currentAnchorIdx = idx
                enablerUsedForBucket = false
            }

            if (!isTableRow(row, columnXs)) {
                flushTableBucket()   // una fila suelta corta cualquier tabla en marcha
                loose += row
            } else if (pending.isEmpty()) {
                // Arranca una tabla nueva: si había sueltos acumulados desde que empezó la
                // banda (p.ej. la propia casilla-interruptor), se vuelcan AHORA, en su sitio
                // cronológico. Sin esto, `loose` se queda esperando al cierre de banda y la
                // tabla sale publicada antes que sus sueltos — el mismo bug de orden de la 5·4,
                // reproducido dentro de la banda en vez de entre páginas. Detectado con una
                // prueba de comportamiento, no a ojo.
                flushSimple()
                pending += row
            } else if (sharesColumns(pending.last(), row, columnXs)) {
                pending += row
            } else {
                flushTableBucket()
                pending += row
            }
        }
        flushTableBucket()
        flushSimple()

        return sections
    }

    /** La fila (ordenada por x) a la que pertenece [field], o lista vacía si no se encuentra. */
    private fun rowOf(
        rows: List<List<PdfFieldInspector.Field>>,
        field: PdfFieldInspector.Field,
    ): List<PdfFieldInspector.Field> =
        rows.firstOrNull { row -> row.any { it.name == field.name && it.x == field.x && it.y == field.y } }
            ?: emptyList()

    /**
     * Etiqueta geométrica de un campo suelto (§3.2 del plan): el grupo de palabras a la
     * izquierda del campo, acotado por el borde derecho del widget anterior de la misma fila
     * — ese acotado es lo que evita que `Localidad` se lleve el «CP:» del campo de al lado. Si
     * no hay nada a la izquierda, la línea de encima que solape en X. Null si ninguna de las
     * dos encuentra nada (el campo sigue con su nombre real como respaldo).
     *
     * Medido: 67 de los 90 campos sueltos de `Contrato_empresas.pdf` (74%) quedan bien
     * etiquetados así, sin llamar a la IA.
     */
    private fun geometricLabel(
        field: PdfFieldInspector.Field,
        row: List<PdfFieldInspector.Field>,
        words: List<LayoutTextExtractor.Word>,
    ): String? {
        if (words.isEmpty()) return null

        val idxInRow = row.indexOfFirst { it.name == field.name && it.x == field.x && it.y == field.y }
        val leftBoundary = if (idxInRow > 0) {
            val prev = row[idxInRow - 1]
            prev.x + prev.width
        } else 0f

        val sameRow = words.filter { w ->
            w.page == field.page &&
                Math.abs(w.y - field.y) <= LABEL_ROW_TOLERANCE &&
                w.endX <= field.x &&
                w.x >= leftBoundary
        }.sortedBy { it.x }
        if (sameRow.isNotEmpty()) {
            return sameRow.joinToString(" ") { it.text }.trim().trimEnd(':').ifBlank { null }
        }

        val above = words.filter { w ->
            w.page == field.page &&
                (field.y - w.y) in 0f..LABEL_ABOVE_MAX_GAP &&
                w.x < field.x + field.width &&
                w.endX > field.x
        }.sortedBy { it.x }
        if (above.isNotEmpty()) {
            return above.joinToString(" ") { it.text }.trim().ifBlank { null }
        }
        return null
    }

    /**
     * Etiqueta de columna de tabla (§3.3 del plan): la línea de texto justo encima de la celda
     * más alta de la columna ([anchor], ver [tableSection]), acotada al ancho de la columna. Es
     * la misma región que ya usa la visión para recortar, sólo que ahora se lee con texto en
     * vez de mandarla a un modelo. Null si no hay texto encima (columnas sin cabecera propia,
     * poco frecuente).
     */
    private fun columnHeaderLabel(
        page: Int,
        x: Float,
        width: Float,
        y: Float,
        words: List<LayoutTextExtractor.Word>,
    ): String? {
        if (words.isEmpty()) return null
        val above = words.filter { w ->
            w.page == page &&
                (y - w.y) in 0f..TABLE_HEADER_MAX_GAP &&
                w.x < x + width &&
                w.endX > x
        }
        if (above.isEmpty()) return null
        val closestY = above.maxOf { it.y }   // la línea más cercana a la celda (más abajo)
        return above.filter { Math.abs(it.y - closestY) <= LABEL_ROW_TOLERANCE }
            .sortedBy { it.x }
            .joinToString(" ") { it.text }
            .trim()
            .ifBlank { null }
    }

    // ── Construcción ─────────────────────────────────────────────────────────

    private fun toField(
        f: PdfFieldInspector.Field,
        order: Int,
        row: List<PdfFieldInspector.Field> = emptyList(),
        words: List<LayoutTextExtractor.Word> = emptyList(),
    ) = FormField(
        name = f.name,
        // Etiqueta geométrica si la hay (5·4b); si no, el nombre real como antes, a la espera
        // de la fase 3 (etiquetado por visión) o de que el usuario la corrija a mano.
        label = geometricLabel(f, row, words) ?: f.name,
        kind = when {
            f.isCheckbox -> FieldKind.CHECKBOX
            f.isRadio -> FieldKind.RADIO
            // Tanda 5·4b, regla de higiene 2 del plan: un `/Sig` es un hueco de firma, no
            // texto — antes caía en este mismo `else` como FieldKind.TEXT.
            f.isSignature -> FieldKind.SIGNATURE
            else -> FieldKind.TEXT
        },
        origin = ValueOrigin.DOCUMENTO,
        page = f.page,
        order = order,
        // Geometría tal cual la da el inspector (origen arriba-izquierda, puntos): `FieldRect`
        // usa la misma convención justamente para que esto sea una copia y no una conversión.
        rect = FieldRect(x = f.x, y = f.y, width = f.width, height = f.height),
        // Estado real de ESTE widget (`Sí`, `PAGO_UNICO`…). Para RADIO, distintos widgets con
        // el mismo `name` (= grupo de opción) traen valores distintos aquí; agrupar por `name`
        // es lo que permitirá al futuro editor de mapeo tratar el grupo como una sola unidad.
        onState = f.onState,
        labelSource = LabelSource.NOMBRE_REAL,
    )

    private fun tableSection(
        rows: List<List<PdfFieldInspector.Field>>,
        columnXs: Set<Int>,
        index: Int,
        words: List<LayoutTextExtractor.Word> = emptyList(),
    ): FormSection {
        // Columnas: las x de columna presentes en esta tabla, de izquierda a derecha.
        val xs = rows.flatMap { row -> row.map { it.x } }
            .filter { xKey(it) in columnXs }
            .groupBy { xKey(it) }
            .mapValues { (_, v) -> v.average().toFloat() }
            .toList()
            .sortedBy { it.second }

        val columns = xs.mapIndexed { i, (key, x) ->
            val cellsInColumn = rows.flatMap { row -> row.filter { xKey(it.x) == key } }

            // Rect REPRESENTATIVO de la columna = su celda más alta, ensanchada al ancho máximo
            // de la columna. Dos decisiones, las dos deliberadas:
            //  · la celda más alta y no la unión de todas: la cabecera está justo encima de la
            //    primera fila, así que ése es el ancla desde el que la visión debe mirar hacia
            //    arriba. La unión de 25 filas (Portabilidad) sería media página y no serviría.
            //  · el ancho máximo y no el de esa celda: si la primera celda es más estrecha que
            //    el resto, el recorte cortaría el rótulo por los lados.
            // Se ordena por (página, y) porque una tabla PUEDE abarcar varias páginas: las filas
            // se acumulan mientras compartan columnas, y `groupIntoRows` va página a página.
            val anchor = cellsInColumn.minWithOrNull(compareBy({ it.page }, { it.y }))
            val colWidth = if (cellsInColumn.isEmpty()) 0f else cellsInColumn.maxOf { it.width }

            TableColumn(
                id = "c$key",
                // Tanda 5·4b §3.3 — la celda no se etiqueta sola, hereda la cabecera de su
                // columna (el texto encima de la primera celda). Si no hay texto ahí, se queda
                // con el número de columna de siempre a la espera de la fase 3.
                label = anchor?.let { columnHeaderLabel(it.page, x, colWidth, it.y, words) }
                    ?: "Columna ${i + 1}",
                x = x,
                kind = when {
                    cellsInColumn.isEmpty() -> FieldKind.TEXT
                    // Si TODA la columna son casillas, es una columna de casillas.
                    cellsInColumn.all { it.isCheckbox } -> FieldKind.CHECKBOX
                    // Si TODA la columna son radios (p.ej. las 4 columnas de "Provisión" en
                    // Portabilidad: prefijo `Check Box4..7`, un grupo de opción por fila).
                    cellsInColumn.all { it.isRadio } -> FieldKind.RADIO
                    else -> FieldKind.TEXT
                },
                origin = ValueOrigin.CATALOGO,
                page = anchor?.page ?: 0,
                rect = anchor?.let {
                    FieldRect(x = it.x, y = it.y, width = colWidth, height = it.height)
                },
            )
        }

        val tableRows = rows.mapIndexed { i, row ->
            val cells = row.filter { xKey(it.x) in columnXs }
                .associate { "c${xKey(it.x)}" to toField(it, i) }
            TableRow(
                index = i,
                cells = cells,
                // La fila de totales del contrato se llama `TF cantidad TOTAL` y hermanos.
                isTotal = row.isNotEmpty() && row.all { it.name.trimEnd().endsWith(TOTAL_SUFFIX) },
            )
        }

        return FormSection(
            id = "tabla_$index",
            title = "Tabla ${index + 1}",   // sobrescrito por el título de la banda en la 5·4b
            kind = SectionKind.TABLE,
            columns = columns,
            rows = tableRows,
        )
    }

    private companion object {
        /** Misma tolerancia de fila que `PdfFieldInspector`. */
        const val ROW_TOLERANCE = 6f

        /** Margen para considerar dos campos en la misma columna. */
        const val X_TOLERANCE = 3f

        /** Repeticiones mínimas de una `x` para tratarla como columna. */
        const val MIN_ROWS_FOR_COLUMN = 4

        /** Celdas mínimas en columnas para que una fila cuente como fila de tabla. */
        const val MIN_COLS_FOR_ROW = 3

        /** Una fila suelta no hace tabla. */
        const val MIN_ROWS_FOR_TABLE = 2

        const val TOTAL_SUFFIX = "TOTAL"

        // ── Tanda 5·4b ──

        /** Tamaño mínimo de letra para que una línea cuente como ancla de sección. */
        const val ANCHOR_MIN_FONT_SIZE = 8f

        /** Margen izquierdo máximo para que una línea cuente como ancla de sección. */
        const val ANCHOR_MAX_X = 150f

        /**
         * Franja superior de página (logo + banda de cabecera) que nunca es una ancla de
         * sección, aunque cumpla mayúscula+margen+tamaño. Medido sobre las tres páginas de
         * `Contrato_empresas.pdf`: la cabecera está a 13–16 pt del borde superior y la primera
         * ancla real (`DATOS DEL CLIENTE`) a 94 pt — de sobra de margen para el corte.
         */
        const val MASTHEAD_MARGIN = 30f

        /** Textos que jamás son ancla de sección aunque cumplan la regla geométrica. */
        val ANCHOR_BLACKLIST = setOf("DOCUMENTACIÓN")

        /** Hueco horizontal máximo entre una casilla y el texto de su ancla. */
        const val ANCHOR_CHECKBOX_GAP_MAX = 25f

        /** Tolerancia vertical (centro del widget vs. línea de texto) para emparejar casilla y ancla. */
        const val ANCHOR_CHECKBOX_Y_TOLERANCE = 12f

        /** Tolerancia vertical para considerar dos palabras en la misma fila al etiquetar. */
        const val LABEL_ROW_TOLERANCE = 6f

        /** Hueco vertical máximo para que una línea "de encima" sirva de etiqueta. */
        const val LABEL_ABOVE_MAX_GAP = 20f

        /** Hueco vertical máximo entre una celda de tabla y la cabecera de su columna. */
        const val TABLE_HEADER_MAX_GAP = 30f
    }
}
