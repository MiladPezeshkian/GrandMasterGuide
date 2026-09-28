package com.zorix.chess.learn

import com.zorix.chess.core.Move
import com.zorix.chess.core.Position
import com.zorix.chess.core.Tactics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleTest {

    @Test fun idsAreUnique() = assertEquals(PuzzleSet.all.size, PuzzleSet.all.map { it.id }.toSet().size)

    @Test fun everyBookSolutionForcesMate() {
        for (p in PuzzleSet.all) {
            val pos = Position.fromFen(p.fen)
            val move = Move.fromUci(p.solution)
            assertNotNull("puzzle ${p.id}: bad move", move)
            assertTrue("puzzle ${p.id}: ${p.solution} does not force mate in ${p.mateIn}", Tactics.forcesMate(pos, move!!, p.mateIn))
        }
    }

    @Test fun mateInTwoPuzzlesHaveNoMateInOne() {
        for (p in PuzzleSet.all.filter { it.mateIn == 2 }) {
            assertTrue("puzzle ${p.id} has a shortcut", Tactics.matingMoves(Position.fromFen(p.fen)).isEmpty())
        }
    }

    @Test fun defenderAlwaysHasAReplyInMateInTwo() {
        for (p in PuzzleSet.all.filter { it.mateIn == 2 }) {
            val pos = Position.fromFen(p.fen)
            val reply = Tactics.bestDefence(pos, Move.fromUci(p.solution)!!, p.mateIn)
            assertNotNull("puzzle ${p.id}", reply)
        }
    }

    @Test fun wrongMoveIsRejected() {
        val pos = Position.fromFen(PuzzleSet.all.first().fen)
        assertFalse(Tactics.forcesMate(pos, Move.fromUci("g1g2")!!, 1))
    }
}
