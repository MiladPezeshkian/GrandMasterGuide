package com.zorix.chess.controller

import com.zorix.chess.coach.Coach
import com.zorix.chess.coach.CoachQuality
import com.zorix.chess.coach.MoveAnalysis
import com.zorix.chess.core.Game
import com.zorix.chess.core.Move
import com.zorix.chess.core.Notation
import com.zorix.chess.core.Position
import com.zorix.chess.core.Side
import com.zorix.chess.core.Tactics
import com.zorix.chess.engine.uci.Score
import com.zorix.chess.engine.uci.SearchLimit
import com.zorix.chess.learn.OpeningBook

/** Runs the engine for the coach and turns the result into an explained verdict. */
object CoachService {

    /** Opening name when [move] keeps the game inside the opening book. */
    fun bookName(gameBefore: Game, move: Move, lang: String): String? {
        if (gameBefore.start.fen() != Position.START_FEN) return null
        val moves = gameBefore.plies.map { it.move } + move
        return if (OpeningBook.isBook(moves)) OpeningBook.name(moves)?.name(lang) else null
    }

    /**
     * Analyses [move] played from the end of [gameBefore] and explains it to the mover.
     * Returns null when the engine is unavailable.
     */
    suspend fun rate(
        hub: EngineHub,
        gameBefore: Game,
        move: Move,
        lang: String,
        name: String?,
        thinkMs: Long = 900,
    ): MoveFeedback? {
        val before = gameBefore.position
        val after = before.play(move)
        val san = Notation.san(before, move)
        val whiteMoved = before.sideToMove == Side.WHITE
        if (Tactics.isCheckmate(after)) {
            val mate = Score(mate = if (whiteMoved) 1 else -1)
            val msg = Coach.explainOwnMove(
                MoveAnalysis(before, move, move, Score(mate = 1), listOf(move.uci), null, Score(mate = 1), emptyList()),
                lang, name, speechLanguage(lang),
            )
            return MoveFeedback(san, CoachQuality.BEST, san, mate, mate, msg.display, msg.speech)
        }
        val result = hub.run { eng ->
            val best = eng.search("fen ${before.fen()}", SearchLimit.MoveTime(thinkMs), multiPv = 2)
            val reply = if (after.legalMoves.isEmpty()) null else eng.search("fen ${after.fen()}", SearchLimit.MoveTime(thinkMs), multiPv = 1)
            best to reply
        } ?: return null
        val (bestSnap, afterSnap) = result
        val bestLine = bestSnap.best ?: return null
        val analysis = MoveAnalysis(
            before = before,
            move = move,
            bestMove = bestSnap.bestMove?.let(Move::fromUci)?.takeIf { before.isLegal(it) },
            bestScore = bestLine.score,
            bestPv = bestLine.pv,
            secondScore = bestSnap.lines.getOrNull(1)?.score,
            afterScore = afterSnap?.best?.score?.negate() ?: Score(centipawns = 0),
            replyPv = afterSnap?.best?.pv.orEmpty(),
            bookOpening = bookName(gameBefore, move, lang),
        )
        val msg = Coach.explainOwnMove(analysis, lang, name, speechLanguage(lang))
        return MoveFeedback(
            san = san,
            quality = msg.quality ?: CoachQuality.GOOD,
            bestSan = msg.bestSan,
            scoreBefore = analysis.bestScore.forWhite(whiteMoved),
            scoreAfter = analysis.afterScore.forWhite(whiteMoved),
            message = msg.display,
            speech = msg.speech,
            bestMove = msg.bestMove,
            expectedSan = msg.expectedSan,
        )
    }
}
