package com.mejoresiagratis.rellenador.data.model

/**
 * Ediciones **manuales** del usuario sobre un [FormSchema] ya construido (y, si procede, ya
 * etiquetado por visión — ver `FieldLabeler`/`SchemaLabeling`).
 *
 * Diferencia clave con `SchemaLabeling.apply()`: aquí SIEMPRE se marca [LabelSource.USUARIO],
 * sin consultar el `labelSource` anterior. Es, precisamente, la corrección manual que
 * `SchemaLabeling` debe respetar después — si aquí también mirásemos "no pisar USUARIO", una
 * segunda corrección de la misma persona no podría cambiar de opinión.
 *
 * Fase 4 (editor de mapeo/etiquetas): permite corregir a mano lo que propuso la fase 3, no sólo
 * campo a campo.
 */
object SchemaEditing {

    /**
     * Cambia la etiqueta de TODOS los [FormField] cuyo [FormField.name] coincide con [name], en
     * cualquier sección y contenedor (campo suelto, celda de tabla, bloque repetido).
     *
     * El mismo mecanismo cubre dos casos de uso distintos porque `name` es, precisamente, cómo
     * el AcroForm agrupa los radios de forma nativa:
     * - **Campo suelto**: su `name` normalmente es único → se actualiza uno solo.
     * - **Grupo de opción completo (RADIO)**: varias casillas comparten `name` (se distinguen
     *   por [FormField.onState], ver v0.10.2) → una sola llamada las actualiza todas.
     *
     * No toca [TableColumn.label] — para eso está [setColumnLabel].
     */
    fun setFieldLabel(schema: FormSchema, name: String, label: String): FormSchema {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return schema

        fun relabel(field: FormField): FormField =
            if (field.name == name) field.copy(label = trimmed, labelSource = LabelSource.USUARIO)
            else field

        return schema.copy(
            sections = schema.sections.map { section ->
                section.copy(
                    fields = section.fields.map(::relabel),
                    rows = section.rows.map { row ->
                        row.copy(cells = row.cells.mapValues { (_, field) -> relabel(field) })
                    },
                    blocks = section.blocks.map { block -> block.map(::relabel) },
                )
            }
        )
    }

    /**
     * Asigna (o quita, con [canonical] a `null`) la **clave canónica transversal** de todos los
     * [FormField] cuyo [FormField.name] coincide con [name]. Tanda 5·4f.
     *
     * Es la pieza que faltaba para que subir un PDF ajeno sirva de algo más que teclear cada
     * hueco: [FormField.canonical] es lo único que conecta un campo del PDF con un dato
     * transversal ([CanonicalKeys]), y de ahí salen tres cosas que ya están escritas y hasta ahora
     * estaban mudas con cualquier PDF que no fuera el de Orange — autorrelleno desde el perfil,
     * validación por tipo y teclado adecuado (ver `FieldKeys.canonicalOf`).
     *
     * Etiquetar **no** asignaba canónicas: `SchemaLabeling`/`FieldLabeler` sólo escriben
     * [FormField.label], y el editor de la fase 4 no tenía por dónde tocar esto.
     *
     * Hasta la tanda 5·4i una canónica no se repartía entre dos campos: asignarla a uno se la
     * quitaba al que la tuviera antes. Eso hacía imposible lo que pide 5·4i — que un mismo dato
     * (el nombre del cliente, su dirección) pueda vivir en varios huecos AFINES del PDF cuando el
     * usuario así lo confirma (ver `AffinityGroup`, que propone los candidatos, nunca los aplica
     * solo). Ahora [canonical] simplemente se escribe en [name]: si otro campo ya la tenía, la
     * sigue teniendo, y ambos comparten dato. `WizardViewModel.pushUndo()` sabe repartir el valor
     * a todos los campos con la misma canónica al escribirlo (ver `CanonicalSiblings.expand`).
     *
     * Se marca [LabelSource.USUARIO] por el mismo motivo que [setFieldLabel]: es una decisión
     * manual y ningún reetiquetado posterior debe pisarla.
     */
    fun setCanonical(schema: FormSchema, name: String, canonical: String?): FormSchema {
        val target = canonical?.trim()?.takeIf { it.isNotEmpty() }

        fun remap(field: FormField): FormField =
            if (field.name == name) field.copy(canonical = target, labelSource = LabelSource.USUARIO)
            else field

        return schema.copy(
            sections = schema.sections.map { section ->
                section.copy(
                    fields = section.fields.map(::remap),
                    rows = section.rows.map { row ->
                        row.copy(cells = row.cells.mapValues { (_, field) -> remap(field) })
                    },
                    blocks = section.blocks.map { block -> block.map(::remap) },
                )
            }
        )
    }

    /**
     * Cambia la etiqueta de una [TableColumn] entera (la cabecera). Las celdas individuales de
     * esa columna no se tocan — la etiqueta visible de una celda es la de su columna, igual que
     * hace `SchemaLabeling.relabelRow()` con las etiquetas por visión.
     */
    fun setColumnLabel(schema: FormSchema, sectionId: String, columnId: String, label: String): FormSchema {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return schema

        return schema.copy(
            sections = schema.sections.map { section ->
                if (section.id != sectionId) return@map section
                section.copy(
                    columns = section.columns.map { column ->
                        if (column.id == columnId) {
                            column.copy(label = trimmed, labelSource = LabelSource.USUARIO)
                        } else column
                    }
                )
            }
        )
    }

    /** Cambia el título de una sección entera (por ejemplo, "Tabla 1" → "Tarifa Telefonía Fija"). */
    fun setSectionTitle(schema: FormSchema, sectionId: String, title: String): FormSchema {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return schema
        return schema.copy(
            sections = schema.sections.map { section ->
                if (section.id == sectionId) section.copy(title = trimmed) else section
            }
        )
    }

    /**
     * Agrupa los campos [FieldKind.RADIO] de una sección por [FormField.name]: cada entrada es
     * un grupo de opción completo, listo para editarse como una sola fila en el editor. Se
     * apoya en [FormSection.allFields], que ya sabe recorrer SIMPLE/TABLE/REPEATED_BLOCK por
     * igual.
     */
    fun radioGroups(section: FormSection): List<RadioGroup> =
        section.allFields()
            .filter { it.kind == FieldKind.RADIO }
            .groupBy { it.name }
            .map { (name, options) -> RadioGroup(name, options.sortedBy { it.order }) }
            .sortedBy { it.options.minOf { field -> field.order } }

    /**
     * Un grupo de opción completo: [name] es el campo AcroForm compartido por todas las
     * [options] (una por widget físico). [label] es la etiqueta común del grupo — todas las
     * [options] la comparten porque [setFieldLabel] las actualiza a la vez — y [FormField.onState]
     * / [FormField.optionLabel] distinguen cada opción dentro del grupo.
     */
    data class RadioGroup(val name: String, val options: List<FormField>) {
        val label: String get() = options.firstOrNull()?.label ?: name
        val labelSource: LabelSource get() = options.firstOrNull()?.labelSource ?: LabelSource.NOMBRE_REAL
    }
}
