package com.mejoresiagratis.rellenador.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tanda 0 — fundación M3 Expressive.
 * v0.9.5 — repaletado de marca: Orange/MASORANGE ya no es el operador con el que se
 * trabaja; la paleta pasa a la identidad de **Aire** (airetech.es). Los tonos no son
 * inventados: están MUESTREADOS por píxel del propio `Contrato_empresas.pdf` de Aire
 * (cabecera y acento #9F0BFF, banda de marca #00095A, fondo de tabla #ECD0FF), igual
 * que antes el naranja partía del `#FF7900` de Orange. Estructura sin cambios: todos
 * los roles derivados a mano, terciario como contrapunto de color (principio
 * Expressive de "paleta más rica"), sin sumar material-color-utilities como dependencia.
 */

// ---- Violeta de marca Aire (acento de logo/cabeceras de tabla del PDF real) ----
private val BrandVioleta = Color(0xFF9F0BFF)
private val OnBrandVioleta = Color(0xFFFFFFFF)
private val VioletaContainerLight = Color(0xFFECD0FF)
private val OnVioletaContainerLight = Color(0xFF350063)
private val VioletaContainerDark = Color(0xFF5A0099)
private val OnVioletaContainerDark = Color(0xFFECD0FF)

// ---- Secundario: azul marino Aire (banda de cabecera del PDF, #00095A) ----
private val SecondaryLight = Color(0xFF00095A)
private val OnSecondaryLight = Color(0xFFFFFFFF)
private val SecondaryContainerLight = Color(0xFFDCDCFF)
private val OnSecondaryContainerLight = Color(0xFF00095A)
private val SecondaryDark = Color(0xFFC2C4FF)
private val OnSecondaryDark = Color(0xFF1A1F6B)
private val SecondaryContainerDark = Color(0xFF23285C)
private val OnSecondaryContainerDark = Color(0xFFDCDCFF)

// ---- Terciario: índigo intermedio — contrapunto de color, variedad Expressive ----
private val TertiaryLight = Color(0xFF5B4FE0)
private val OnTertiaryLight = Color(0xFFFFFFFF)
private val TertiaryContainerLight = Color(0xFFE3DFFF)
private val OnTertiaryContainerLight = Color(0xFF1A0F66)
private val TertiaryDark = Color(0xFFC9C1FF)
private val OnTertiaryDark = Color(0xFF2E2270)
private val TertiaryContainerDark = Color(0xFF433794)
private val OnTertiaryContainerDark = Color(0xFFE3DFFF)

// ---- Error: valores estándar M3 (no reinventar el color de accesibilidad crítica) ----
private val ErrorLight = Color(0xFFBA1A1A)
private val OnErrorLight = Color(0xFFFFFFFF)
private val ErrorContainerLight = Color(0xFFFFDAD6)
private val OnErrorContainerLight = Color(0xFF410002)
private val ErrorDark = Color(0xFFFFB4AB)
private val OnErrorDark = Color(0xFF690005)
private val ErrorContainerDark = Color(0xFF93000A)
private val OnErrorContainerDark = Color(0xFFFFDAD6)

// ---- Superficies: tinte frío neutro (antes cálido, a tono con el naranja) ----
private val SurfaceLight = Color(0xFFFAF8FF)
private val OnSurfaceLight = Color(0xFF1B1B23)
private val SurfaceVariantLight = Color(0xFFE3E0EC)
private val OnSurfaceVariantLight = Color(0xFF46464F)
private val OutlineLight = Color(0xFF767680)
private val OutlineVariantLight = Color(0xFFC7C5D0)
private val SurfaceDark = Color(0xFF131318)
private val OnSurfaceDark = Color(0xFFE5E1EA)
private val SurfaceVariantDark = Color(0xFF46464F)
private val OnSurfaceVariantDark = Color(0xFFC7C5D0)
private val OutlineDark = Color(0xFF908F9A)
private val OutlineVariantDark = Color(0xFF46464F)

// ---- Roles surfaceContainer* (v0.8.2) ----
// Sin declararlos, Material 3 los deriva en GRIS NEUTRO y rompen la coherencia del
// resto del tema. Estos valores continúan la rampa tonal fría, la que ahora usa
// `surface` con la marca Aire.
private val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
private val SurfaceContainerLowLight = Color(0xFFF4F2FA)
private val SurfaceContainerLight = Color(0xFFEEEBF5)
private val SurfaceContainerHighLight = Color(0xFFE8E5F0)
private val SurfaceContainerHighestLight = Color(0xFFE2DFEB)

private val SurfaceContainerLowestDark = Color(0xFF0D0D11)
private val SurfaceContainerLowDark = Color(0xFF1B1B21)
private val SurfaceContainerDark = Color(0xFF1F1F26)
private val SurfaceContainerHighDark = Color(0xFF2A2A32)
private val SurfaceContainerHighestDark = Color(0xFF35343D)

private val LightColors: ColorScheme = lightColorScheme(
    primary = BrandVioleta, onPrimary = OnBrandVioleta,
    primaryContainer = VioletaContainerLight, onPrimaryContainer = OnVioletaContainerLight,
    secondary = SecondaryLight, onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight, onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight, onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight, onTertiaryContainer = OnTertiaryContainerLight,
    error = ErrorLight, onError = OnErrorLight,
    errorContainer = ErrorContainerLight, onErrorContainer = OnErrorContainerLight,
    surface = SurfaceLight, onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight, onSurfaceVariant = OnSurfaceVariantLight,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
    outline = OutlineLight, outlineVariant = OutlineVariantLight,
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFD8AFFF), onPrimary = Color(0xFF3A0069),
    primaryContainer = VioletaContainerDark, onPrimaryContainer = OnVioletaContainerDark,
    secondary = SecondaryDark, onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark, onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark, onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark, onTertiaryContainer = OnTertiaryContainerDark,
    error = ErrorDark, onError = OnErrorDark,
    errorContainer = ErrorContainerDark, onErrorContainer = OnErrorContainerDark,
    surface = SurfaceDark, onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark, onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
    outline = OutlineDark, outlineVariant = OutlineVariantDark,
)

/**
 * Escala de formas Expressive: radios más generosos que el M3 base — las formas
 * "dirigen la atención y comunican marca" (principio Expressive), no solo decoran.
 * Componentes concretos (firma, chips de motor, tarjetas de bloque) podrán pedir
 * formas más singulares de MaterialShapes en las tandas de rediseño por pantalla.
 */
val RellenadorShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * Tipografía: se parte de la escala M3 por defecto (Roboto) pero con más peso en los
 * títulos, dando la jerarquía "letras con personalidad" que pide Expressive sin
 * necesitar tipografía de marca propia todavía. Las tandas de rediseño por pantalla
 * podrán usar `titleLarge`/`headlineSmall` como estilo "emphasized" puntual.
 */
val RellenadorTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RellenadorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialExpressiveTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = RellenadorShapes,
        typography = RellenadorTypography,
        // Física de resortes con rebote — el esquema recomendado por defecto en
        // Expressive para que las interacciones se sientan vivas, no solo correctas.
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}
