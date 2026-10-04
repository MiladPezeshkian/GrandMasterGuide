package com.zorix.chess.coach

import com.zorix.chess.core.Attacks
import com.zorix.chess.core.Move
import com.zorix.chess.core.Notation
import com.zorix.chess.core.Position
import com.zorix.chess.engine.uci.Score

/** A coach message: what to show and what the voice should say. */
data class CoachMessage(
    val quality: CoachQuality?,
    val display: String,
    val speech: String,
    /** Better move in SAN, when the coach recommends one. */
    val bestSan: String? = null,
    val bestMove: Move? = null,
    /** The opponent's expected reply (the engine's prediction). */
    val expectedSan: String? = null,
    /** The message warns about a threat against the listener. */
    val warning: Boolean = false,
)

/** Engine data about one played move, everything from the mover's point of view. */
data class MoveAnalysis(
    val before: Position,
    val move: Move,
    /** Engine's best move in [before], its score and principal variation. */
    val bestMove: Move?,
    val bestScore: Score,
    val bestPv: List<String>,
    /** Score of the second best move, when known (to recognise "only moves"). */
    val secondScore: Score?,
    /** Score after [move] and the opponent's best reply line. */
    val afterScore: Score,
    val replyPv: List<String>,
    /** Opening name when the move is still in the opening book. */
    val bookOpening: String? = null,
)

/**
 * The Zorix coach: classifies moves like chess.com's game review and explains them in plain
 * language, addressing the player by name, in English, Persian or Kurdish (display and voice).
 */
object Coach {

    /** How good the move was. */
    fun classify(a: MoveAnalysis, facts: List<Fact> = Facts.of(a.before, a.move)): CoachQuality {
        val bestWin = a.bestScore.winChance()
        val afterWin = a.afterScore.winChance()
        val loss = (bestWin - afterWin).coerceAtLeast(0.0)
        val isBest = a.bestMove == a.move
        if (a.bookOpening != null && loss < 0.05) return CoachQuality.BOOK
        var q = when {
            isBest || loss < 0.005 -> CoachQuality.BEST
            loss < 0.02 -> CoachQuality.EXCELLENT
            loss < 0.05 -> CoachQuality.GOOD
            loss < 0.10 -> CoachQuality.INACCURACY
            loss < 0.20 -> CoachQuality.MISTAKE
            else -> CoachQuality.BLUNDER
        }
        if (q == CoachQuality.BEST || q == CoachQuality.EXCELLENT) {
            val sacrifice = facts.any { it is Fact.Sacrifice && Attacks.value(it.piece.type) >= 300 } ||
                facts.any { it is Fact.Captures && it.net <= -200 }
            if (sacrifice && afterWin >= 0.55 && bestWin < 0.97) return CoachQuality.BRILLIANT
            val second = a.secondScore
            if (q == CoachQuality.BEST && second != null && bestWin - second.winChance() >= 0.20 && afterWin >= 0.4) {
                return CoachQuality.GREAT
            }
        }
        if (q in setOf(CoachQuality.INACCURACY, CoachQuality.MISTAKE, CoachQuality.BLUNDER)) {
            val bestLine = Facts.outcome(a.before, a.bestPv, a.before.sideToMove)
            val bestWins = (a.bestScore.mate ?: 0) > 0 || bestLine.material >= 250
            val after = a.before.play(a.move)
            val reply = Facts.outcome(after, a.replyPv, a.before.sideToMove.opposite)
            val playedSafe = reply.material < 80 && (a.afterScore.mate ?: 1) > 0
            if (bestWins && playedSafe && afterWin >= 0.3) q = CoachQuality.MISS
        }
        return q
    }

