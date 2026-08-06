package com.mejoresiagratis.rellenador.ui.wizard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mejoresiagratis.rellenador.data.model.AiProvider

/**
 * Aviso previo a mandar documentos a los motores de IA (v0.9.1).
 *
 * ── Por qué existe ──
 * Los documentos que se analizan son DNI, NIE, certificados censales y datos bancarios
 * **de terceros** (el distribuidor y a veces su representante). Enviarlos a un proveedor
 * de IA es una comunicación de datos personales, y buena parte de los motores procesan
 * fuera de la UE. La app web ya paraba aquí desde su versión F7; la app Android los
 * enviaba directamente, que es justo lo contrario de lo esperable en la versión que más
 * se usa a diario.
 *
 * No pretende ser un dictamen legal: es transparencia mínima —qué se envía, a quién, y si
 * sale de la UE— y una decisión explícita antes de que salga el primer byte.
 *
 * ── Diseño ──
 * Los motores fuera de la UE se listan aparte y en `errorContainer`, porque es el dato que
 * cambia la decisión. Si todos son europeos, ese bloque no aparece y el aviso queda corto:
 * no conviene acostumbrar al usuario a descartar una advertencia que casi nunca aplica.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsentSheet(
    engines: List<AiProvider>,
    docCount: Int,
    onAccept: (remember: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val nonEu = engines.filter { !it.eu }
    val eu = engines.filter { it.eu }
    var accepted by remember { mutableStateOf(false) }
    var rememberChoice by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Antes de analizar con IA", style = MaterialTheme.typography.headlineSmall)

            Text(
                "Vas a enviar ${if (docCount == 1) "1 documento" else "$docCount documentos"} " +
                    "a los motores seleccionados para extraer los datos del distribuidor. " +
                    "Pueden contener datos personales de terceros (DNI, NIE, cuenta bancaria).",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )

            // Lo que decide: qué sale de la UE.
            if (nonEu.isNotEmpty()) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = scheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        Icon(
                            Icons.Filled.Warning, contentDescription = null,
                            tint = scheme.onErrorContainer,
                        )
                        Column {
                            Text(
                                "Salen de la UE",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onErrorContainer,
                            )
                            Text(
                                nonEu.joinToString(", ") { it.displayName },
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onErrorContainer,
                            )
                            Text(
                                "Sus servidores están fuera del Espacio Económico Europeo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onErrorContainer,
                            )
                        }
                    }
                }
            }

            if (eu.isNotEmpty()) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = scheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "Procesan en la UE",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onTertiaryContainer,
                        )
                        Text(
                            eu.joinToString(", ") { it.displayName },
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onTertiaryContainer,
                        )
                    }
                }
            }

            Text(
                "Los documentos se analizan de uno en uno y solo se envía lo necesario para " +
                    "extraer los datos del contrato. Las claves de los motores no salen del " +
                    "servidor proxy.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )

            HorizontalDivider(Modifier.padding(vertical = 2.dp))

            // Casilla obligatoria: sin marcarla el botón no se habilita.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(checked = accepted, onCheckedChange = { accepted = it })
                Text(
                    "Tengo autorización del titular para tratar estos documentos",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(checked = rememberChoice, onCheckedChange = { rememberChoice = it })
                Text(
                    "No volver a preguntar en este dispositivo",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Cancelar")
                }
                Button(
                    onClick = { onAccept(rememberChoice) },
                    enabled = accepted,
                    modifier = Modifier.weight(1.4f),
                ) {
                    Text("Analizar")
                }
            }
        }
    }
}
