package com.zorix.chess.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import org.jetbrains.compose.resources.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp
import com.zorix.chess.resources.*

/** Brand palette taken from the Zorix logo: glossy red "Z", graphite/silver "C". */
object ZorixColors {
    val Background = Color(0xFF0E0E11)
    val Surface = Color(0xFF16161B)
    val SurfaceHigh = Color(0xFF1E1E25)
    val SurfaceHighest = Color(0xFF282831)
    val Outline = Color(0xFF3A3A45)
    val Red = Color(0xFFE3202B)
    val RedBright = Color(0xFFFF4B55)
    val RedDeep = Color(0xFF8A0610)
    val Silver = Color(0xFFD7D8DE)
    val SilverDim = Color(0xFF9C9DA8)
    val Text = Color(0xFFF1F1F4)
    val TextDim = Color(0xFFA7A8B3)
    val Best = Color(0xFF2FD27C)
    val Line2 = Color(0xFF4F9CFF)
    val Line3 = Color(0xFFFFB02E)
    val EvalWhite = Color(0xFFF4F4F6)
    val EvalBlack = Color(0xFF2A2A31)

    val RedGradient = Brush.verticalGradient(listOf(Color(0xFFFF5A5A), Color(0xFFE20C18), Color(0xFF7A000A)))
    val SilverGradient = Brush.verticalGradient(listOf(Color(0xFFFAFAFC), Color(0xFFBEBEC6), Color(0xFF7C7C86)))
}

private val scheme = darkColorScheme(
    primary = ZorixColors.Red,
    onPrimary = Color.White,
    primaryContainer = ZorixColors.RedDeep,
    onPrimaryContainer = Color.White,
    secondary = ZorixColors.Silver,
    onSecondary = Color(0xFF15151A),
    secondaryContainer = ZorixColors.SurfaceHighest,
    onSecondaryContainer = ZorixColors.Text,
    tertiary = ZorixColors.Best,
    background = ZorixColors.Background,
    onBackground = ZorixColors.Text,
    surface = ZorixColors.Surface,
    onSurface = ZorixColors.Text,
    surfaceVariant = ZorixColors.SurfaceHigh,
    onSurfaceVariant = ZorixColors.TextDim,
    surfaceContainerLowest = ZorixColors.Background,
    surfaceContainerLow = ZorixColors.Surface,
    surfaceContainer = ZorixColors.Surface,
    surfaceContainerHigh = ZorixColors.SurfaceHigh,
    surfaceContainerHighest = ZorixColors.SurfaceHighest,
    outline = ZorixColors.Outline,
    outlineVariant = Color(0xFF2A2A33),
    error = Color(0xFFFF6B6B),
)

/** Orbitron (SIL OFL), the extended geometric face used for the ZORIX CHESS wordmark. */
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

@Composable
fun ZorixTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
