package com.mejoresiagratis.rellenador.ui.wizard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.mejoresiagratis.rellenador.data.model.BuiltinSchemas
import com.mejoresiagratis.rellenador.data.model.CanonicalKeys
import com.mejoresiagratis.rellenador.data.model.ContractFields
import com.mejoresiagratis.rellenador.data.model.FieldKind
import com.mejoresiagratis.rellenador.data.model.FieldKeys
import com.mejoresiagratis.rellenador.data.model.FormField
import com.mejoresiagratis.rellenador.data.validation.FieldNormalizer
import com.mejoresiagratis.rellenador.data.validation.FieldValidator
import com.mejoresiagratis.rellenador.ui.components.ExpressiveButton

// Tanda 3 — agrupación de los 21 campos canónicos en secciones temáticas, fiel a
// ContractFields.CANON (ver Extraction.kt). "Fecha" se trata aparte porque sus 3
// claves (Fecha/de/año) se muestran como una sola fila compacta día/mes/año, no
// como 3 campos apilados sueltos.
//
// Tanda 5·2 — las tres claves de fecha salían de las canónicas vía `BuiltinSchemas.realKeyFor`
// en vez del literal `setOf("Fecha", "de", "año")`.
// Tanda 5·3 — y ya no pueden ser una constante de fichero: dependen del PDF que se esté
// rellenando, así que se resuelven con el `FieldKeys` que llega por parámetro.
private fun fechaKeysOf(keys: FieldKeys): Set<String> =
    listOf(CanonicalKeys.FECHA_DIA, CanonicalKeys.FECHA_MES, CanonicalKeys.FECHA_ANIO)
        .mapNotNull { BuiltinSchemas.realKeyFor(it) }
        .map(keys::real)
        .toSet()

/**
 * Nombre real del campo Provincia que corresponde a un CP dado.
 *
 * Tanda 5·2 — se resolvía por canónica (`BuiltinSchemas.provinciaKeyFor`) en vez de por la
 * convención de nombre `_2` de Orange (docs/PLAN_FASE_5.md, hallazgo 2.6).
 * Tanda 5·3 — y además se traduce al nombre real del PDF actual, porque desde esta tanda `key`
 * ya es un nombre real y el hermano que hay que leer también tiene que serlo.
 */
private fun provinciaKeyFor(key: String, keys: FieldKeys): String {
    val canonKey = keys.canonKeyOf(key) ?: key
    val provinciaCanon = BuiltinSchemas.provinciaKeyFor(canonKey)
        ?: if (canonKey.endsWith("_2")) "Provincia_2" else "Provincia"
    return keys.real(provinciaCanon)
}

// Las secciones ya no viven aquí: se reciben como parámetro. Ver `FillSections.kt` y la tanda
// 5·1 de `docs/PLAN_FASE_5.md`.

/**
 * Paso 4 — Relleno editable con validación en vivo (dígitos de control).
 * Tanda 3: secciones con fondo `surfaceContainer`, progreso real, tipo de
 * identificación editable (antes solo lo fijaba la IA, sin forma de corregirlo),
 * copia rápida fiscal → comercio, y fecha en fila compacta.
 */