    /** Full coach message about a move the listener just played. */
    fun explainOwnMove(a: MoveAnalysis, lang: String, name: String?, speechLang: String = lang): CoachMessage {
        val facts = Facts.of(a.before, a.move)
        val quality = classify(a, facts)
        val san = Notation.san(a.before, a.move)
        val bestSan = a.bestMove?.takeIf { a.before.isLegal(it) }?.let { Notation.san(a.before, it) }
        val after = a.before.play(a.move)
        val replyMove = a.replyPv.firstOrNull()?.let(Move::fromUci)?.takeIf { after.isLegal(it) }
        val replySan = replyMove?.let { Notation.san(after, it) }
        val variant = (a.before.fen().hashCode() xor a.move.uci.hashCode()) and 0x7fffffff

        fun render(mode: TextMode): String {
            val p = Phrases.of(if (mode == TextMode.SPEECH) speechLang else lang, mode, a.before.sideToMove)
            val who = name?.trim()?.takeIf { it.isNotEmpty() } ?: p.defaultName()
            val out = ArrayList<String>()
            out += p.headline(quality, who, variant)
            val positive = quality in setOf(
                CoachQuality.BRILLIANT, CoachQuality.GREAT, CoachQuality.BEST,
                CoachQuality.EXCELLENT, CoachQuality.GOOD, CoachQuality.BOOK,
            )
            if (positive) {
                if (quality == CoachQuality.BOOK && a.bookOpening != null) out += p.bookMove(a.bookOpening)
                val good = facts.filter { isPositive(it) }.take(2)
                good.mapNotNullTo(out) { p.fact(it) }
                if (quality == CoachQuality.BRILLIANT) out += p.sacrificeWorks()
                val mate = a.afterScore.mate
                if (mate != null && mate > 0 && facts.none { it == Fact.Checkmate }) out += p.fact(Fact.ForcedMate(mate)) ?: ""
                if (quality == CoachQuality.GREAT) out += p.onlyMove()
                if (replySan != null && !after.legalMoves.isEmpty() && facts.none { it == Fact.Checkmate }) out += p.expect(replySan)
            } else {
                problem(a, facts, after, replyMove, replySan)?.let { f -> p.fact(f)?.let { out += it } }
                if (bestSan != null && a.bestMove != a.move) {
                    out += p.better(bestSan, reasonFor(a, a.bestMove!!, p))
                }
                if (mode == TextMode.DISPLAY) p.tip(quality, variant)?.let { out += it }
            }
            return p.join(out)
        }

        return CoachMessage(
            quality = quality,
            display = render(TextMode.DISPLAY),
            speech = render(TextMode.SPEECH),
            bestSan = bestSan?.takeIf { a.bestMove != a.move },
            bestMove = a.bestMove?.takeIf { it != a.move },
            expectedSan = replySan,
        )
    }

    /** Explains a move played by the opponent (Zorix in play mode) and warns about new threats. */
    fun explainOpponentMove(
        before: Position,
        move: Move,
        lang: String,
        name: String?,
        opponent: String,
        speechLang: String = lang,
    ): CoachMessage {
        val facts = Facts.of(before, move).filter { isPositive(it) || it is Fact.Check }
        val after = before.play(move)
        val threats = if (after.legalMoves.isEmpty()) emptyList() else Facts.threats(after)
        val san = Notation.san(before, move)
        fun render(mode: TextMode): String {
            val p = Phrases.of(if (mode == TextMode.SPEECH) speechLang else lang, mode, before.sideToMove.opposite)
            val who = name?.trim()?.takeIf { it.isNotEmpty() } ?: p.defaultName()
            val out = ArrayList<String>()
            out += p.opponentPlayed(opponent, san)
            facts.take(2).mapNotNullTo(out) { p.fact(it) }
            if (threats.isNotEmpty()) {
                out += p.watchOut(who)
                threats.take(2).mapNotNullTo(out) { p.fact(it) }
            }
            return p.join(out)
        }
        return CoachMessage(null, render(TextMode.DISPLAY), render(TextMode.SPEECH), warning = threats.isNotEmpty())
    }

    /** Explanation for the "Best move" hint: what the move does and how the game may continue. */
    fun explainBestMove(
        position: Position,
        best: Move,
        score: Score,
        pv: List<String>,
        lang: String,
        speechLang: String = lang,
    ): CoachMessage {
        val facts = Facts.of(position, best).filter { isPositive(it) }
        val san = Notation.san(position, best)
        val outcome = Facts.outcome(position, pv, position.sideToMove)
        val threats = Facts.threats(position)
        val line = Notation.variationToSan(position, pv, 6)
        fun render(mode: TextMode): String {
            val p = Phrases.of(if (mode == TextMode.SPEECH) speechLang else lang, mode, position.sideToMove)
            val out = ArrayList<String>()
            out += p.bestMoveIs(san)
            if (mode == TextMode.DISPLAY) threats.firstOrNull()?.let { t -> p.fact(t)?.let { out += it } }
            facts.take(2).mapNotNullTo(out) { p.fact(it) }
            val mate = score.mate
            if (mate != null && mate > 1) {
                out += p.fact(Fact.ForcedMate(mate)) ?: ""
            } else if (facts.none { it is Fact.Captures && it.net > 0 }) {
                Facts.describe(outcome.material, outcome.gained)?.let { (piece, amount) ->
                    out += p.fact(Fact.WinsMaterial(piece, amount)) ?: ""
                }
            }
            if (line.size >= 2 && mode == TextMode.DISPLAY) out += p.expectedLine(p.line(line))
            return p.join(out)
        }
        return CoachMessage(null, render(TextMode.DISPLAY), render(TextMode.SPEECH), san, best)
    }

