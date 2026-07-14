package com.mejoresiagratis.rellenador.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Sección plegable Expressive reutilizable — cabecera con icono, título, contador
 * opcional animado, y chevron con motion physics; cuerpo con expand/shrink vertical.
 *
 * Ya se usaba en DocumentsStep para "Documentos" y "Motores IA" (con count numérico).
 * Se extrae aquí y se generaliza con `count: Int? = null` para poder usarla también
 * en SignatureStep (secciones "Ajustes de firma" y "Huecos de firma") sin obligar a
 * un contador en la cabecera.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveAccordion(
    title: String,
    icon: ImageVector,
    shape: Shape,
    containerColor: Color,
    onContainerColor: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    countSuffix: String = "",
    content: @Composable ColumnScope.() -> Unit
) {
    // Motion physics real (M3 Expressive): el muelle del MotionScheme del tema.
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "chevron"
    )
    Surface(
        shape = shape,
        color = containerColor,
        modifier = modifier.fillMaxWidth().animateContentSize()
    ) {
        Column(Modifier.padding(4.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = onContainerColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    if (count != null) {
                        Text("$title · ", style = MaterialTheme.typography.titleSmall, color = onContainerColor)
                        AnimatedContent(
                            targetState = count,
                            transitionSpec = {
                                (slideInVertically { h -> h } + fadeIn())
                                    .togetherWith(slideOutVertically { h -> -h } + fadeOut())
                            },
                            label = "sectionCount"
                        ) { c ->
                            Text("$c$countSuffix", style = MaterialTheme.typography.titleSmall, color = onContainerColor)
                        }
                    } else {
                        Text(title, style = MaterialTheme.typography.titleSmall, color = onContainerColor)
                    }
                }
                Icon(
                    Icons.Filled.KeyboardArrowDown, contentDescription = if (expanded) "Contraer" else "Expandir",
                    tint = onContainerColor,
                    modifier = Modifier.rotate(chevronRotation)
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit = shrinkVertically(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec())
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).padding(bottom = 12.dp)) {
                    content()
                }
            }
        }
    }
}
