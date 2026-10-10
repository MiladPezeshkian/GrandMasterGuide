package com.zorix.chess.learn

import com.zorix.chess.core.Move
import com.zorix.chess.core.Position
import com.zorix.chess.core.Squares
import com.zorix.chess.core.Tactics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Checks the shipped curriculum and puzzle bank with the app's own chess rules. */
class CurriculumTest {

    private val dir = File(System.getProperty("zorix.content") ?: "src/commonMain/composeResources/files")

    private val courses: List<Course> by lazy {
        val index = Json.parse(File(dir, "learn/index.json").readText()).obj()["courses"].arr().map { it as String }
        index.map { CurriculumParser.course(File(dir, "learn/$it.json").readText()) }
    }

    private val lessons get() = courses.flatMap { it.lessons }

    private fun position(fen: String, where: String): Position =
        runCatching { Position.fromFen(fen) }.getOrElse { throw AssertionError("$where: bad FEN $fen", it) }

    private fun move(p: Position, uci: String, where: String): Move {
        val m = Move.fromUci(uci) ?: throw AssertionError("$where: bad move $uci")
        assertTrue("$where: illegal move $uci in ${p.fen()}", p.isLegal(m))
        return m
    }

    @Test fun thereAreAtLeast300Lessons() {
        assertTrue("only ${lessons.size} lessons", lessons.size >= 300)
    }

    @Test fun lessonIdsAreUnique() {
        val ids = lessons.map { it.id }
        assertEquals(ids.groupBy { it }.filterValues { it.size > 1 }.keys.toString(), ids.size, ids.toSet().size)
    }

    @Test fun everyLessonIsTranslatedAndHasExercises() {
        for (l in lessons) {
            assertTrue("${l.id}: no exercises", l.exercises > 0)
            assertTrue("${l.id}: level ${l.level}", l.level in 1..5)
            for (lang in listOf("en", "fa", "ckb")) {
                assertTrue("${l.id}: title missing in $lang", l.title[lang].isNotBlank())
                for ((i, s) in l.steps.withIndex()) {
                    assertTrue("${l.id} step $i: prompt missing in $lang", s.prompt[lang].isNotBlank() || s is Step.Squares)
                }
            }
        }
    }

    @Test fun everyExerciseIsSolvable() {
        for (l in lessons) for ((i, s) in l.steps.withIndex()) {
            val where = "${l.id} step $i"
            when (s) {
                is Step.Theory -> s.fen?.let { position(it, where) }
                is Step.Stars -> {
                    position(s.fen, where)
                    assertTrue("$where: no stars", s.stars.isNotEmpty())
                    s.stars.forEach { Squares.parse(it) }
                }
                is Step.Capture -> {
                    val p = position(s.fen, where)
                    assertTrue("$where: nothing to capture", p.pieces().count { it?.side == p.sideToMove.opposite } > 0)
                }
                is Step.Puzzle -> {
                    var p = position(s.fen, where)
                    s.last?.let { last -> move(position(s.pre ?: error("$where: last without pre"), where), last, "$where (last)") }
                    for (u in s.moves) p = p.play(move(p, u, where))
                }
                is Step.Mate -> {
                    val p = position(s.fen, where)
                    if (s.n <= 2) {
                        assertTrue("$where: no mate in ${s.n}", p.legalMoves.any { Tactics.forcesMate(p, it, s.n) })
                    }
                }
                is Step.GoalStep -> {
                    val p = position(s.fen, where)
                    assertTrue("$where: no move reaches ${s.goal}", p.legalMoves.any { LessonSession.satisfies(s.goal, p, it) })
                }
                is Step.Quiz -> {
                    s.fen?.let { position(it, where) }
                    assertTrue("$where: answer ${s.answer} of ${s.options.size}", s.answer in s.options.indices)
                }
                is Step.Squares -> assertTrue(where, s.count > 0)
                is Step.Best -> {
                    val p = position(s.fen, where)
                    assertTrue("$where: nothing accepted", s.accept.isNotEmpty())
                    s.accept.forEach { move(p, it, where) }
                }
                is Step.Line -> {
                    var p = Position.fromFen(START)
                    assertTrue("$where: empty line", s.moves.isNotEmpty())
                    for (u in s.moves) p = p.play(move(p, u, where))
                }
                is Step.Play -> {
                    val p = position(s.fen, where)
                    assertFalse("$where: game already over", p.legalMoves.isEmpty())
                }
            }
        }
    }

    @Test fun puzzleBankIsValid() {
        val bank = CurriculumParser.puzzles(File(dir, "puzzles.json").readText())
        assertTrue("only ${bank.size} puzzles", bank.size >= 100)
        assertEquals(bank.size, bank.map { it.id }.toSet().size)
        for (pz in bank) {
            val where = "puzzle ${pz.id}"
            var p = position(pz.fen, where)
            assertTrue("$where: rating ${pz.rating}", pz.rating in 300..3000)
            pz.last?.let { move(position(pz.pre ?: error("$where: last without pre"), where), it, "$where (last)") }
            for ((i, u) in pz.moves.withIndex()) {
                p = p.play(move(p, u, where))
                if (i == pz.moves.lastIndex && "mate" in pz.themes) assertTrue("$where: does not end in mate", Tactics.isCheckmate(p))
            }
        }
    }

    private companion object {
        const val START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    }
}
