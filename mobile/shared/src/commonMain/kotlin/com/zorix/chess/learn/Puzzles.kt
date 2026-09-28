package com.zorix.chess.learn

/**
 * A checkmate puzzle. Answers are graded with exact rules ([com.zorix.chess.core.Tactics]),
 * so any correct solution is accepted, fully offline. [solution] is one known first move.
 */
data class Puzzle(val id: Int, val fen: String, val mateIn: Int, val solution: String)

/** Classic patterns (back rank, smothered mate, Scholar's mate, promotion...) and verified mates in two. */
object PuzzleSet {
    val all: List<Puzzle> = listOf(
        Puzzle(1, "6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1", 1, "a1a8"),
        Puzzle(2, "r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4", 1, "h5f7"),
        Puzzle(3, "rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR b KQkq - 0 2", 1, "d8h4"),
        Puzzle(4, "6rk/6pp/8/6N1/8/8/8/6K1 w - - 0 1", 1, "g5f7"),
        Puzzle(5, "6k1/4Pppp/8/8/8/8/8/6K1 w - - 0 1", 1, "e7e8q"),
        Puzzle(6, "r5k1/5ppp/8/8/8/8/5PPP/6K1 b - - 0 1", 1, "a8a1"),
        Puzzle(7, "3k4/8/3K4/8/8/8/8/7R w - - 0 1", 1, "h1h8"),
        Puzzle(8, "k7/8/1R6/8/8/8/8/1Q4K1 w - - 0 1", 1, "b6a6"),
        Puzzle(9, "6k1/5p1p/6pB/8/8/8/8/3Q2K1 w - - 0 1", 1, "d1d8"),
        Puzzle(10, "4k3/8/4K3/8/8/8/8/Q7 w - - 0 1", 1, "a1h8"),
        Puzzle(11, "7k/K1R5/6p1/8/p7/4R3/8/8 w - - 0 1", 1, "e3e8"),
        Puzzle(12, "4k3/R6R/8/8/8/K2p2b1/8/8 w - - 0 1", 1, "h7h8"),
        Puzzle(13, "7k/2R5/6K1/8/2N5/1p6/8/8 w - - 0 1", 1, "c7c8"),
        Puzzle(14, "2k5/3r4/1K6/8/8/7B/8/R7 w - - 0 1", 1, "a1a8"),
        Puzzle(15, "2KBk3/8/3Q4/1p6/8/6rn/8/8 w - - 0 1", 1, "d6e7"),
        Puzzle(16, "8/7k/5KR1/8/5p2/6R1/8/8 w - - 0 1", 1, "g3h3"),
        Puzzle(17, "k7/8/1K3R2/8/8/8/1R1n4/8 w - - 0 1", 1, "f6f8"),
        Puzzle(18, "3k4/6R1/5p2/7r/1R5b/K7/8/8 w - - 0 1", 1, "b4b8"),
        Puzzle(19, "k7/4Q3/8/2N5/8/8/7b/5K2 w - - 0 1", 1, "e7b7"),
        Puzzle(20, "2k5/1b6/5B2/3Q4/2K5/5r2/1r6/8 w - - 0 1", 1, "d5d8"),
        Puzzle(21, "1k6/8/8/3N3p/1K6/8/5Q2/8 w - - 0 1", 2, "f2b6"),
        Puzzle(22, "1k6/8/2Q5/8/8/8/2B1n1K1/8 w - - 0 1", 2, "c6b6"),
        Puzzle(23, "2Q5/4K2k/5B2/8/8/8/1p4p1/8 w - - 0 1", 2, "e7f7"),
        Puzzle(24, "8/3k4/6R1/4K2R/8/7p/8/8 w - - 0 1", 2, "h5h7"),
        Puzzle(25, "8/1k6/7R/5K2/3p4/4R3/1p1p4/8 w - - 0 1", 2, "e3e7"),
        Puzzle(26, "4k3/8/8/3p2K1/5QB1/8/6b1/8 w - - 0 1", 2, "g5f6"),
        Puzzle(27, "4k3/8/1Q6/r3N3/8/1K6/8/8 w - - 0 1", 2, "b6e6"),
        Puzzle(28, "3R4/k7/4Q3/5p1p/2K5/7r/8/8 w - - 0 1", 2, "d8d7"),
        Puzzle(29, "8/5k1K/2R4n/8/8/R7/8/8 w - - 0 1", 2, "a3a7"),
        Puzzle(30, "k7/4p3/8/8/K5R1/6p1/8/4R3 w - - 0 1", 2, "e1e7"),
        Puzzle(31, "3k4/5K2/8/5p2/2p5/4R2R/3n4/8 w - - 0 1", 2, "e3e7"),
        Puzzle(32, "8/6k1/8/8/5R2/K7/1p6/2Q5 w - - 0 1", 2, "c1g1"),
        Puzzle(33, "k7/8/6B1/8/4p3/4Q3/1n6/K7 w - - 0 1", 2, "e3b6"),
        Puzzle(34, "5RR1/4k3/6K1/8/3n4/1n6/8/8 w - - 0 1", 2, "f8d8"),
        Puzzle(35, "6k1/1Q6/8/8/4nB1p/4K3/8/8 w - - 0 1", 2, "f4h6"),
        Puzzle(36, "6k1/8/5K2/3N4/b6p/8/3Q4/8 w - - 0 1", 2, "d2h6"),
        Puzzle(37, "8/k3K3/3r4/8/8/2R5/8/1R6 w - - 0 1", 2, "e7d6"),
        Puzzle(38, "7k/4K3/8/5Q2/8/2b5/4pR2/8 w - - 0 1", 2, "f2h2"),
        Puzzle(39, "3N4/k7/8/1K6/8/2p5/5r2/4Q3 w - - 0 1", 2, "e1e7"),
        Puzzle(40, "k7/1r6/8/3R4/1Q6/8/K7/8 w - - 0 1", 2, "d5d8"),
    )
}
