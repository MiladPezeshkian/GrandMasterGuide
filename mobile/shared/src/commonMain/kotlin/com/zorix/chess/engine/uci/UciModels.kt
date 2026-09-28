package com.zorix.chess.engine.uci

/**
 * Engine evaluation. UCI reports scores from the side to move's point of view;
 * use [forWhite] before showing them on an evaluation bar.
 */
data class Score(val centipawns: Int? = null, val mate: Int? = null) {

    val isMate: Boolean get() = mate != null

    fun negate(): Score = Score(centipawns?.let { -it }, mate?.let { -it })

    fun forWhite(whiteToMove: Boolean): Score = if (whiteToMove) this else negate()

    /** Pawn units clamped for display bars; mate scores map to +/- [cap]. */
    fun pawns(cap: Double = 10.0): Double = when {
        mate != null -> if (mate > 0) cap else -cap
        centipawns != null -> (centipawns / 100.0).coerceIn(-cap, cap)
        else -> 0.0
    }

    /** Win probability (0..1) for the side the score belongs to, lichess-style logistic curve. */
    fun winChance(): Double = when {
        mate != null -> if (mate > 0) 1.0 else 0.0
        centipawns != null -> 1.0 / (1.0 + kotlin.math.exp(-0.00368208 * centipawns))
        else -> 0.5
    }

    /** "+1.25", "-0.40", "#3", "#-2". */
    fun format(): String = when {
        mate != null -> "#$mate"
        centipawns != null -> {
            val v = centipawns / 100.0
            (if (v > 0) "+" else "") + formatDecimal(v, 2)
        }
        else -> "0.00"
    }
}

/** Win / draw / loss in permille, when the engine reports it (UCI_ShowWDL). */
data class Wdl(val win: Int, val draw: Int, val loss: Int)

enum class Bound { LOWER, UPPER }

/** One principal variation from an "info ... pv ..." line. */
data class PvLine(
    val multipv: Int,
    val depth: Int,
    val selDepth: Int,
    val score: Score,
    val bound: Bound?,
    val pv: List<String>,
    val nodes: Long,
    val nps: Long,
    val timeMs: Long,
    val wdl: Wdl?,
)

/** Everything known about the running (or finished) search. */
data class AnalysisSnapshot(
    val lines: List<PvLine> = emptyList(),
    val depth: Int = 0,
    val nodes: Long = 0,
    val nps: Long = 0,
    val timeMs: Long = 0,
    val bestMove: String? = null,
    val ponderMove: String? = null,
    val finished: Boolean = false,
) {
    val best: PvLine? get() = lines.firstOrNull()
}

sealed interface SearchLimit {
    val uci: String

    data class MoveTime(val millis: Long) : SearchLimit {
        override val uci: String get() = "movetime $millis"
    }

    data class Depth(val plies: Int) : SearchLimit {
        override val uci: String get() = "depth $plies"
    }

    data class Nodes(val count: Long) : SearchLimit {
        override val uci: String get() = "nodes $count"
    }

    data object Infinite : SearchLimit {
        override val uci: String get() = "infinite"
    }
}

/** Parsed "info" line. Only the fields the app uses are kept. */
data class InfoLine(
    val depth: Int? = null,
    val selDepth: Int? = null,
    val multipv: Int? = null,
    val score: Score? = null,
    val bound: Bound? = null,
    val nodes: Long? = null,
    val nps: Long? = null,
    val timeMs: Long? = null,
    val wdl: Wdl? = null,
    val pv: List<String>? = null,
    val string: String? = null,
)

object UciParser {

    fun parseInfo(line: String): InfoLine? {
        val t = line.trim().split(Regex("\\s+"))
        if (t.isEmpty() || t[0] != "info") return null
        var info = InfoLine()
        var i = 1
        while (i < t.size) {
            when (t[i]) {
                "depth" -> info = info.copy(depth = t.getOrNull(++i)?.toIntOrNull())
                "seldepth" -> info = info.copy(selDepth = t.getOrNull(++i)?.toIntOrNull())
                "multipv" -> info = info.copy(multipv = t.getOrNull(++i)?.toIntOrNull())
                "nodes" -> info = info.copy(nodes = t.getOrNull(++i)?.toLongOrNull())
                "nps" -> info = info.copy(nps = t.getOrNull(++i)?.toLongOrNull())
                "time" -> info = info.copy(timeMs = t.getOrNull(++i)?.toLongOrNull())
                "score" -> {
                    val kind = t.getOrNull(++i)
                    val value = t.getOrNull(++i)?.toIntOrNull()
                    info = when (kind) {
                        "cp" -> info.copy(score = Score(centipawns = value))
                        "mate" -> info.copy(score = Score(mate = value))
                        else -> info
                    }
                    when (t.getOrNull(i + 1)) {
                        "lowerbound" -> { info = info.copy(bound = Bound.LOWER); i++ }
                        "upperbound" -> { info = info.copy(bound = Bound.UPPER); i++ }
                    }
                }
                "wdl" -> {
                    val w = t.getOrNull(i + 1)?.toIntOrNull()
                    val d = t.getOrNull(i + 2)?.toIntOrNull()
                    val l = t.getOrNull(i + 3)?.toIntOrNull()
                    if (w != null && d != null && l != null) info = info.copy(wdl = Wdl(w, d, l))
                    i += 3
                }
                "pv" -> {
                    info = info.copy(pv = t.subList(i + 1, t.size).toList())
                    i = t.size
                }
                "string" -> {
                    info = info.copy(string = t.subList(i + 1, t.size).joinToString(" "))
                    i = t.size
                }
                "currmove", "currmovenumber", "hashfull", "tbhits", "cpuload", "refutation", "currline" -> i++
            }
            i++
        }
        return info
    }

    /** "bestmove e2e4 ponder e7e5" -> (e2e4, e7e5). "(none)" means no legal move. */
    fun parseBestMove(line: String): Pair<String?, String?> {
        val t = line.trim().split(Regex("\\s+"))
        val best = t.getOrNull(1)?.takeUnless { it == "(none)" || it == "0000" }
        val ponder = if (t.getOrNull(2) == "ponder") t.getOrNull(3) else null
        return best to ponder
    }
}

/** Locale-independent fixed-point formatting ("1.25", "-0.40"), usable on every platform. */
fun formatDecimal(value: Double, decimals: Int): String {
    var factor = 1L
    repeat(decimals) { factor *= 10 }
    val scaled = kotlin.math.round(kotlin.math.abs(value) * factor).toLong()
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    val whole = scaled / factor
    if (decimals == 0) return "$sign$whole"
    return "$sign$whole." + (scaled % factor).toString().padStart(decimals, '0')
}
