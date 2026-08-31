package com.mejoresiagratis.rellenador.ui.wizard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mejoresiagratis.rellenador.data.model.FieldKind
import com.mejoresiagratis.rellenador.data.model.FormField
import com.mejoresiagratis.rellenador.data.model.FormSchema
import com.mejoresiagratis.rellenador.data.model.FormSection
import com.mejoresiagratis.rellenador.data.model.LabelSource
import com.mejoresiagratis.rellenador.data.model.SchemaEditing
import com.mejoresiagratis.rellenador.data.model.SectionKind
import com.mejoresiagratis.rellenador.data.model.TableColumn

/**
 * Editor de mapeo/etiquetas — **Fase 4**. No confundir con [MappingEditor], que es del flujo
 * legado Orange/CANON (mapea un PDF propio a las 22 claves fijas). Este trabaja sobre un
 * [FormSchema] dinámico (fase 2 en adelante): revisa y corrige a mano lo que propuso el
 * etiquetado por visión de la fase 3 (`FieldLabeler`).
 *
 * Toda corrección aquí pasa por [SchemaEditing], que siempre marca [LabelSource.USUARIO] — la
 * regla que no se negocia es que un reetiquetado automático posterior (`SchemaLabeling.apply`)
 * nunca la pisa.
 *
 * Aún NO engancha al asistente: [schema] se recibe y se devuelve por callback, sin tocar
 * `WizardViewModel` ni `FillStep`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelEditor(
    schema: FormSchema,
    onSchemaChange: (FormSchema) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val totalFields = schema.allFields().size
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            Text("Revisar etiquetas", style = MaterialTheme.typography.titleMedium)
            Text(
                "\"${schema.title}\" · ${schema.sections.size} secciones · $totalFields campos. " +
                    "Corrige lo que la IA no haya acertado; tu corrección manda siempre.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(schema.sections, key = { it.id }) { section ->
                SectionEditor(
                    schema = schema,
                    section = section,
                    onSchemaChange = onSchemaChange,
                )
            }
        }

        HorizontalDivider()
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack) { Text("Atrás") }
            Button(onClick = onDone, modifier = Modifier.weight(1f)) { Text("Confirmar etiquetas") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionEditor(
    schema: FormSchema,
    section: FormSection,
    onSchemaChange: (FormSchema) -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = section.title,
                onValueChange = {
                    onSchemaChange(SchemaEditing.setSectionTitle(schema, section.id, it))
                },
                label = { Text("Título de la sección") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            when (section.kind) {
                SectionKind.SIMPLE -> SimpleSectionBody(schema, section, onSchemaChange)
                SectionKind.TABLE -> TableSectionBody(schema, section, onSchemaChange)
                SectionKind.REPEATED_BLOCK -> RepeatedBlockSectionBody(schema, section, onSchemaChange)
            }
        }
    }
}

/** Campos sueltos: los RADIO se agrupan por [FormField.name] y se editan como un solo grupo. */
@Composable
private fun SimpleSectionBody(
    schema: FormSchema,
    section: FormSection,
    onSchemaChange: (FormSchema) -> Unit,
) {
    val loose = section.fields.filter { it.kind != FieldKind.RADIO }
    val groups = SchemaEditing.radioGroups(section)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        loose.forEach { field ->
            LabelFieldRow(field) { newLabel ->
                onSchemaChange(SchemaEditing.setFieldLabel(schema, field.name, newLabel))
            }
        }
        groups.forEach { group ->
            RadioGroupRow(group) { newLabel ->
                onSchemaChange(SchemaEditing.setFieldLabel(schema, group.name, newLabel))
            }
        }
    }
}

/** Tabla: se edita la cabecera de cada columna, no celda a celda. */
@Composable
private fun TableSectionBody(
    schema: FormSchema,
    section: FormSection,
    onSchemaChange: (FormSchema) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "${section.columns.size} columnas · ${section.rows.size} filas",
            style = MaterialTheme.typography.bodySmall,
        )
        section.columns.forEach { column ->
            ColumnRow(column) { newLabel ->
                onSchemaChange(SchemaEditing.setColumnLabel(schema, section.id, column.id, newLabel))
            }
        }
    }
}

/**
 * Bloques repetidos (p.ej. "Dirección de instalación 1..4"): cada bloque tiene sus propios
 * nombres de AcroForm, así que hoy se editan bloque a bloque — [SchemaEditing.setFieldLabel]
 * no propaga entre bloques porque no comparten `name`. Pendiente de una clave de posición común
 * si algún día se quiere corregir los 4 de una vez.
 */
@Composable
private fun RepeatedBlockSectionBody(
    schema: FormSchema,
    section: FormSection,
    onSchemaChange: (FormSchema) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        section.blocks.forEachIndexed { i, block ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Bloque ${i + 1}", style = MaterialTheme.typography.labelLarge)
                block.filter { it.kind != FieldKind.RADIO }.forEach { field ->
                    LabelFieldRow(field) { newLabel ->
                        onSchemaChange(SchemaEditing.setFieldLabel(schema, field.name, newLabel))
                    }
                }
            }
        }
    }
}

@Composable
private fun LabelFieldRow(field: FormField, onLabelChange: (String) -> Unit) {
    Column {
        Text(
            field.name,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = field.label,
                onValueChange = onLabelChange,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            LabelSourceChip(field.labelSource)
        }
    }
}

@Composable
private fun ColumnRow(column: TableColumn, onLabelChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = column.label,
            onValueChange = onLabelChange,
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        LabelSourceChip(column.labelSource)
    }
}

/** Un grupo de opción completo (RADIO): una sola etiqueta editable + sus opciones, de solo lectura. */
@Composable
private fun RadioGroupRow(group: SchemaEditing.RadioGroup, onLabelChange: (String) -> Unit) {
    Column {
        Text(
            group.name,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = group.label,
                onValueChange = onLabelChange,
                label = { Text("Grupo de opción (${group.options.size})") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            LabelSourceChip(group.labelSource)
        }
        Text(
            group.options.joinToString("  ·  ") { it.optionLabel ?: it.onState ?: "?" },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
        )
    }
}

@Composable
private fun LabelSourceChip(source: LabelSource) {
    val text = when (source) {
        LabelSource.NOMBRE_REAL -> "nombre PDF"
        LabelSource.VISION -> "IA"
        LabelSource.USUARIO -> "manual"
    }
    AssistChip(onClick = {}, enabled = false, label = { Text(text, style = MaterialTheme.typography.labelSmall) })
}
