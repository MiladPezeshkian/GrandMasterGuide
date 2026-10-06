package com.zorix.chess.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import org.jetbrains.compose.resources.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp
import com.zorix.chess.controller.AppThemeId
import com.zorix.chess.resources.*

/** One complete set of app colours. [accent] is the brand colour (Zorix red, or sky blue in the light style). */
data class Palette(
    val dark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val surfaceHighest: Color,
    val outline: Color,
    val accent: Color,
    val accentBright: Color,
    val accentDeep: Color,
    val silver: Color,
    val silverDim: Color,
    val text: Color,
    val textDim: Color,
    val best: Color,
    val line2: Color,
    val line3: Color,
    val accentGradient: List<Color>,
    val secondWordGradient: List<Color>,
    val buttonGradient: List<Color>,
    val heroGradient: List<Color>,
)

/** The original style taken from the Zorix logo: black, glossy red "Z", graphite/silver "C". */
val ZorixPalette = Palette(
    dark = true,
    background = Color(0xFF0E0E11),
    surface = Color(0xFF16161B),
    surfaceHigh = Color(0xFF1E1E25),
    surfaceHighest = Color(0xFF282831),
    outline = Color(0xFF3A3A45),
    accent = Color(0xFFE3202B),
    accentBright = Color(0xFFFF4B55),
    accentDeep = Color(0xFF8A0610),
    silver = Color(0xFFD7D8DE),
    silverDim = Color(0xFF9C9DA8),
    text = Color(0xFFF1F1F4),
    textDim = Color(0xFFA7A8B3),
    best = Color(0xFF2FD27C),
    line2 = Color(0xFF4F9CFF),
    line3 = Color(0xFFFFB02E),
    accentGradient = listOf(Color(0xFFFF5A5A), Color(0xFFE20C18), Color(0xFF7A000A)),
    secondWordGradient = listOf(Color(0xFFFAFAFC), Color(0xFFBEBEC6), Color(0xFF7C7C86)),
    buttonGradient = listOf(Color(0xFFF23A44), Color(0xFFC3121C)),
    heroGradient = listOf(Color(0xFFB0141E), Color(0xFF5A0610), Color(0xFF22080B)),
)

/** Light style: white and sky blue. */
val SkyPalette = Palette(
    dark = false,
    background = Color(0xFFF2F8FD),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFE7F2FB),
    surfaceHighest = Color(0xFFD6E9F8),
    outline = Color(0xFFB3CDE3),
    accent = Color(0xFF0B8BD9),
    accentBright = Color(0xFF0277BD),
    accentDeep = Color(0xFFCDE8FA),
    silver = Color(0xFF3B4D61),
    silverDim = Color(0xFF6C7F94),
    text = Color(0xFF0F1E2D),
    textDim = Color(0xFF55687C),
    best = Color(0xFF138A4B),
    line2 = Color(0xFF2563EB),
    line3 = Color(0xFFD97706),
    accentGradient = listOf(Color(0xFF4FC3F7), Color(0xFF0B8BD9), Color(0xFF075E99)),
    secondWordGradient = listOf(Color(0xFF51647A), Color(0xFF2C3D50), Color(0xFF16222F)),
    buttonGradient = listOf(Color(0xFF29A8F0), Color(0xFF0A78C2)),
    heroGradient = listOf(Color(0xFF1B9BE6), Color(0xFF0A6FB3), Color(0xFF064A7A)),
)

/**
 * Brand colours used across the UI. They follow the selected app style: reading them inside a
 * composable recomposes it when the style changes.
 */
object ZorixColors {
    var palette: Palette by mutableStateOf(ZorixPalette)

