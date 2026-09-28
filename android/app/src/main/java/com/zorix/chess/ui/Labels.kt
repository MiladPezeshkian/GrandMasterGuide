package com.zorix.chess.ui

import androidx.annotation.StringRes
import com.zorix.chess.R
import com.zorix.chess.controller.BoardThemeId
import com.zorix.chess.controller.UiMessage
import com.zorix.chess.core.EndReason
import com.zorix.chess.core.GameResult
import com.zorix.chess.core.GameStatus
import com.zorix.chess.core.PositionProblem
import com.zorix.chess.core.Side

@StringRes
fun UiMessage.label(): Int = when (this) {
    UiMessage.ILLEGAL_MOVE -> R.string.msg_illegal_move
    UiMessage.GAME_IS_OVER -> R.string.msg_game_over
    UiMessage.ENGINE_NOT_READY -> R.string.msg_engine_not_ready
    UiMessage.NO_LEGAL_MOVES -> R.string.msg_no_legal_moves
    UiMessage.SUGGESTION_OUTDATED -> R.string.msg_suggestion_outdated
    UiMessage.FEN_INVALID -> R.string.msg_fen_invalid
    UiMessage.FEN_COPIED -> R.string.msg_fen_copied
    UiMessage.PGN_COPIED -> R.string.msg_pgn_copied
    UiMessage.POSITION_LOADED -> R.string.msg_position_loaded
    UiMessage.NOTHING_TO_UNDO -> R.string.msg_nothing_to_undo
    UiMessage.NOTHING_TO_REDO -> R.string.msg_nothing_to_redo
}

@StringRes
fun GameStatus.label(): Int? = when (reason) {
    EndReason.CHECKMATE ->
        if (result == GameResult.WHITE_WINS) R.string.game_over_checkmate_white else R.string.game_over_checkmate_black
    EndReason.STALEMATE -> R.string.game_over_stalemate
    EndReason.INSUFFICIENT_MATERIAL -> R.string.game_over_insufficient
    EndReason.FIFTY_MOVES -> R.string.game_over_fifty
    EndReason.THREEFOLD_REPETITION -> R.string.game_over_repetition
    null -> null
}

@StringRes
fun PositionProblem.label(): Int = when (this) {
    PositionProblem.MISSING_KING -> R.string.problem_missing_king
    PositionProblem.TOO_MANY_KINGS -> R.string.problem_too_many_kings
    PositionProblem.PAWN_ON_BACK_RANK -> R.string.problem_pawn_on_back_rank
    PositionProblem.TOO_MANY_PIECES -> R.string.problem_too_many_pieces
    PositionProblem.OPPONENT_IN_CHECK -> R.string.problem_opponent_in_check
}

@StringRes
fun BoardThemeId.label(): Int = when (this) {
    BoardThemeId.CLASSIC -> R.string.theme_classic
    BoardThemeId.WALNUT -> R.string.theme_walnut
    BoardThemeId.GREEN -> R.string.theme_green
    BoardThemeId.BLUE -> R.string.theme_blue
    BoardThemeId.GRAPHITE -> R.string.theme_graphite
}

@StringRes
fun Side.label(): Int = if (this == Side.WHITE) R.string.side_white else R.string.side_black

@StringRes
fun Side.turnLabel(): Int = if (this == Side.WHITE) R.string.turn_white else R.string.turn_black