    /** Explanation of a correct move in a puzzle or lesson solution. */
    fun explainSolutionMove(position: Position, move: Move, line: List<String>, lang: String, speechLang: String = lang): CoachMessage {
        val facts = Facts.of(position, move).filter { isPositive(it) }
        val outcome = Facts.outcome(position, line, position.sideToMove, maxPlies = 12)
        fun render(mode: TextMode): String {
            val p = Phrases.of(if (mode == TextMode.SPEECH) speechLang else lang, mode, position.sideToMove)
            val out = ArrayList<String>()
            facts.take(2).mapNotNullTo(out) { p.fact(it) }
            if (facts.none { it == Fact.Checkmate }) {
                if (outcome.mates) {
                    out += p.fact(Fact.ForcedMate(((line.size + 1) / 2).coerceAtLeast(1))) ?: ""
                } else {
                    Facts.describe(outcome.material, outcome.gained)?.let { (piece, amount) ->
                        out += p.fact(Fact.WinsMaterial(piece, amount)) ?: ""
                    }
                }
            }
            return p.join(out)
        }
        return CoachMessage(null, render(TextMode.DISPLAY), render(TextMode.SPEECH))
    }

    private fun problem(a: MoveAnalysis, facts: List<Fact>, after: Position, reply: Move?, replySan: String?): Fact? {
        val mate = a.afterScore.mate
        if (mate != null && mate < 0 && replySan != null) return Fact.AllowsMate(-mate, replySan)
        if (reply != null && replySan != null) {
            val them = a.before.sideToMove.opposite
            val outcome = Facts.outcome(after, a.replyPv, them)
            val bestOutcome = Facts.outcome(a.before, a.bestPv, a.before.sideToMove)
            // Material the opponent gains beyond what the best line would have conceded.
            val swing = outcome.material + bestOutcome.material.coerceAtMost(0)
            Facts.describe(swing, outcome.gained)?.let { (piece, amount) ->
                val how = Facts.of(after, reply).firstOrNull { isTactic(it) }
                return Fact.LosesMaterial(replySan, piece, amount, how)
            }
        }
        return facts.firstOrNull { it is Fact.Hangs || it == Fact.KingWalksEarly || it == Fact.QueenOutEarly }
    }

    /** Why the best move is better: its strongest idea. */
    private fun reasonFor(a: MoveAnalysis, best: Move, p: Phrases): String? {
        val mate = a.bestScore.mate
        if (mate != null && mate > 0) {
            return p.fact(if (mate == 1) Fact.Checkmate else Fact.ForcedMate(mate))
        }
        val facts = Facts.of(a.before, best).filter { isPositive(it) }
        val first = facts.firstOrNull()?.let { p.fact(it) }
        val outcome = Facts.outcome(a.before, a.bestPv, a.before.sideToMove)
        val wins = Facts.describe(outcome.material, outcome.gained)?.let { (piece, amount) -> p.fact(Fact.WinsMaterial(piece, amount)) }
        return when {
            first != null && wins != null && facts.first() !is Fact.Captures -> p.join(listOf(first, wins))
            first != null -> first
            else -> wins
        }
    }

    fun isPositive(f: Fact): Boolean = when (f) {
        is Fact.Hangs, Fact.KingWalksEarly, Fact.QueenOutEarly, is Fact.AllowsMate, is Fact.LosesMaterial,
        is Fact.ThreatCapture, is Fact.ThreatMate -> false
        is Fact.Captures -> f.net >= 0
        else -> true
    }

    private fun isTactic(f: Fact): Boolean =
        f is Fact.Fork || f is Fact.Pin || f is Fact.Skewer || f is Fact.DiscoveredAttack ||
            (f is Fact.Check && f.double) || (f is Fact.Captures && f.net > 0) || f is Fact.Promotes

    /** Shortcut for tests and tools: SAN of a UCI move in [pos]. */
    fun san(pos: Position, uci: String): String = Move.fromUci(uci)?.let { Notation.san(pos, it) } ?: uci
}