    val Background get() = palette.background
    val Surface get() = palette.surface
    val SurfaceHigh get() = palette.surfaceHigh
    val SurfaceHighest get() = palette.surfaceHighest
    val Outline get() = palette.outline
    val Red get() = palette.accent
    val RedBright get() = palette.accentBright
    val RedDeep get() = palette.accentDeep
    val Silver get() = palette.silver
    val SilverDim get() = palette.silverDim
    val Text get() = palette.text
    val TextDim get() = palette.textDim
    val Best get() = palette.best
    val Line2 get() = palette.line2
    val Line3 get() = palette.line3
    val EvalWhite = Color(0xFFF4F4F6)
    val EvalBlack = Color(0xFF2A2A31)

    val RedGradient get() = Brush.verticalGradient(palette.accentGradient)
    val SilverGradient get() = Brush.verticalGradient(palette.secondWordGradient)
    val ButtonGradient get() = Brush.verticalGradient(palette.buttonGradient)
}

private fun scheme(p: Palette): ColorScheme = if (p.dark) {
    darkColorScheme(
        primary = p.accent,
        onPrimary = Color.White,
        primaryContainer = p.accentDeep,
        onPrimaryContainer = Color.White,
        secondary = p.silver,
        onSecondary = Color(0xFF15151A),
        secondaryContainer = p.surfaceHighest,
        onSecondaryContainer = p.text,
        tertiary = p.best,
        background = p.background,
        onBackground = p.text,
        surface = p.surface,
        onSurface = p.text,
        surfaceVariant = p.surfaceHigh,
        onSurfaceVariant = p.textDim,
        surfaceContainerLowest = p.background,
        surfaceContainerLow = p.surface,
        surfaceContainer = p.surface,
        surfaceContainerHigh = p.surfaceHigh,
        surfaceContainerHighest = p.surfaceHighest,
        outline = p.outline,
        outlineVariant = Color(0xFF2A2A33),
        error = Color(0xFFFF6B6B),
    )
} else {
    lightColorScheme(
        primary = p.accent,
        onPrimary = Color.White,
        primaryContainer = p.accentDeep,
        onPrimaryContainer = Color(0xFF00344F),
        secondary = p.silver,
        onSecondary = Color.White,
        secondaryContainer = p.surfaceHighest,
        onSecondaryContainer = p.text,
        tertiary = p.best,
        background = p.background,
        onBackground = p.text,
        surface = p.surface,
        onSurface = p.text,
        surfaceVariant = p.surfaceHigh,
        onSurfaceVariant = p.textDim,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = p.surface,
        surfaceContainer = p.surface,
        surfaceContainerHigh = p.surfaceHigh,
        surfaceContainerHighest = p.surfaceHighest,
        outline = p.outline,
        outlineVariant = Color(0xFFD3E2EF),
        error = Color(0xFFC62828),
    )
}

/** Orbitron (SIL OFL), the extended geometric face used for the wordmark. */
@Composable
fun brandFont(): FontFamily = FontFamily(
    Font(Res.font.orbitron_bold, FontWeight.Bold),
    Font(Res.font.orbitron_black, FontWeight.Black),
)

private val base = Typography()

private val typography = Typography(
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp),
)

/** Tabular figures for engine numbers so they do not jitter while updating. */
val NumberStyle = TextStyle(fontFeatureSettings = "tnum", fontWeight = FontWeight.SemiBold)

/** Chess notation (SAN lines, scores) must read left-to-right even in Persian. */
fun TextStyle.notation(): TextStyle = copy(textDirection = TextDirection.Ltr)

fun paletteOf(theme: AppThemeId): Palette = when (theme) {
    AppThemeId.ZORIX -> ZorixPalette
    AppThemeId.SKY -> SkyPalette
}

@Composable
fun ZorixTheme(theme: AppThemeId = AppThemeId.ZORIX, content: @Composable () -> Unit) {
    val palette = paletteOf(theme)
    // Switched before the content is composed, so every screen draws with the new colours.
    if (ZorixColors.palette != palette) ZorixColors.palette = palette
    MaterialTheme(colorScheme = scheme(palette), typography = typography, content = content)
}