// Usa `motionScheme` (M3 Expressive, experimental) para el rebote del contador y el
// progreso del hero: cada función que lo use necesita su propio @OptIn.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FillStep(
    state: WizardUiState,
    vm: WizardViewModel,
    /**
     * Secciones a pintar. Quien llama decide cuáles: hoy siempre las de `CANON`
     * ([canonFillSections]); en la tanda 5·4 vendrán del `FormSchema` del PDF subido.
     */
    sections: List<FillSection>,
    /**
     * Traductor entre claves de `CANON` y los nombres reales del PDF actual (tanda 5·3).
     * `FieldKeys.IDENTITY` para el contrato de Orange, donde ambas coinciden.
     */
    keys: FieldKeys = FieldKeys.IDENTITY,
) {
    var showHistory by remember { mutableStateOf(false) }
    if (showHistory) HistoryPanel(vm, onDismiss = { showHistory = false })

    // v0.8.2 — confirmación de cada decisión, con DESHACER a mano. Antes el cambio
    // ocurría en silencio y el usuario no sabía qué campos se habían tocado.
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun confirm(message: String) {
        scope.launch {
            snackbarHost.currentSnackbarData?.dismiss()
            val res = snackbarHost.showSnackbar(
                message = message,
                actionLabel = "DESHACER",
                withDismissAction = true,
                duration = SnackbarDuration.Short,
            )
            if (res == SnackbarResult.ActionPerformed) vm.undoLast()
        }
    }

    // Tanda 5·3 — la etiqueta ya no se busca en `CANON` por la clave (que ahora es un nombre real
    // que puede no estar en `CANON`), sino que la resuelve `FieldKeys`: nombre real -> clave de
    // `CANON` -> etiqueta. Si el campo no está mapeado, se muestra su propio nombre.
    val fechaKeys = remember(keys) { fechaKeysOf(keys) }

    /**
     * Tanda 5·4d (2ª mitad) — índice `nombre real -> entradas del esquema`, que es lo que permite
     * a [FieldRow] pintar una casilla como casilla y un radio como radio.
     *
     * Va aquí y con `remember` a propósito: son varios cientos de campos (472 en el contrato de
     * Aire) y resolverlo dentro de cada fila sería recorrer el esquema entero por fila en cada
     * recomposición. Un grupo de radio son varias entradas con el mismo `name`, así que el valor
     * del mapa es una lista, no un campo.
     *
     * Vacío cuando no hay esquema activo (flujo Orange/CANON): entonces todo se pinta como texto,
     * que es exactamente el comportamiento anterior a esta tanda.
     */
    val fieldsByName = remember(state.activeSchema) {
        state.activeSchema?.allFields()?.groupBy { it.name }.orEmpty()
    }

    fun isFieldOk(key: String): Boolean {
        val v = state.fieldValues[key]
        if (v.isNullOrBlank()) return false
        val result = FieldValidator.validate(
            key, v, state.tipoIdentificacion,
            state.fieldValues[provinciaKeyFor(key, keys)],
            canonicalHint = keys.canonicalOf(key),
        )
        return result?.ok != false
    }

    // Se cuenta lo que hay en pantalla, no `CANON.size`: con las secciones parametrizadas, medir
    // contra una constante ajena daría un progreso que no corresponde a lo que se ve. Con `CANON`
    // el número es el mismo (21), así que aquí no cambia nada visible.
    val counted = remember(sections, keys) { countedKeys(sections, keys) }
    val totalFields = counted.size
    val filledFields = counted.count { isFieldOk(it) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // v0.8.3 — hero: resume de un vistazo QUÉ ha hecho la IA y cuánto queda.
            // Va en terciario (frío) para separarlo del naranja de las acciones, y el
            // contador rebota al cambiar para que el progreso se note.
            val aiCount = state.fieldStates.count { it.value == FieldState.AI }
            val progress by animateFloatAsState(
                targetValue = if (totalFields == 0) 0f else filledFields.toFloat() / totalFields,
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                label = "fillProgress"
            )
            val bumpSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
            val settleSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
            val counterScale = remember { Animatable(1f) }
            LaunchedEffect(filledFields) {
                if (filledFields > 0) {
                    counterScale.animateTo(1.22f, bumpSpec)
                    counterScale.animateTo(1f, settleSpec)
                }
            }
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (aiCount > 0) "La IA ha rellenado el contrato"
                                else "Completa el contrato",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                buildString {
                                    append("${state.docUris.size} documento")
                                    if (state.docUris.size != 1) append("s")
                                    if (state.enginesOk.isNotEmpty()) {
                                        append(" · ")
                                        append(state.enginesOk.joinToString(", "))
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Text(
                            "$filledFields/$totalFields",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.scale(counterScale.value)
                        )
                    }
                    // v0.9.0 — motores que no participaron. Al borrar ReviewStep (v0.8.0)
                    // esto dejó de verse: la extracción salía con menos datos y no había
                    // forma de saber que Gemini había agotado cuota. Va aquí, dentro del
                    // hero, porque es contexto de "qué ha hecho la IA", no un error de la app.
                    if (state.engineIssues.isNotEmpty()) {
                        var issuesOpen by rememberSaveable { mutableStateOf(false) }
                        Spacer(Modifier.height(6.dp))
                        TextButton(
                            onClick = { issuesOpen = !issuesOpen },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                Icons.Filled.Warning, contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (state.engineIssues.size == 1) "1 motor no participó"
                                else "${state.engineIssues.size} motores no participaron",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        AnimatedVisibility(
                            visible = issuesOpen,
                            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                            exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec())
                        ) {
                            Column(Modifier.padding(start = 4.dp, top = 2.dp)) {
                                state.engineIssues.forEach { issue ->
                                    Text(
                                        issue.summary,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Text(
                                        issue.failure.hint,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(bottom = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        color = MaterialTheme.colorScheme.tertiary,
                        trackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { showHistory = true }) { Text("Historial") }
            }

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

            // v0.8.0 — aviso de los campos que la IA NO rellenó a propósito (conflicto
            // entre documentos o procedencia dudosa). Bloquean el avance a Firma: son
            // justo los que provocan los errores caros si pasan desapercibidos.
            val pending = state.pendingDecisions()
            if (pending.isNotEmpty()) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        Icon(
                            Icons.Filled.Warning, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Column {
                            Text(
                                if (pending.size == 1) "1 campo necesita tu decisión"
                                else "${pending.size} campos necesitan tu decisión",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "No los he rellenado solo: hay documentos que se contradicen " +
                                    "o que podrían no ser de este cliente.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            // Tanda 5·4h — decir CUÁLES. El aviso contaba los campos pero no los
                            // nombraba, así que con 461 huecos había que bajar buscando el
                            // triángulo a ojo. Con más de seis se corta: la lista dejaría de ser
                            // un aviso y el botón de abajo lleva igualmente al primero.
                            Spacer(Modifier.height(4.dp))
                            Text(
                                pending.take(6).joinToString(" · ") { keys.labelOf(it) } +
                                    if (pending.size > 6) " · y ${pending.size - 6} más" else "",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider()

        /**
         * Tanda 5·4h — ¿este hueco tiene algo que mirar?
         *
         * Con el contrato de Aire son **461 campos y 14 rellenos**: dejar los 447 vacíos en medio
         * de la lista obliga a bajar por todos ellos para encontrar lo que la IA sí trajo. Tiene
         * algo si hay valor, si la IA dejó un estado (propuesta, conflicto o dudoso) o si hay
         * alternativas entre las que elegir.
         */
        fun hasSomething(key: String): Boolean {
            val v = state.fieldValues[key]
            if (!v.isNullOrBlank()) return true
            if (state.fieldCandidates[key].orEmpty().isNotEmpty()) return true
            val fs = state.fieldStates[key] ?: FieldState.EMPTY
            return fs != FieldState.EMPTY
        }

        // Cada sección se queda con lo que tiene algo; el resto se aparta a un desplegable al
        // final, agrupado por su sección de origen para no perder el contexto de dónde estaba.
        val conAlgo = sections
            .map { it.copy(keys = it.keys.filter(::hasSomething)) }
            .filter { it.keys.isNotEmpty() }
        val sinSugerencia = sections
            .map { it.title to it.keys.filterNot(::hasSomething) }
            .filter { it.second.isNotEmpty() }
        val totalSinSugerencia = sinSugerencia.sumOf { it.second.size }

        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 14.dp)) {

            // Tanda 5·4h — la clave era `it.title`, y LazyColumn exige claves ÚNICAS: dos
            // secciones del esquema aprendido con el mismo título (o con el título vacío) hacían
            // que Compose lanzara «Key was already used» EN CUANTO LA SEGUNDA ENTRABA EN
            // COMPOSICIÓN, o sea al bajar por la lista. Con el índice delante la clave es única
            // aunque los títulos se repitan.
            itemsIndexed(
                conAlgo,
                key = { i, section -> "$i:${section.title}" },
            ) { _, section ->
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
                            // v0.8.2 — la sección avisa de sus propios campos por decidir,
                            // para no tener que buscarlos bajando por todo el formulario.
                            val sectionPending = section.keys.count {
                                val fs = state.fieldStates[it]
                                fs == FieldState.CONFLICT || fs == FieldState.WARN
                            }
                            when {
                                sectionPending > 0 -> Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Text(
                                        if (sectionPending == 1) "1 por decidir" else "$sectionPending por decidir",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                    )
                                }
                                sectionComplete -> Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Check, contentDescription = "Sección completa",
                                            tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        }
                        section.keys.forEach { key ->
                            FieldRow(key, keys, state, vm, ::confirm, fieldsByName[key].orEmpty())
                        }
                    }
                }
            }

            // Fecha — fila compacta día/mes/año en vez de 3 campos apilados.
            item {
                val fechaComplete = fechaKeys.all { isFieldOk(it) }
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
                            CompactDateField("Día", keys.real("Fecha"), state, vm, Modifier.weight(1f))
                            CompactDateField("Mes", keys.real("de"), state, vm, Modifier.weight(1f))
                            CompactDateField("Año", keys.real("año"), state, vm, Modifier.weight(1f))
                        }
                    }
                }
            }

            // Tanda 5·4h — los huecos que la IA no tocó, apartados y PLEGADOS. No se esconden
            // (hay que poder rellenarlos a mano), pero dejan de estorbar: con el contrato de Aire
            // son 447 de 461. Van al final y agrupados por su sección de origen, que es el único
            // contexto que dice qué se escribe en cada uno.
            if (totalSinSugerencia > 0) {
                item(key = "sin-sugerencia") {
                    var abierto by rememberSaveable { mutableStateOf(false) }
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(
                                Modifier.fillMaxWidth().clickable { abierto = !abierto },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Sin sugerencias · $totalSinSugerencia",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        "La IA no encontró nada para estos huecos. Los rellenas " +
                                            "a mano si el contrato los necesita.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(
                                    Icons.Filled.KeyboardArrowDown,
                                    contentDescription = if (abierto) "Plegar" else "Desplegar",
                                    modifier = Modifier
                                        .size(22.dp)
                                        .scale(if (abierto) -1f else 1f),
                                )
                            }
                            AnimatedVisibility(
                                visible = abierto,
                                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()),
                            ) {
                                Column(
                                    Modifier.padding(top = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    sinSugerencia.forEach { (titulo, claves) ->
                                        Text(
                                            titulo,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        claves.forEach { key ->
                                            FieldRow(key, keys, state, vm, ::confirm, fieldsByName[key].orEmpty())
                                        }
                                    }
                                }
                            }
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

        // Host del snackbar: encima de la barra de acciones para que no la tape.
        SnackbarHost(snackbarHost, Modifier.padding(horizontal = 12.dp))

        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::back) { Text("Atrás") }
            // Deshacer: revierte la última elección o edición del Relleno. Solo activo si
            // hay algo que deshacer (la pila NO se persiste entre sesiones a propósito).
            FilledTonalIconButton(
                onClick = vm::undoLast,
                enabled = state.undoStack.isNotEmpty()
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Deshacer el último cambio")
            }
            val pendingCount = state.pendingDecisions().size
            ExpressiveButton(
                onClick = vm::next,
                text = if (pendingCount > 0) {
                    if (pendingCount == 1) "Resuelve 1 campo" else "Resuelve $pendingCount campos"
                } else "Ir a la firma",
                enabled = pendingCount == 0,
                trailingIcon = if (pendingCount == 0) Icons.AutoMirrored.Filled.ArrowForward else null,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FieldRow(
    key: String,
    keys: FieldKeys,
    state: WizardUiState,
    vm: WizardViewModel,
    onConfirm: (String) -> Unit = {},
    /**
     * Entradas del esquema que comparten este `name`. Vacío = sin esquema o campo desconocido,
     * y entonces se pinta como texto (comportamiento previo a la 5·4d).
     */
    group: List<FormField> = emptyList(),
) {
    // Tanda 5·4d (2ª mitad) — el CONTROL lo decide el `FieldKind` del esquema, no la pantalla.
    // El reparto al mapa correcto del PDF lo hace después `routeFieldValues()`; aquí sólo se
    // elige con qué se rellena. Basta una entrada `/Sig` para no pintar nada: un hueco de firma
    // no se rellena por ninguna de las dos vías.
    val kinds = group.map { it.kind }.toSet()
    when {
        FieldKind.SIGNATURE in kinds -> return
        FieldKind.RADIO in kinds -> {
            RadioGroupRow(key, keys, group, state, vm, onConfirm)
            return
        }
        FieldKind.CHECKBOX in kinds -> {
            CheckboxRow(key, keys, group.first(), state, vm, onConfirm)
            return
        }
    }

    val value = state.fieldValues[key] ?: ""
    // Tanda 5·3 — `key` es el nombre REAL del campo. La etiqueta y la canónica (que gobierna
    // validación y teclado) se resuelven con `FieldKeys`, porque el nombre real de un PDF que no
    // sea el de Orange no está en `CANON`.
    val label = keys.labelOf(key)
    val canonicalHint = keys.canonicalOf(key)
    val result = FieldValidator.validate(
        key, value, state.tipoIdentificacion,
        state.fieldValues[provinciaKeyFor(key, keys)],
        canonicalHint = canonicalHint,
    )
    val isError = result?.ok == false

    // v0.8.0 — el paso de Relleno absorbe la antigua "Revisión IA":
    //  · fieldStates    dice si el valor lo puso la IA, si hay conflicto o si es dudoso
    //  · fieldOrigins   de qué DOCUMENTO salió (clave para detectar un documento intruso)
    //  · fieldCandidates alternativas elegibles, con su procedencia
    val fState = state.fieldStates[key] ?: FieldState.EMPTY
    val origin = state.fieldOrigins[key]
    val candidates = state.fieldCandidates[key].orEmpty()
    val needsDecision = fState == FieldState.CONFLICT || fState == FieldState.WARN
    var showSheet by rememberSaveable(key) { mutableStateOf(false) }

    val scheme = MaterialTheme.colorScheme
    // El estado tiñe el CONTENEDOR del propio campo (no un fondo detrás): así el campo se
    // lee como una caja rellena, sin doble superficie. La IA usa el terciario (frío) para
    // no competir con el naranja de marca, reservado a las acciones.
    val container = when (fState) {
        FieldState.AI -> scheme.tertiaryContainer
        FieldState.CONFLICT -> scheme.errorContainer
        FieldState.WARN -> scheme.surfaceContainerHighest
        else -> scheme.surfaceContainerLowest
    }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = container,
        unfocusedContainerColor = container,
        errorContainerColor = scheme.errorContainer,
        disabledContainerColor = container,
    )

    // Tanda 5·4i, mitad 2 — candidatos a compartir este valor, entre los que siguen vacíos.
    // `remember` con estas claves porque cambia cuando el propio valor cambia o cuando OTRO
    // campo se rellena/vacía (deja de/empieza a estar vacío y entra o sale de la lista).
    val schema = state.activeSchema
    val affinityCandidates = remember(key, value, schema, state.fieldValues) {
        val filled = schema?.allFields()?.firstOrNull { it.name == key }
        if (schema == null || filled == null || value.isBlank()) {
            emptyList()
        } else {
            val emptyNames = schema.allFields()
                .map { it.name }
                .filter { name -> state.fieldValues[name].isNullOrBlank() }
                .toSet()
            com.mejoresiagratis.rellenador.data.model.AffinityGroup
                .candidatesFor(schema, filled, emptyNames)
        }
    }

    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = { vm.setFieldValue(key, it) },
            label = { Text(label) },
            singleLine = true,
            isError = isError || fState == FieldState.CONFLICT,
            keyboardOptions = keyboardFor(key, canonicalHint),
            placeholder = if (needsDecision) {
                { Text(if (fState == FieldState.CONFLICT) "Elige una opción" else "Revisa la procedencia") }
            } else null,
            trailingIcon = when {
                needsDecision -> {
                    {
                        IconButton(onClick = { showSheet = true }) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = "Este campo necesita tu decisión",
                                tint = if (fState == FieldState.CONFLICT) scheme.error else scheme.tertiary
                            )
                        }
                    }
                }
                candidates.size > 1 -> {
                    {
                        IconButton(onClick = { showSheet = true }) {
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Ver ${candidates.size} alternativas"
                            )
                        }
                    }
                }
                else -> null
            },
            colors = fieldColors,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        )

        if (isError) {
            Text(
                result?.message ?: "",
                color = scheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 12.dp, top = 2.dp)
            )
        }

        // Procedencia: qué documento aportó el dato y qué motores lo respaldan.
        if (origin != null && value.isNotBlank()) {
            Row(
                Modifier.padding(start = 10.dp, top = 5.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Chip de procedencia: DE QUÉ DOCUMENTO salió el dato. Es lo que permite
                // detectar a simple vista un valor que viene del documento equivocado.
                Surface(
                    shape = CircleShape,
                    color = scheme.surfaceContainerLowest.copy(alpha = 0.86f),
                ) {
                    Text(
                        origin.document,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
                if (origin.engines.isNotEmpty()) {
                    Text(
                        origin.engines.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
        }

        // Tanda 5·4i, mitad 2 — afines: huecos vacíos que podrían llevar este mismo dato.
        // Se OFRECEN, nunca se aplican solos (ver `AffinityGroup`); marcar la casilla es lo
        // que de verdad copia el valor (`vm.confirmAffinity`). Al confirmarse deja de estar
        // vacío y desaparece de la lista en la siguiente recomposición — no hace falta
        // "desmarcarlo": no hay vuelta atrás salvo deshacer (Icons.Filled.Refresh, arriba) o
        // vaciar el campo a mano.
        if (affinityCandidates.isNotEmpty()) {
            var showAffinity by rememberSaveable(key) { mutableStateOf(false) }
            Column(Modifier.padding(start = 10.dp, top = 6.dp, end = 12.dp)) {
                TextButton(onClick = { showAffinity = !showAffinity }) {
                    Icon(
                        Icons.Filled.ContentCopy, contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Este dato aparece en otros ${affinityCandidates.size} " +
                            if (affinityCandidates.size == 1) "campo" else "campos",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (showAffinity) {
                    affinityCandidates.forEach { candidate ->
                        val candidateLabel = candidate.label.ifBlank { candidate.name }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.confirmAffinity(key, candidate.name) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = false,
                                onCheckedChange = { vm.confirmAffinity(key, candidate.name) },
                            )
                            Text(candidateLabel, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }

    if (showSheet) {
        CandidateSheet(
            fieldLabel = label,
            state = fState,
            candidates = candidates,
            onPick = {
                vm.chooseCandidate(key, it)
                showSheet = false
                onConfirm("$label: ${it.value}")
            },
            onManual = {
                vm.dismissField(key)
                showSheet = false
                onConfirm("$label: lo rellenas a mano")
            },
            onDismiss = { showSheet = false },
        )
    }
}

/**
 * Hoja de decisión para un campo en conflicto o de procedencia dudosa. Muestra cada
 * alternativa CON SU DOCUMENTO DE ORIGEN — es lo que permite al comercial detectar que un
 * valor viene de un documento que no es de este cliente, algo que el consenso de motores
 * por sí solo no revela (dos motores pueden coincidir leyendo el documento equivocado).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CandidateSheet(
    fieldLabel: String,
    state: FieldState,
    candidates: List<FieldCandidate>,
    onPick: (FieldCandidate) -> Unit,
    onManual: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(fieldLabel, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                when (state) {
                    FieldState.CONFLICT ->
                        "Los documentos no coinciden. Elige cuál va al contrato — debajo de cada opción ves de qué documento sale."
                    FieldState.WARN ->
                        "Estos valores salen de documentos que podrían no ser de este cliente. Compruébalos antes de usarlos."
                    else -> "Alternativas encontradas en los documentos."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))

            candidates.forEach { c ->
                ElevatedCard(
                    onClick = { onPick(c) },
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (c.origin.risky) scheme.tertiaryContainer
                        else scheme.surfaceContainerHigh
                    ),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(c.value, style = MaterialTheme.typography.titleSmall)
                        if (c.origin.note.isNotBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                c.origin.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(7.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AssistChip(
                                onClick = { onPick(c) },
                                label = { Text(c.origin.document) },
                                modifier = Modifier.height(28.dp)
                            )
                            if (c.origin.engines.isNotEmpty()) {
                                Text(
                                    c.origin.engines.joinToString(", "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                            if (c.origin.risky) {
                                Text(
                                    "revisar",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.tertiary
                                )
                            }
                        }
                        if (c.linked.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Rellena también: ${c.linked.entries.joinToString(" · ") { it.value }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) {
                Text("Dejar en blanco · lo relleno a mano")
            }
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

/** Teclado adecuado por tipo de campo.
 *
 * Tanda 5·2b — decidía por `norm(key.substringBefore("_"))`, heurística sobre el nombre; ahora
 * por canónica, con el nombre como respaldo (docs/PLAN_FASE_5.md, hallazgo 2.6). Un teclado
 * equivocado no corrompe datos, pero con Aire salían todos como texto: teclear un CP o un
 * teléfono en el teclado alfabético es peor de lo que parece en un formulario de 481 campos.
 */
private fun keyboardFor(key: String, canonicalHint: String? = null): KeyboardOptions {
    val canonical = canonicalHint ?: BuiltinSchemas.canonicalFor(key)
    val b by lazy { FieldNormalizer.norm(key.substringBefore("_")) }
    val type = when {
        canonical == CanonicalKeys.TELEFONO || (canonical == null && b == "telefono") ->
            KeyboardType.Phone
        canonical == CanonicalKeys.EMAIL_COMERCIAL ||
            canonical == CanonicalKeys.EMAIL_FACTURACION ||
            (canonical == null && b.startsWith("email")) -> KeyboardType.Email
        canonical == CanonicalKeys.CP || canonical == CanonicalKeys.CP_2 ||
            (canonical == null && b == "cp") -> KeyboardType.Number
        else -> KeyboardType.Text
    }
    return KeyboardOptions(keyboardType = type, imeAction = ImeAction.Next)
}

/**
 * Casilla suelta — tanda 5·4d (2ª mitad).
 *
 * Se guarda el **estado real** del PDF (`onState`, leído del `/AP /N` al construir el esquema) y
 * no un `"On"` inventado, porque los estados de un PDF ajeno no siguen ninguna convención: en el
 * contrato de Aire son `/Sí`, `/0`..`/5`, `/Opción1` (ver v0.9.7). Apagado es cadena vacía, nunca
 * `"0"` — `"0"` es un estado ENCENDIDO válido en Aire, y confundirlos es el fallo que esta tanda
 * viene a cerrar. `routeFieldValues()` aplica la misma regla al otro lado.
 */
@Composable
private fun CheckboxRow(
    key: String,
    keys: FieldKeys,
    field: FormField,
    state: WizardUiState,
    vm: WizardViewModel,
    onConfirm: (String) -> Unit = {},
) {
    val label = field.label.ifBlank { keys.labelOf(key) }
    val stored = state.fieldValues[key] ?: ""
    val checked = stored.isNotBlank() &&
        !stored.equals(ContractFields.CHECKBOX_OFF, ignoreCase = true)
    val onState = field.onState ?: ContractFields.CHECKBOX_ON

    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable {
                val next = if (checked) "" else onState
                vm.setFieldValue(key, next)
                onConfirm("$label: ${if (checked) "sin marcar" else "marcada"}")
            }
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

/**
 * Grupo de opciones excluyentes — tanda 5·4d (2ª mitad).
 *
 * Las entradas comparten el `name` (es UN campo del AcroForm) y se distinguen por `onState`, así
 * que la selección se guarda como el `onState` de la opción elegida. La etiqueta de cada opción
 * es su `optionLabel`; si el etiquetado no lo resolvió se cae al propio `onState`, que al menos
 * es el valor real y no una invención.
 *
 * Volver a pulsar la opción marcada la desmarca: un radio del AcroForm admite `/Off`, y sin esto
 * un grupo mal tocado no tiene vuelta atrás sin reiniciar el asistente.
 */
@Composable
private fun RadioGroupRow(
    key: String,
    keys: FieldKeys,
    group: List<FormField>,
    state: WizardUiState,
    vm: WizardViewModel,
    onConfirm: (String) -> Unit = {},
) {
    val title = group.firstOrNull { it.label.isNotBlank() }?.label ?: keys.labelOf(key)
    val stored = (state.fieldValues[key] ?: "").removePrefix("/")

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
        )
        group.forEach { option ->
            val optionState = option.onState ?: return@forEach
            val optionLabel = option.optionLabel?.ifBlank { null } ?: optionState
            val selected = stored.equals(optionState, ignoreCase = true)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable {
                        val next = if (selected) "" else optionState
                        vm.setFieldValue(key, next)
                        onConfirm("$title: ${if (selected) "sin elegir" else optionLabel}")
                    }
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RadioButton(selected = selected, onClick = null)
                Text(
                    optionLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
