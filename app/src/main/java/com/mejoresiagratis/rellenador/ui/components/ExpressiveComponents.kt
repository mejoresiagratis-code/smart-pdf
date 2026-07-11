package com.mejoresiagratis.rellenador.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

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
    content: @Composable () -> Unit // <-- LA CORRECCIÓN ESTÁ AQUÍ (se eliminó ColumnScope.)
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
