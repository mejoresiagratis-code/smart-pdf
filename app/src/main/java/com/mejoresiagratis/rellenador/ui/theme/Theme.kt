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
 *
 * Antes, el tema solo sobreescribía `primary`; el resto de roles (containers,
 * superficies, secundario/terciario…) quedaban en el morado por defecto de M3, de ahí
 * la sensación de "app sin terminar". Esta paleta deriva TODOS los roles a mano a
 * partir del naranja de marca (0xFFFF7900), con un terciario azul-verdoso frío que da
 * la variedad de color que pide el principio Expressive de "paleta más rica para
 * marcar jerarquía visual" — sin añadir ninguna librería de generación de esquemas
 * (material-color-utilities), para no sumar otra dependencia alpha además de material3.
 */

// ---- Naranja de marca (idéntico al de antes, ahora con toda su familia tonal) ----
private val BrandOrange = Color(0xFFFF7900)
private val OnBrandOrange = Color(0xFFFFFFFF)
private val OrangeContainerLight = Color(0xFFFFDBC2)
private val OnOrangeContainerLight = Color(0xFF2E1500)
private val OrangeContainerDark = Color(0xFF5C3600)
private val OnOrangeContainerDark = Color(0xFFFFDBC2)

// ---- Secundario: marrón cálido neutro (chips, acciones menos prioritarias) ----
private val SecondaryLight = Color(0xFF7C5635)
private val OnSecondaryLight = Color(0xFFFFFFFF)
private val SecondaryContainerLight = Color(0xFFFFDCC0)
private val OnSecondaryContainerLight = Color(0xFF2E1600)
private val SecondaryDark = Color(0xFFEDBF95)
private val OnSecondaryDark = Color(0xFF452B0D)
private val SecondaryContainerDark = Color(0xFF5F4021)
private val OnSecondaryContainerDark = Color(0xFFFFDCC0)

// ---- Terciario: azul-verdoso frío — contrapunto de color, variedad Expressive ----
private val TertiaryLight = Color(0xFF3D6472)
private val OnTertiaryLight = Color(0xFFFFFFFF)
private val TertiaryContainerLight = Color(0xFFC0E9F9)
private val OnTertiaryContainerLight = Color(0xFF001F27)
private val TertiaryDark = Color(0xFFA4CDDD)
private val OnTertiaryDark = Color(0xFF063542)
private val TertiaryContainerDark = Color(0xFF244C59)
private val OnTertiaryContainerDark = Color(0xFFC0E9F9)

// ---- Error: valores estándar M3 (no reinventar el color de accesibilidad crítica) ----
private val ErrorLight = Color(0xFFBA1A1A)
private val OnErrorLight = Color(0xFFFFFFFF)
private val ErrorContainerLight = Color(0xFFFFDAD6)
private val OnErrorContainerLight = Color(0xFF410002)
private val ErrorDark = Color(0xFFFFB4AB)
private val OnErrorDark = Color(0xFF690005)
private val ErrorContainerDark = Color(0xFF93000A)
private val OnErrorContainerDark = Color(0xFFFFDAD6)

// ---- Superficies: tinte cálido sutil (no gris puro), coherente con el naranja ----
private val SurfaceLight = Color(0xFFFFF8F5)
private val OnSurfaceLight = Color(0xFF221A14)
private val SurfaceVariantLight = Color(0xFFF3DFD3)
private val OnSurfaceVariantLight = Color(0xFF51443A)
private val OutlineLight = Color(0xFF837469)
private val OutlineVariantLight = Color(0xFFD6C3B6)
private val SurfaceDark = Color(0xFF19120D)
private val OnSurfaceDark = Color(0xFFEDE0D9)
private val SurfaceVariantDark = Color(0xFF51443A)
private val OnSurfaceVariantDark = Color(0xFFD6C3B6)
private val OutlineDark = Color(0xFF9E8D80)
private val OutlineVariantDark = Color(0xFF51443A)

private val LightColors: ColorScheme = lightColorScheme(
    primary = BrandOrange, onPrimary = OnBrandOrange,
    primaryContainer = OrangeContainerLight, onPrimaryContainer = OnOrangeContainerLight,
    secondary = SecondaryLight, onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight, onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight, onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight, onTertiaryContainer = OnTertiaryContainerLight,
    error = ErrorLight, onError = OnErrorLight,
    errorContainer = ErrorContainerLight, onErrorContainer = OnErrorContainerLight,
    surface = SurfaceLight, onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight, onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight, outlineVariant = OutlineVariantLight,
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = BrandOrange, onPrimary = Color(0xFF4A2800),
    primaryContainer = OrangeContainerDark, onPrimaryContainer = OnOrangeContainerDark,
    secondary = SecondaryDark, onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark, onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark, onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark, onTertiaryContainer = OnTertiaryContainerDark,
    error = ErrorDark, onError = OnErrorDark,
    errorContainer = ErrorContainerDark, onErrorContainer = OnErrorContainerDark,
    surface = SurfaceDark, onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark, onSurfaceVariant = OnSurfaceVariantDark,
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
