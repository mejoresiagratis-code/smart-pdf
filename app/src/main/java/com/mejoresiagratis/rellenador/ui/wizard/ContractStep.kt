package com.mejoresiagratis.rellenador.ui.wizard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Importamos nuestros componentes globales
import com.mejoresiagratis.rellenador.ui.components.ExpressiveButton

/**
 * Visibilidad de la tarjeta del contrato heredado de Orange/MASORANGE (v0.9.6).
 *
 * Está en `false` porque ya no se trabaja con ese operador y no tiene sentido ofrecerlo como
 * opción. **No es un borrado**: el camino `ContractSource.DEFAULT` sigue completo y funcional
 * (asset, CANON, autorrelleno del responsable, páginas de firma calibradas), así que ese
 * contrato se sigue rellenando igual de bien si se sube como PDF propio. Sólo desaparece el
 * atajo de la interfaz.
 */
private const val SHOW_LEGACY_DEFAULT_CONTRACT = false

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContractStep(state: WizardUiState, vm: WizardViewModel) {
    var showMapping by remember { mutableStateOf(false) }

    // 0.10.12 — la revisión ya NO depende de `needsMapping`. Ese flag se apaga en cuanto hay una
    // plantilla guardada para la huella, así que al volver a subir el mismo PDF (o uno ya
    // conocido) el botón decía «Continuar» y no había forma de llegar a la revisión. Ahora se
    // ofrece siempre que haya un PDF propio con campos: revisar es barato y el usuario puede
    // querer corregir una etiqueta, o comprobar que se reutilizó lo correcto.
    val canReview = state.contractSource == ContractSource.USER &&
        state.userContractUri != null &&
        state.userFieldNames.isNotEmpty()
    val isMappingState = showMapping && canReview

    // 1. Transición Fluida: Evitamos el "return" abrupto que rompe el ciclo de vida de Compose
    AnimatedContent(
        targetState = isMappingState,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "ContractStepTransition"
    ) { isMapping ->
        // El URI se lee dentro del contenido y no se fuerza con `!!`: durante la animación, el
        // fotograma saliente sigue componiéndose con el `isMapping` viejo pero con el `state`
        // nuevo, así que si el usuario cambia de contrato justo ahí, `userContractUri` puede ser
        // nulo aunque `isMappingState` fuera cierto al arrancar la transición.
        val reviewUri = state.userContractUri
        if (isMapping && reviewUri != null) {
            // 0.10.12 — aquí se mostraba `MappingEditor`, que preguntaba por las 21 canónicas de
            // Orange («Razón social», «Nombre comercial»…) sea cual sea el PDF: con el contrato de
            // Aire cargado, una lista plana de 21 destinos ajenos delante de 481 campos propios.
            // Ahora se muestra el MISMO panel que Ajustes › «Analizar y etiquetar un PDF»: las
            // secciones y los campos del PDF subido, con su etiquetado por IA y la corrección a
            // mano. `MappingEditor` NO se retira — sigue en el repo y sigue sirviendo para enlazar
            // canónicas cuando el PDF es un contrato conocido; lo que se quita es que sea la única
            // puerta del paso 1.
            ContractSchemaReview(
                uri = reviewUri,
                onDone = { schema ->
                    vm.adoptSchema(schema)
                    vm.rememberTemplateMapping()
                    showMapping = false
                    vm.next()
                },
                onCancel = { showMapping = false },
            )
        } else {
            ContractSelectionContent(
                state = state,
                vm = vm,
                canReview = canReview,
                onReviewMapping = { showMapping = true }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContractSelectionContent(
    state: WizardUiState,
    vm: WizardViewModel,
    canReview: Boolean,
    onReviewMapping: () -> Unit
) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(vm::chooseUserContract) }

    // 2. Layout Estructural: Separamos el área de scroll del botón de acción anclado.
    // Densidad ajustada 1:1 al mockup — SIN envoltorio ExpressiveSurface (ese
    // Surface extra metía 24dp de padding interno sobre los 16dp del Column, es
    // decir 40dp perdidos por lado en vez de los 20dp del mockup: por eso todo
    // se veía más estrecho y más alto de lo debido, y hacía falta scroll).
    Column(modifier = Modifier.fillMaxSize()) {
        // Elevados al scope de la función (antes vivían dentro del Column de abajo) —
        // el bloque "Estructura detectada" que sigue después también los necesita, y
        // estando dentro de ese Column quedaban fuera de su alcance: exactamente el
        // "Unresolved reference 'isDefault'/'isUser'" que rompió el build de v0.7.7.
        val isDefault = state.contractSource == ContractSource.DEFAULT
        val isUser = state.contractSource == ContractSource.USER
        // 0.10.12 — el nombre real del fichero. El `lastPathSegment` de un URI de SAF es un id
        // opaco (`document:27726`) que no dice nada y cambia entre aperturas; el nombre visible
        // sale de `OpenableColumns.DISPLAY_NAME` y lo resuelve el ViewModel al elegir el PDF.
        val fileName = state.userContractName
            ?: state.userContractUri?.lastPathSegment?.substringAfterLast('/')
            ?: "Seleccionar un PDF del dispositivo"

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp), // 20dp por lado, como el mockup
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // (Título "Paso 1 · ..." y descripción retirados: el stepper superior ya
            // indica en qué paso estás; la pantalla puede respirar más sin el bloque.)
            // 3. Semántica de Grupo: Le dice a los servicios de accesibilidad que esto es un grupo de opciones
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ContractOptionCard(
                    selected = isUser,
                    onClick = { picker.launch(arrayOf("application/pdf")) },
                    headline = "Aportar mi PDF",
                    supporting = fileName,
                    icon = { Icon(Icons.Outlined.UploadFile, contentDescription = null) }
                )

                // Contrato heredado de Orange/MASORANGE: OCULTO en la interfaz desde la v0.9.6
                // (ya no se trabaja con ese operador), pero NO eliminado. Todo el camino
                // `ContractSource.DEFAULT` sigue intacto —`chooseDefaultContract()`, el asset
                // `contrato-base.pdf`, `CANON`, `RESPONSABLE_KEY`, la calibración de firma—
                // para que ese contrato se siga reconociendo y rellenando exactamente igual
                // si alguien lo SUBE como PDF propio, y para que una sesión persistida que ya
                // estuviera en DEFAULT se restaure sin romperse.
                //
                // Poner a `true` para volver a mostrar la tarjeta.
                if (SHOW_LEGACY_DEFAULT_CONTRACT) {
                    ContractOptionCard(
                        selected = isDefault,
                        onClick = vm::chooseDefaultContract,
                        headline = "Contrato Orange/MASORANGE (heredado)",
                        supporting = "Contrato de distribución PdV (54 páginas)",
                        icon = { Icon(Icons.Outlined.Description, contentDescription = null) }
                    )
                }
            }

            // Resumen "Estructura detectada" — mismo feedback inmediato que ya daba la
            // app web al elegir el contrato por defecto o subir uno propio: páginas,
            // campos y huecos de firma detectados, antes de continuar. Se actualiza cada
            // vez que cambia la selección (state.detectingStructure controla el estado
            // de carga mientras SignaturePageDetector analiza el PDF en segundo plano).
            if (isDefault || isUser) {
                val camposDetectados = if (isDefault) {
                    com.mejoresiagratis.rellenador.data.model.ContractFields.CANON.size
                } else {
                    state.userFieldNames.size
                }
                ElevatedCard(shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Estructura detectada", style = MaterialTheme.typography.labelLarge)
                        if (state.detectingStructure) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text("Analizando contrato…", style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            Text(
                                "${state.totalPages} páginas · $camposDetectados campos · " +
                                    "${state.signPages.size} huecos de firma",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isUser && camposDetectados == 0) {
                                Text(
                                    "No se detectaron campos de formulario en este PDF — puede que " +
                                        "no tenga AcroForm, o que sea un PDF escaneado sin campos.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            com.mejoresiagratis.rellenador.ui.components.TipBanner(
                "Usa \"Aportar mi PDF\" solo si tienes una versión del contrato distinta a la incluida por defecto."
            )
        }

        // 4. Acción Primaria Anclada: Fundamental en flujos tipo "Wizard"
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp, // Sutil elevación para separarlo del contenido scrolleable
            shadowElevation = 4.dp
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
                // Usamos nuestro botón global estandarizado
                ExpressiveButton(
                    onClick = { if (canReview) onReviewMapping() else vm.next() },
                    enabled = state.canAdvanceFromContrato,
                    text = if (canReview) "Revisar mapeo" else "Continuar",
                    trailingIcon = Icons.AutoMirrored.Filled.ArrowForward
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContractOptionCard(
    selected: Boolean,
    onClick: () -> Unit,
    headline: String,
    supporting: String,
    icon: @Composable () -> Unit
) {
    // Alineado al mockup: la tarjeta seleccionada pasa a `primaryContainer` (antes
    // usaba `secondaryContainer`) — es el color de marca, no el neutro secundario.
    val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val borderColor = if (selected) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.outlineVariant
    val onContainerColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.5.dp, borderColor),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Icono en contenedor "blob" orgánico (fiel al mockup): relleno con
            // `primary` cuando está seleccionado, `secondaryContainer` si no.
            Surface(
                shape = com.mejoresiagratis.rellenador.ui.components.blobShape(),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CompositionLocalProvider(
                        LocalContentColor provides if (selected) MaterialTheme.colorScheme.onPrimary
                                                    else MaterialTheme.colorScheme.onSecondaryContainer
                    ) { icon() }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(headline, style = MaterialTheme.typography.titleMedium, color = onContainerColor)
                Text(
                    supporting, style = MaterialTheme.typography.bodySmall,
                    color = if (selected) onContainerColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Marca de verificación circular — rellena si está seleccionada, solo
            // contorno si no (mismo patrón que el ".check" del mockup).
            if (selected) {
                Surface(shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Check, contentDescription = "Seleccionado",
                            tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(13.dp))
                    }
                }
            } else {
                Box(
                    Modifier.size(22.dp)
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, androidx.compose.foundation.shape.CircleShape)
                )
            }
        }
    }
}

/**
 * La revisión del paso 1: exactamente el mismo panel que Ajustes › Herramientas › «Analizar y
 * etiquetar un PDF», pero sembrado con el contrato que el usuario ya ha elegido, sin volver a
 * pedirle el fichero (0.10.12).
 *
 * Usa [LabelEditorViewModel] a propósito en vez de duplicar su lógica en `WizardViewModel`: es
 * quien sabe inspeccionar, calcular la huella, reencontrar el esquema guardado, llamar al
 * etiquetado por visión y persistir. Al confirmar guarda y devuelve el esquema hacia arriba para
 * que el asistente lo adopte.
 */
@Composable
private fun ContractSchemaReview(
    uri: android.net.Uri,
    onDone: (com.mejoresiagratis.rellenador.data.model.FormSchema) -> Unit,
    onCancel: () -> Unit,
) {
    val vm: LabelEditorViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val state by vm.state.collectAsState()

    // Sembrar una sola vez por URI: `ensureLoaded` no hace nada si ya está cargado ese mismo PDF,
    // así que abrir y cerrar el panel no vuelve a leer el documento ni pierde las correcciones a
    // medias.
    LaunchedEffect(uri) { vm.ensureLoaded(uri) }

    when {
        state.loading || state.schema == null -> Box(
            Modifier.fillMaxSize(), contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.error == null) {
                    CircularProgressIndicator()
                    Text("Leyendo los campos del PDF…", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(
                        state.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    OutlinedButton(onClick = onCancel) { Text("Volver") }
                }
            }
        }

        else -> SchemaReviewPanel(
            vm = vm,
            state = state,
            onDone = {
                vm.save()
                state.schema?.let(onDone)
            },
            doneLabel = "Confirmar y continuar",
            onSecondary = onCancel,
            secondaryLabel = "Volver",
        )
    }
}
