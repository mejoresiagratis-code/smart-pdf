package com.mejoresiagratis.rellenador.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mejoresiagratis.rellenador.data.model.AiProvider

// Radio de borde unificado (estilo M3 Expressive 2026)
val ExpressiveShape = RoundedCornerShape(28.dp)

/**
 * Forma "blob" orgánica (aprox. del border-radius asimétrico CSS del mockup:
 * 46% 54% 58% 42% / 48% 44% 56% 52%). `RoundedCornerShape` de Compose no soporta
 * radios elípticos independientes por eje como CSS (cada esquina un único radio,
 * no H/V separados) — esta es la aproximación más cercana sin escribir un Shape a
 * medida con Path/bezier, que da el mismo efecto "no es un círculo perfecto" que
 * pide el mockup para iconos y el botón de ajustes.
 */
fun blobShape(): Shape = RoundedCornerShape(
    topStartPercent = 46, topEndPercent = 54, bottomEndPercent = 58, bottomStartPercent = 42
)

@Composable
fun ExpressiveSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ExpressiveShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
        content = content
    )
}

@Composable
fun ExpressiveButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingIcon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp).fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        enabled = enabled
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
        if (trailingIcon != null) {
            Spacer(Modifier.width(8.dp))
            Icon(trailingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * Aviso informativo con el color terciario (primer uso real de ese rol en la app,
 * fuera de la paleta base de Tanda 0) — mismo patrón que el ".tip" del mockup.
 */
@Composable
fun TipBanner(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Filled.Info, contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

// =============================================================================
// Tanda 2 — Identidad visual por proveedor
// =============================================================================

/**
 * Glyph fallback: círculo con `brandColor` y la inicial del proveedor en blanco.
 * Se usa cuando no hay drawable oficial disponible o cuando se quiere la variante
 * neutra (nunca es el logo real, solo la identidad de color).
 */
@Composable
fun ProviderGlyph(
    provider: AiProvider,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(provider.brandColor)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = provider.initial,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            // Tamaño de fuente proporcional al tamaño del círculo: 45% del diámetro
            // funciona bien para iniciales de 1–2 caracteres.
            fontSize = (size.value * 0.45f).sp
        )
    }
}

/**
 * Logo del proveedor: intenta cargar el drawable oficial (o placeholder de la
 * Tanda 2). Al estar el drawable garantizado presente desde el ZIP (`res/drawable/
 * ic_provider_*.xml`), `painterResource` es seguro sin try/catch. Encima del disco
 * pintado por el drawable se superpone la inicial del proveedor — así el aspecto es
 * "logo simple pero reconocible" incluso antes de sustituir por SVG oficial.
 *
 * Cuando se sustituye el drawable por el logo oficial (que ya incluye la marca
 * completa), basta con eliminar el overlay pasando `showInitial = false` desde el
 * call site, o rediseñar este composable para omitirlo automáticamente.
 */
@Composable
fun ProviderLogo(
    provider: AiProvider,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    showInitial: Boolean = true
) {
    // `LocalContext.current.resources` no es necesario si usamos painterResource
    // directamente con el ID por nombre resuelto de forma segura.
    val ctx = LocalContext.current
    val resId = remember(provider) {
        ctx.resources.getIdentifier(provider.drawableName, "drawable", ctx.packageName)
    }
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (resId != 0) {
            Icon(
                painter = painterResource(id = resId),
                contentDescription = provider.displayName,
                tint = Color.Unspecified,          // respeta los colores del vector
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Fallback total: si por lo que sea el drawable no existe, glyph puro.
            ProviderGlyph(provider = provider, size = size)
        }
        if (showInitial) {
            Text(
                text = provider.initial,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.4f).sp
            )
        }
    }
}

