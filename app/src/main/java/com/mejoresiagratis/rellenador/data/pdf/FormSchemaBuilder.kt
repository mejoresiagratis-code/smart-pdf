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
 */
class FormSchemaBuilder @Inject constructor() {

    fun build(
        fields: List<PdfFieldInspector.Field>,
        fingerprint: String,
        pageCount: Int,
        title: String = "Formulario",
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

        val rows = groupIntoRows(fields.map(::promoted))
        val columnXs = detectColumnXs(rows)
        val sections = mutableListOf<FormSection>()

        var pending = mutableListOf<List<PdfFieldInspector.Field>>()   // filas de tabla en curso
        // Sueltos por página: 5·4 — el objetivo declarado es visual (que el usuario sepa en qué
        // parte del formulario está mientras rellena), y con un contrato de 3 páginas metiéndolo
        // todo en una única sección "Campos" al principio se pierde por completo esa referencia.
        // Se emite una sección simple por página con sus sueltos, intercalada con las tablas en
        // el orden real de aparición.
        val loosePerPage = linkedMapOf<Int, MutableList<PdfFieldInspector.Field>>()

        fun addLoose(row: List<PdfFieldInspector.Field>) {
            if (row.isEmpty()) return
            val bucket = loosePerPage.getOrPut(row.first().page) { mutableListOf() }
            bucket += row
        }

        // Antes de flush(): materializa el bloque de sueltos que quede pendiente **de páginas ya
        // completadas**. La sección simple de una página se emite en cuanto empieza una tabla o
        // una fila de otra página, para que las tablas caigan intercaladas y no siempre al final.
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
                // Antes de cerrar la tabla, cierra los sueltos de páginas anteriores a la que
                // arranca la tabla, para conservar el orden PDF.
                flushLooseBefore(pending.first().first().page)
                sections += tableSection(pending, columnXs, sections.size)
            } else {
                // Una fila suelta no hace tabla: sus campos vuelven a la sección simple.
                pending.forEach { addLoose(it) }
            }
            pending = mutableListOf()
        }

        for (row in rows) {
            val isTableRow = row.count { xKey(it.x) in columnXs } >= MIN_COLS_FOR_ROW
            if (!isTableRow) {
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

        return FormSchema(
            id = "learned:$fingerprint",
            title = title,
            source = SchemaSource.LEARNED,
            fingerprint = fingerprint,
            pageCount = pageCount,
            sections = sections,
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

    // ── Construcción ─────────────────────────────────────────────────────────

    private fun toField(f: PdfFieldInspector.Field, order: Int) = FormField(
        name = f.name,
        // El nombre real como etiqueta provisional. Cuando sea autogenerado
        // (`Campo de texto 116`) lo sustituirá el etiquetado por visión de la fase 3; por eso
        // queda marcado como NOMBRE_REAL y no como USUARIO.
        label = f.name,
        kind = when {
            f.isCheckbox -> FieldKind.CHECKBOX
            f.isRadio -> FieldKind.RADIO
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

            TableColumn(
                id = "c$key",
                label = "Columna ${i + 1}",   // la etiqueta real la pone la fase 3
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
                    FieldRect(
                        x = it.x,
                        y = it.y,
                        width = cellsInColumn.maxOf { c -> c.width },
                        height = it.height,
                    )
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
            title = "Tabla ${index + 1}",
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
    }
}
