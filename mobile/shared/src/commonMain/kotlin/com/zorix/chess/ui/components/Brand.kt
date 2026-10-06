package com.zorix.chess.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.zorix.chess.ui.theme.brandFont
import com.zorix.chess.ui.theme.ZorixColors

/**
 * The "ZORIX CHESS" wordmark (replaces "ZORIXCODE" from the original logo):
 * glossy red ZORIX, metallic CHESS, wide extended letters.
 */
@Composable
fun ZorixWordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 20.sp,
    letterSpacing: TextUnit = 0.28.em,
    alpha: Float = 1f,
    secondWord: Brush = ZorixColors.SilverGradient,
) {
    val style = TextStyle(
        fontFamily = brandFont(),
        fontWeight = FontWeight.Black,
        fontSize = fontSize,
        letterSpacing = letterSpacing,
        textGeometricTransform = TextGeometricTransform(scaleX = 1.1f),
    )
    // Brand text never mirrors in right-to-left languages.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Text("ZORIX", style = style.copy(brush = ZorixColors.RedGradient, alpha = alpha))
            Spacer(Modifier.width(with(LocalDensity.current) { (fontSize * 0.35f).toDp() }))
            Text("CHESS", style = style.copy(brush = secondWord, alpha = alpha))
        }
    }
}

/**
 * The "GRANDMASTER GUIDE" wordmark: GRANDMASTER in the accent gradient, GUIDE in silver (or slate
 * in the light style), with an optional small "BY ZORIX" line under it.
 */
@Composable
fun AppWordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 20.sp,
    letterSpacing: TextUnit = 0.14.em,
    alpha: Float = 1f,
    showMaker: Boolean = false,
) {
    val style = TextStyle(
        fontFamily = brandFont(),
        fontWeight = FontWeight.Black,
        fontSize = fontSize,
        letterSpacing = letterSpacing,
    )
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("GRANDMASTER", style = style.copy(brush = ZorixColors.RedGradient, alpha = alpha), maxLines = 1)
                Spacer(Modifier.width(with(LocalDensity.current) { (fontSize * 0.4f).toDp() }))
                Text("GUIDE", style = style.copy(brush = ZorixColors.SilverGradient, alpha = alpha), maxLines = 1)
            }
            if (showMaker) {
                Text(
                    "BY ZORIX",
                    style = style.copy(fontSize = fontSize * 0.45f, letterSpacing = 0.5.em, fontWeight = FontWeight.Bold, color = ZorixColors.SilverDim.copy(alpha = alpha)),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Thin red line with faded ends, as under the original logo. [fraction] animates its width. */
@Composable
fun BrandUnderline(modifier: Modifier = Modifier, fraction: Float = 1f) {
    Canvas(modifier) {
        val w = size.width * fraction.coerceIn(0f, 1f)
        if (w <= 0f) return@Canvas
        val start = (size.width - w) / 2
        val y = size.height / 2
        drawLine(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                0.2f to ZorixColors.Red,
                0.8f to ZorixColors.Red,
                1f to Color.Transparent,
                startX = start,
                endX = start + w,
            ),
            start = Offset(start, y),
            end = Offset(start + w, y),
            strokeWidth = size.height,
        )
    }
}