/**
 * Chip de motor IA: filter chip Expressive con logo del proveedor, nombre, badge EU
 * y estado "activo" (destacado cuando ese motor está procesando en tiempo real).
 *
 * Estados visuales:
 *  - `active=true`: borde con `primary` grueso + `primaryContainer` de fondo, tenga o
 *    no el usuario seleccionado el chip (indica trabajo en curso, no elección).
 *  - `selected=true`: color `secondaryContainer` de fondo (rol de "elección del
 *    usuario", distinto del rol de marca `primary`).
 *  - Base: contorno neutro.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngineChip(
    provider: AiProvider,
    selected: Boolean,
    active: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Halo pulsante alrededor del logo cuando el motor está trabajando: da la
    // señal viva sin animar el chip entero (que se sentiría inestable).
    val infinite = rememberInfiniteTransition(label = "engineChipPulse")
    val pulseScale by infinite.animateFloat(
        initialValue = if (active) 1f else 1f,
        targetValue = if (active) 1.12f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "engineChipPulseScale"
    )

    FilterChip(
        modifier = modifier,
        selected = selected,
        onClick = onClick,
        leadingIcon = {
            Box(contentAlignment = Alignment.Center) {
                if (active) {
                    // Halo animado detrás del logo
                    Box(
                        Modifier
                            .size(24.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    )
                }
                ProviderLogo(provider = provider, size = 20.dp)
            }
        },
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(provider.displayName)
                if (provider.eu) {
                    Spacer(Modifier.width(4.dp))
                    Text("🇪🇺", fontSize = 12.sp)
                }
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = if (active) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surface,
            selectedContainerColor = if (active) MaterialTheme.colorScheme.primaryContainer
                                     else MaterialTheme.colorScheme.secondaryContainer,
            labelColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                         else MaterialTheme.colorScheme.onSurface,
            selectedLabelColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                                 else MaterialTheme.colorScheme.onSecondaryContainer
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = if (active) MaterialTheme.colorScheme.primary
                                  else MaterialTheme.colorScheme.secondary,
            borderWidth = 1.dp,
            selectedBorderWidth = if (active) 2.dp else 1.5.dp
        )
    )
}

/**
 * Indicador de progreso multi-motor: sustituye al `busyMsg` genérico
 * ("Analizando con IA…") durante la extracción. Muestra qué motor trabaja ahora,
 * qué motores ya terminaron y cuáles siguen en cola, con el logo real y estado.
 *
 * Diseño:
 *  - Cabecera: logo grande del motor activo + "Analizando con **NombreMotor**…"
 *    (o `busyMsg` si aún no arrancó ninguno concreto, ej. "Preparando documentos…")
 *  - `LoadingIndicator` Expressive debajo (forma animada, no círculo)
 *  - Fila de puntos (uno por motor habilitado): logo pequeño de cada uno con
 *    opacidad según estado (pendiente 30% / actual 100% + halo / hecho 60% + tick)
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MotorLoadingIndicator(
    busyMsg: String,
    activeProvider: AiProvider?,
    finishedProviders: Set<AiProvider>,
    enabledProviders: List<AiProvider>,
    modifier: Modifier = Modifier,
    // Mezcla 2+3 — documento real en curso ("zeb1.pdf (pág. 2/4)") y progreso agregado
    // documento × motor. progressTotal=0 oculta la barra (compatibilidad con llamadas
    // que no pasen estos parámetros).
    activeDocLabel: String? = null,
    progressCurrent: Int = 0,
    progressTotal: Int = 0
) {
    ExpressiveSurface(modifier = modifier) {
        Column(
            Modifier.padding(24.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Cabecera: SIEMPRE el LoadingIndicator squiggly de M3 Expressive. El motor
            // activo ya se ve destacado con halo en la fila de motores de abajo, no
            // repetirlo aquí arriba — antes el logo grande + logo pequeño abajo daba
            // sensación redundante de "dos avatares del mismo".
            LoadingIndicator(
                modifier = Modifier.size(56.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = when {
                    activeProvider != null -> "Analizando con ${activeProvider.displayName}"
                    else -> busyMsg.ifBlank { "Procesando…" }
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Documento real en curso — solo si viene informado.
            if (activeDocLabel != null) {
                Text(
                    text = activeDocLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Barra de progreso agregada documento × motor con porcentaje al lado
            // (más compacto y legible que apilado). Solo si el llamador pasa total > 0.
            if (progressTotal > 0) {
                val fraction = (progressCurrent.toFloat() / progressTotal).coerceIn(0f, 1f)
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.weight(1f).height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    Text(
                        text = "${(fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Fila de estado por motor: pendiente / actual / hecho. Ya no hay
            // LoadingIndicator suelto encima — la actividad la comunica el halo
            // alrededor del motor activo y la propia barra de progreso.
            if (enabledProviders.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    enabledProviders.forEach { p ->
                        val isDone = p in finishedProviders
                        val isActive = p == activeProvider
                        Box(contentAlignment = Alignment.BottomEnd) {
                            ProviderLogo(
                                provider = p,
                                size = 24.dp,
                                modifier = Modifier.alpha(
                                    when {
                                        isActive -> 1f
                                        isDone -> 0.75f
                                        else -> 0.35f
                                    }
                                )
                            )
                            if (isDone) {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(7.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
