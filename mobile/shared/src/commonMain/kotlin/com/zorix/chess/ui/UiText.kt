package com.zorix.chess.ui

import androidx.compose.runtime.Composable
import com.zorix.chess.engine.uci.formatDecimal
import com.zorix.chess.learn.Text
import com.zorix.chess.resources.*
import org.jetbrains.compose.resources.stringResource

/** Lesson text in the app language. */
fun Text.localized(lang: String): String = this[lang]

/** "87.5%" */
fun formatPercent(value: Double): String = formatDecimal(value, 1) + "%"

/** Localised name of a puzzle theme tag. */
@Composable
fun themeLabel(theme: String): String = when (theme) {
    "mate" -> stringResource(Res.string.theme_mate)
    "mateIn1" -> stringResource(Res.string.puzzle_mate1)
    "mateIn2" -> stringResource(Res.string.puzzle_mate2)
    "fork" -> stringResource(Res.string.theme_fork)
    "pin" -> stringResource(Res.string.theme_pin)
    "skewer" -> stringResource(Res.string.theme_skewer)
    "discoveredAttack" -> stringResource(Res.string.theme_discovered)
    "doubleCheck" -> stringResource(Res.string.theme_double_check)
    "hangingPiece" -> stringResource(Res.string.theme_hanging)
    "capturingDefender" -> stringResource(Res.string.theme_defender)
    "deflection" -> stringResource(Res.string.theme_deflection)
    "attraction" -> stringResource(Res.string.theme_attraction)
    "sacrifice" -> stringResource(Res.string.theme_sacrifice)
    "promotion" -> stringResource(Res.string.theme_promotion)
    "quietMove" -> stringResource(Res.string.theme_quiet)
    "endgame" -> stringResource(Res.string.theme_endgame)
    "opening" -> stringResource(Res.string.theme_opening)
    else -> theme
}
