package com.zorix.chess.ui

import com.zorix.chess.resources.*
import org.jetbrains.compose.resources.StringResource
import androidx.compose.ui.graphics.Color
import com.zorix.chess.controller.BoardThemeId
import com.zorix.chess.controller.MoveQuality
import com.zorix.chess.controller.UiMessage
import com.zorix.chess.core.EndReason
import com.zorix.chess.core.GameResult
import com.zorix.chess.core.GameStatus
import com.zorix.chess.core.PositionProblem
import com.zorix.chess.core.Side

fun UiMessage.label(): StringResource = when (this) {
    UiMessage.ILLEGAL_MOVE -> Res.string.msg_illegal_move
    UiMessage.GAME_IS_OVER -> Res.string.msg_game_over
    UiMessage.ENGINE_NOT_READY -> Res.string.msg_engine_not_ready
    UiMessage.NO_LEGAL_MOVES -> Res.string.msg_no_legal_moves
    UiMessage.SUGGESTION_OUTDATED -> Res.string.msg_suggestion_outdated
    UiMessage.FEN_INVALID -> Res.string.msg_fen_invalid
    UiMessage.FEN_COPIED -> Res.string.msg_fen_copied
    UiMessage.PGN_COPIED -> Res.string.msg_pgn_copied
    UiMessage.POSITION_LOADED -> Res.string.msg_position_loaded
    UiMessage.NOTHING_TO_UNDO -> Res.string.msg_nothing_to_undo
    UiMessage.NOTHING_TO_REDO -> Res.string.msg_nothing_to_redo
}

fun GameStatus.label(): StringResource? = when (reason) {
    EndReason.CHECKMATE ->
        if (result == GameResult.WHITE_WINS) Res.string.game_over_checkmate_white else Res.string.game_over_checkmate_black
    EndReason.STALEMATE -> Res.string.game_over_stalemate
    EndReason.INSUFFICIENT_MATERIAL -> Res.string.game_over_insufficient
    EndReason.FIFTY_MOVES -> Res.string.game_over_fifty
    EndReason.THREEFOLD_REPETITION -> Res.string.game_over_repetition
    null -> null
}

fun PositionProblem.label(): StringResource = when (this) {
    PositionProblem.MISSING_KING -> Res.string.problem_missing_king
    PositionProblem.TOO_MANY_KINGS -> Res.string.problem_too_many_kings
    PositionProblem.PAWN_ON_BACK_RANK -> Res.string.problem_pawn_on_back_rank
    PositionProblem.TOO_MANY_PIECES -> Res.string.problem_too_many_pieces
    PositionProblem.OPPONENT_IN_CHECK -> Res.string.problem_opponent_in_check
}

fun BoardThemeId.label(): StringResource = when (this) {
    BoardThemeId.CLASSIC -> Res.string.theme_classic
    BoardThemeId.WALNUT -> Res.string.theme_walnut
    BoardThemeId.GREEN -> Res.string.theme_green
    BoardThemeId.BLUE -> Res.string.theme_blue
    BoardThemeId.GRAPHITE -> Res.string.theme_graphite
}

fun MoveQuality.label(): StringResource = when (this) {
    MoveQuality.BEST -> Res.string.quality_best
    MoveQuality.EXCELLENT -> Res.string.quality_excellent
    MoveQuality.GOOD -> Res.string.quality_good
    MoveQuality.INACCURACY -> Res.string.quality_inaccuracy
    MoveQuality.MISTAKE -> Res.string.quality_mistake
    MoveQuality.BLUNDER -> Res.string.quality_blunder
}

fun MoveQuality.tip(): StringResource = when (this) {
    MoveQuality.BEST -> Res.string.tip_best
    MoveQuality.EXCELLENT -> Res.string.tip_excellent
    MoveQuality.GOOD -> Res.string.tip_good
    MoveQuality.INACCURACY -> Res.string.tip_inaccuracy
    MoveQuality.MISTAKE -> Res.string.tip_mistake
    MoveQuality.BLUNDER -> Res.string.tip_blunder
}

/** Annotation symbol shown next to the move, as in chess books. */
val MoveQuality.symbol: String
    get() = when (this) {
        MoveQuality.BEST -> "★"
        MoveQuality.EXCELLENT -> "!"
        MoveQuality.GOOD -> "✓"
        MoveQuality.INACCURACY -> "?!"
        MoveQuality.MISTAKE -> "?"
        MoveQuality.BLUNDER -> "??"
    }

val MoveQuality.color: Color
    get() = when (this) {
        MoveQuality.BEST -> Color(0xFF2FD27C)
        MoveQuality.EXCELLENT -> Color(0xFF4FC3F7)
        MoveQuality.GOOD -> Color(0xFF9CCC65)
        MoveQuality.INACCURACY -> Color(0xFFF5C542)
        MoveQuality.MISTAKE -> Color(0xFFFF9800)
        MoveQuality.BLUNDER -> Color(0xFFFF4B55)
    }

fun Side.label(): StringResource = if (this == Side.WHITE) Res.string.side_white else Res.string.side_black

fun Side.turnLabel(): StringResource = if (this == Side.WHITE) Res.string.turn_white else Res.string.turn_black
