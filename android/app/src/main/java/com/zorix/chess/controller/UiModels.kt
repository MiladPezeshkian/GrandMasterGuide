package com.zorix.chess.controller

import com.zorix.chess.core.Game
import com.zorix.chess.core.Move
import com.zorix.chess.core.Side
import com.zorix.chess.engine.uci.Score

/** Engine life cycle as shown to the user. */
sealed interface EngineStatus {
    /** Copying the neural network on first launch. */
    data class Preparing(val progress: Float) : EngineStatus
    data object Starting : EngineStatus
    data class Ready(val name: String, val build: String) : EngineStatus
    data class Failed(val message: String) : EngineStatus
}

/** One engine line converted for display. Scores are from White's point of view. */
data class EngineLine(
    val rank: Int,
    val depth: Int,
    val score: Score,
    val uci: List<String>,
    val san: List<String>,
    /** "12. Nf3 Nc6 13. Bb5" */
    val text: String,
) {
    val firstMove: Move? get() = uci.firstOrNull()?.let(Move::fromUci)
}

/** Engine output for one position. */
data class EngineView(
    val fen: String,
    val sideToMove: Side,
    val lines: List<EngineLine>,
    val depth: Int,
    val nodes: Long,
    val nps: Long,
    val timeMs: Long,
    val bestMove: Move?,
    val bestSan: String?,
    val finished: Boolean,
) {
    val score: Score? get() = lines.firstOrNull()?.score
}

/** The "Best move" (hint) feature, the mobile version of "Suggest for White/Black". */
sealed interface HintState {
    data object Idle : HintState

    data class Thinking(
        val fen: String,
        val startedAtMs: Long,
        val budgetMs: Int,
        val view: EngineView?,
    ) : HintState

    data class Ready(val view: EngineView) : HintState
}

/** A pawn reached the last rank; the user must pick the new piece. */
data class PendingPromotion(val from: Int, val to: Int, val side: Side)

/** Short feedback messages; the UI maps them to localised text. */
enum class UiMessage {
    ILLEGAL_MOVE,
    GAME_IS_OVER,
    ENGINE_NOT_READY,
    NO_LEGAL_MOVES,
    SUGGESTION_OUTDATED,
    FEN_INVALID,
    FEN_COPIED,
    PGN_COPIED,
    POSITION_LOADED,
    NOTHING_TO_UNDO,
    NOTHING_TO_REDO,
}

data class ChessUiState(
    val game: Game = Game.new(),
    val flipped: Boolean = false,
    val pendingPromotion: PendingPromotion? = null,
    val engine: EngineStatus = EngineStatus.Preparing(0f),
    val hint: HintState = HintState.Idle,
    val analysisOn: Boolean = false,
    val analysis: EngineView? = null,
    val settings: Settings = Settings(),
    /** Incremented on every move so the UI can trigger haptics/animations. */
    val moveCounter: Int = 0,
) {
    val fen: String get() = game.position.fen()
    val isThinking: Boolean get() = hint is HintState.Thinking

    /** The engine output that belongs to the position on the board, preferring a finished hint. */
    val currentView: EngineView?
        get() {
            val h = hint
            val fromHint = when (h) {
                is HintState.Ready -> h.view
                is HintState.Thinking -> h.view
                HintState.Idle -> null
            }
            return listOfNotNull(fromHint, analysis).firstOrNull { it.fen == fen }
        }
}
