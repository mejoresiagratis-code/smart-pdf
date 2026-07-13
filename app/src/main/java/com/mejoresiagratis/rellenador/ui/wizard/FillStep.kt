package com.mejoresiagratis.rellenador.ui.wizard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.mejoresiagratis.rellenador.data.model.CanonField
import com.mejoresiagratis.rellenador.data.model.ContractFields
import com.mejoresiagratis.rellenador.data.validation.FieldNormalizer
import com.mejoresiagratis.rellenador.data.validation.FieldValidator
import com.mejoresiagratis.rellenador.ui.components.ExpressiveButton

// Tanda 3 — agrupación de los 21 campos canónicos en secciones temáticas, fiel a
// ContractFields.CANON (ver Extraction.kt). "Fecha" se trata aparte porque sus 3
// claves (Fecha/de/año) se muestran como una sola fila compacta día/mes/año, no
// como 3 campos apilados sueltos.
private val FECHA_KEYS = setOf("Fecha", "de", "año")

private data class Section(val title: String, val keys: List<String>, val showCopyFiscal: Boolean = false)

private val SECTIONS = listOf(
    Section("Empresa / Identificación", listOf(
        "Nombre  Razón Social", "Nombre Comercial", "NIE", "Nombre representante", "NIF representante",
        // Añadido tras auditoría contra el AcroForm real y la web (existía en el PDF y
        // en el prompt de IA, pero no estaba conectado en Android).
        "Actividad principal del negocio"
    )),
    Section("Dirección fiscal", listOf("Dirección", "CP", "Población", "Provincia")),
    Section("Dirección comercio / PdV", listOf("Dirección_2", "CP_2", "Población_2", "Provincia_2"), showCopyFiscal = true),
    Section("Contacto", listOf("Teléfono", "Email Comercial", "Email  Facturación")),
    Section("Datos bancarios", listOf("Datos bancarios del DISTRIBUIDOR")),
)

/**
 * Paso 4 — Relleno editable con validación en vivo (dígitos de control).
 * Tanda 3: secciones con fondo `surfaceContainer`, progreso real, tipo de
 * identificación editable (antes solo lo fijaba la IA, sin forma de corregirlo),
 * copia rápida fiscal → comercio, y fecha en fila compacta.
 */
@Composable
fun FillStep(state: WizardUiState, vm: WizardViewModel) {
    var showHistory by remember { mutableStateOf(false) }
    if (showHistory) HistoryPanel(vm, onDismiss = { showHistory = false })

    val canonByKey = remember { ContractFields.CANON.associateBy { it.key } }

    fun isFieldOk(key: String): Boolean {
        val v = state.fieldValues[key]
        if (v.isNullOrBlank()) return false
        val provKey = if (key.endsWith("_2")) "Provincia_2" else "Provincia"
        val result = FieldValidator.validate(key, v, state.tipoIdentificacion, state.fieldValues[provKey])
        return result?.ok != false
    }

    val totalFields = ContractFields.CANON.size
    val filledFields = ContractFields.CANON.count { isFieldOk(it.key) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Paso 4 · Relleno", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text("$filledFields/$totalFields", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { showHistory = true }) { Text("Historial") }
            }
            LinearProgressIndicator(
                progress = { if (totalFields == 0) 0f else filledFields.toFloat() / totalFields },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer
            )

            // Tipo de identificación — corregible a mano. Antes solo lo fijaba la IA
            // y no había forma de arreglarlo si se equivocaba, pese a que determina
            // qué casilla del PDF (NIF/CIF) se marca al firmar.
            Text("Tipo de identificación", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("NIF", "CIF", "NIE").forEachIndexed { i, tipo ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = 3),
                        selected = state.tipoIdentificacion?.uppercase() == tipo,
                        onClick = { vm.setTipoIdentificacion(tipo) }
                    ) { Text(tipo) }
                }
            }
        }
        HorizontalDivider()

        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 14.dp)) {

            items(SECTIONS, key = { it.title }) { section ->
                val sectionComplete = section.keys.all { isFieldOk(it) }
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(section.title, style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (section.showCopyFiscal) {
                                AssistChip(
                                    onClick = vm::copyFiscalToComercio,
                                    label = { Text("Copiar fiscal") },
                                    leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            if (sectionComplete) {
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Check, contentDescription = "Sección completa",
                                            tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        }
                        section.keys.forEach { key ->
                            FieldRow(key, canonByKey[key], state, vm)
                        }
                    }
                }
            }

            // Fecha — fila compacta día/mes/año en vez de 3 campos apilados.
            item {
                val fechaComplete = FECHA_KEYS.all { isFieldOk(it) }
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Fecha", style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.weight(1f))
                            if (fechaComplete) {
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Check, contentDescription = "Sección completa",
                                            tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CompactDateField("Día", "Fecha", state, vm, Modifier.weight(1f))
                            CompactDateField("Mes", "de", state, vm, Modifier.weight(1f))
                            CompactDateField("Año", "año", state, vm, Modifier.weight(1f))
                        }
                    }
                }
            }

            item {
                AssistChip(
                    onClick = { },
                    label = { Text("${state.responsableComercial} (automático · editar en Ajustes)") },
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::back) { Text("Atrás") }
            ExpressiveButton(
                onClick = vm::next,
                text = "Ir a la firma",
                trailingIcon = Icons.AutoMirrored.Filled.ArrowForward,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FieldRow(key: String, field: CanonField?, state: WizardUiState, vm: WizardViewModel) {
    val value = state.fieldValues[key] ?: ""
    val provKey = if (key.endsWith("_2")) "Provincia_2" else "Provincia"
    val result = FieldValidator.validate(key, value, state.tipoIdentificacion, state.fieldValues[provKey])
    val isError = result?.ok == false

    Column {
        OutlinedTextField(
            value = value,
            onValueChange = { vm.setFieldValue(key, it) },
            label = { Text(field?.label ?: key) },
            singleLine = true,
            isError = isError,
            keyboardOptions = keyboardFor(key),
            modifier = Modifier.fillMaxWidth()
        )
        if (isError) {
            Text(result?.message ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 12.dp, top = 2.dp))
        }
    }
}

@Composable
private fun CompactDateField(label: String, key: String, state: WizardUiState, vm: WizardViewModel, modifier: Modifier = Modifier) {
    val value = state.fieldValues[key] ?: ""
    OutlinedTextField(
        value = value,
        onValueChange = { vm.setFieldValue(key, it) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        modifier = modifier
    )
}

/** Teclado adecuado por tipo de campo. */
private fun keyboardFor(key: String): KeyboardOptions {
    val b = FieldNormalizer.norm(key.substringBefore("_"))
    val type = when {
        b == "telefono" -> KeyboardType.Phone
        b.startsWith("email") -> KeyboardType.Email
        b == "cp" -> KeyboardType.Number
        else -> KeyboardType.Text
    }
    return KeyboardOptions(keyboardType = type, imeAction = ImeAction.Next)
}
