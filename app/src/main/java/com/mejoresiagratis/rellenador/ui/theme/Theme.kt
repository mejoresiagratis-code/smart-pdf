package com.mejoresiagratis.rellenador.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OrangeAccent = Color(0xFFFF7900)

private val LightColors = lightColorScheme(primary = OrangeAccent)
private val DarkColors = darkColorScheme(primary = OrangeAccent)

@Composable
fun RellenadorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
