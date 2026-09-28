package com.zorix.chess.coach

import com.zorix.chess.core.Attacks
import com.zorix.chess.core.Move
import com.zorix.chess.core.PieceType
import com.zorix.chess.core.Position
import com.zorix.chess.core.Side
import com.zorix.chess.core.Squares
import com.zorix.chess.engine.uci.Score
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachTest {

    private fun facts(fen: String, uci: String) = Facts.of(Position.fromFen(fen), Move.fromUci(uci)!!)
    private fun sq(s: String) = Squares.parse(s)

    @Test fun seeCountsExchanges() {
        // Rook takes a knight defended by a pawn: wins 300, loses 500.
        val pos = Position.fromFen("4k3/8/4p3/3n4/8/8/8/3RK3 w - - 0 1")
        assertEquals(-200, Attacks.seeMove(pos, Move.fromUci("d1d5")!!))
        // Undefended knight: a clean win.
        val free = Position.fromFen("4k3/8/8/3n4/8/8/8/3RK3 w - - 0 1")
        assertEquals(300, Attacks.seeMove(free, Move.fromUci("d1d5")!!))
        assertEquals(mapOf(sq("d5") to 300), Attacks.hangingPieces(free, Side.BLACK))
    }

    @Test fun detectsKnightFork() {
        val f = facts("8/4k3/8/8/1q6/2N5/8/6K1 w - - 0 1", "c3d5")
        val fork = f.filterIsInstance<Fact.Fork>().single()
        assertEquals(setOf(PieceType.KING, PieceType.QUEEN), fork.targets.map { it.type }.toSet())
        assertTrue(f.any { it is Fact.Check })
    }

    @Test fun detectsPinSkewerAndDiscoveredAttack() {
        val pin = facts("4k3/8/2n5/8/8/8/8/4KB2 w - - 0 1", "f1b5").filterIsInstance<Fact.Pin>().single()
        assertEquals(sq("c6"), pin.pinned.square)
        assertEquals(PieceType.KING, pin.behind.type)

        val skewer = facts("8/8/8/q2k4/8/8/8/6KR w - - 0 1", "h1h5").filterIsInstance<Fact.Skewer>().single()
        assertEquals(PieceType.KING, skewer.front.type)
        assertEquals(PieceType.QUEEN, skewer.back.type)

        val disc = facts("4k3/4q3/8/8/4N3/8/8/K3R3 w - - 0 1", "e4c5").filterIsInstance<Fact.DiscoveredAttack>().single()
        assertEquals(sq("e1"), disc.attacker.square)
        assertEquals(sq("e7"), disc.target.square)
    }

    @Test fun capturesMatesCastlingAndHanging() {
        assertTrue(facts("4k3/8/8/3n4/8/8/8/3RK3 w - - 0 1", "d1d5").any { it is Fact.Captures && it.net == 300 })
        assertEquals(listOf(Fact.Checkmate), facts("6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1", "a1a8"))
        val castle = facts("r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4", "e1g1")
        assertTrue(castle.contains(Fact.Castles(kingside = true)))
        // Qh5?? next to the black knight on f6: the queen is simply lost.
        val hang = facts("rnbqkb1r/pppp1ppp/5n2/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 2 3", "d1h5")
        assertTrue(hang.toString(), hang.any { (it is Fact.Hangs && it.piece.type == PieceType.QUEEN) || it is Fact.Sacrifice })
    }

    @Test fun lineOutcomeCountsMaterial() {
        val pos = Position.fromFen("8/4k3/8/8/1q6/2N5/8/6K1 w - - 0 1")
        val out = Facts.outcome(pos, listOf("c3d5", "e7d6", "d5b4"), Side.WHITE)
        assertEquals(900, out.material)
        assertEquals(PieceType.QUEEN, out.gained.single().type)
    }

    @Test fun explainsABlunderByNameInEveryLanguage() {
        // White blunders the queen with Qh5 next to the f6 knight; best was developing Nf3.
        val before = Position.fromFen("rnbqkb1r/pppp1ppp/5n2/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 2 3")
        val a = MoveAnalysis(
            before = before,
            move = Move.fromUci("d1h5")!!,
            bestMove = Move.fromUci("g1f3"),
            bestScore = Score(centipawns = 30),
            bestPv = listOf("g1f3", "b8c6", "f1c4"),
            secondScore = Score(centipawns = 25),
            afterScore = Score(centipawns = -850),
            replyPv = listOf("f6h5", "g2g4", "h5f4"),
        )
        assertEquals(CoachQuality.BLUNDER, Coach.classify(a))
        val en = Coach.explainOwnMove(a, "en", "Milad")
        assertTrue(en.display, en.display.contains("Milad"))
        assertTrue(en.display, en.display.contains("queen"))
        assertTrue(en.display, en.display.contains("Nf3"))
        assertEquals("Nf3", en.bestSan)
        val fa = Coach.explainOwnMove(a, "fa", "میلاد")
        assertTrue(fa.display, fa.display.contains("میلاد") && fa.display.contains("وزیر"))
        assertTrue(fa.speech, fa.speech.contains("اسب اِف سه"))
        val ckb = Coach.explainOwnMove(a, "ckb", "میلاد")
        assertTrue(ckb.display, ckb.display.contains("میلاد") && ckb.display.contains("وەزیر"))
        println(en.display); println(fa.display); println(ckb.display); println(fa.speech)
    }

    @Test fun praisesAForkAndPredictsTheReply() {
        val before = Position.fromFen("8/4k3/8/8/1q6/2N5/8/6K1 w - - 0 1")
        val a = MoveAnalysis(
            before = before,
            move = Move.fromUci("c3d5")!!,
            bestMove = Move.fromUci("c3d5"),
            bestScore = Score(centipawns = 700),
            bestPv = listOf("c3d5", "e7d6", "d5b4"),
            secondScore = Score(centipawns = -800),
            afterScore = Score(centipawns = 700),
            replyPv = listOf("e7d6", "d5b4"),
        )
        assertEquals(CoachQuality.GREAT, Coach.classify(a))
        val msg = Coach.explainOwnMove(a, "en", "Sara")
        assertTrue(msg.display, msg.display.contains("fork"))
        assertTrue(msg.display, msg.display.contains("expects"))
        println(msg.display)
        println(Coach.explainOwnMove(a, "fa", "سارا").display)
    }

    @Test fun warnsAboutOpponentThreats() {
        // Black threatens Qxh2 mate after ...Qh4? Use a simple back-rank threat instead.
        val before = Position.fromFen("6k1/5ppp/8/8/8/8/r4PPP/1R4K1 b - - 0 1")
        val msg = Coach.explainOpponentMove(before, Move.fromUci("a2a1")!!, "en", "Ali", "Zorix")
        assertTrue(msg.display, msg.display.contains("Zorix played"))
        println(msg.display)
        val hint = Coach.explainBestMove(
            Position.fromFen("8/4k3/8/8/1q6/2N5/8/6K1 w - - 0 1"), Move.fromUci("c3d5")!!,
            Score(centipawns = 700), listOf("c3d5", "e7d6", "d5b4"), "fa",
        )
        assertTrue(hint.display, hint.display.contains("چنگال"))
        println(hint.display)
    }

    @Test fun sanIsSpokenNaturally() {
        val en = Phrases.of("en", TextMode.SPEECH, Side.WHITE)
        assertEquals("knight to f3", en.move("Nf3"))
        assertEquals("pawn takes d5, check", en.move("exd5+"))
        assertEquals("castles kingside", en.move("O-O"))
        val fa = Phrases.of("fa", TextMode.SPEECH, Side.WHITE)
        assertEquals("وزیر زد اِچ هفت، کیش و مات", fa.move("Qxh7#"))
    }
}
