package com.mejoresiagratis.rellenador.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Radio de borde unificado (estilo M3 Expressive 2026)
val ExpressiveShape = RoundedCornerShape(28.dp)

@Composable
fun ExpressiveSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
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
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp).fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        enabled = enabled
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}
