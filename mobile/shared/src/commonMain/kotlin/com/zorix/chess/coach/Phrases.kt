package com.zorix.chess.coach

import com.zorix.chess.core.PieceType
import com.zorix.chess.core.Side
import com.zorix.chess.core.Squares

/** Text for reading on screen, or for the voice (no symbols, squares spelled out). */
enum class TextMode { DISPLAY, SPEECH }

/**
 * Language-specific sentences for the coach. One instance per language, mode and listener
 * (pieces of [listener] are "yours", the others belong to the opponent).
 */
abstract class Phrases(val mode: TextMode, val listener: Side) {
    abstract val lang: String

    abstract fun pieceName(type: PieceType): String
    protected abstract fun yourPiece(type: PieceType, square: String): String
    protected abstract fun theirPiece(type: PieceType, square: String): String
    protected abstract fun spokenSquare(square: Int): String
    protected abstract fun spokenMove(m: SanParts): String
    protected abstract fun number(n: Int): String

    fun piece(p: PieceAt): String = if (p.side == listener) yourPiece(p.type, sq(p.square)) else theirPiece(p.type, sq(p.square))

    fun sq(square: Int): String = if (mode == TextMode.SPEECH) spokenSquare(square) else ltr(Squares.name(square))

    fun move(san: String): String = if (mode == TextMode.SPEECH) spokenMove(SanParts.parse(san)) else ltr(san)

    fun line(sans: List<String>): String =
        if (mode == TextMode.SPEECH) sans.joinToString(", ") { spokenMove(SanParts.parse(it)) } else ltr(sans.joinToString(" "))

    fun num(n: Int): String = number(n)

    /** Keeps Latin notation intact inside right-to-left text. */
    protected open fun ltr(text: String): String = text

    abstract fun fact(f: Fact): String?
    abstract fun amount(won: PieceAt?, amount: MaterialAmount): String

    // --------------------------------------------------------------- building blocks of coach messages
    abstract fun defaultName(): String
    abstract fun headline(quality: CoachQuality, name: String, variant: Int): String
    abstract fun better(bestSan: String, reason: String?): String
    abstract fun expect(replySan: String): String
    abstract fun tip(quality: CoachQuality, variant: Int): String?
    abstract fun opponentPlayed(opponent: String, san: String): String
    abstract fun watchOut(name: String): String
    abstract fun bestMoveIs(san: String): String
    abstract fun expectedLine(line: String): String
    abstract fun bookMove(opening: String): String
    abstract fun sacrificeWorks(): String
    abstract fun onlyMove(): String
    abstract fun equalPosition(): String
    abstract fun winning(side: Side): String
    abstract fun join(sentences: List<String>): String

    companion object {
        fun of(lang: String, mode: TextMode, listener: Side): Phrases = when (lang) {
            "fa" -> PersianPhrases(mode, listener)
            "ckb" -> KurdishPhrases(mode, listener)
            else -> EnglishPhrases(mode, listener)
        }

        const val LRI = '⁦'
        const val PDI = '⁩'
    }
}

/** Coach verdicts, chess.com style. */
enum class CoachQuality { BRILLIANT, GREAT, BEST, EXCELLENT, GOOD, BOOK, INACCURACY, MISTAKE, MISS, BLUNDER }

/** A SAN move split into parts, for speaking it. */
data class SanParts(
    val piece: PieceType,
    val capture: Boolean,
    val to: Int?,
    val promotion: PieceType?,
    val castle: Int, // 0 none, 1 short, 2 long
    val check: Boolean,
    val mate: Boolean,
) {
    companion object {
        fun parse(san: String): SanParts {
            val check = san.endsWith("+")
            val mate = san.endsWith("#")
            val body = san.trimEnd('+', '#', '!', '?')
            if (body == "O-O" || body == "0-0") return SanParts(PieceType.KING, false, null, null, 1, check, mate)
            if (body == "O-O-O" || body == "0-0-0") return SanParts(PieceType.KING, false, null, null, 2, check, mate)
            val piece = if (body.isNotEmpty() && body[0].isUpperCase()) PieceType.fromLetter(body[0]) ?: PieceType.PAWN else PieceType.PAWN
            val promo = body.substringAfter('=', "").firstOrNull()?.let { PieceType.fromLetter(it) }
            val main = body.substringBefore('=')
            val dest = Regex("[a-h][1-8]").findAll(main).lastOrNull()?.value
            return SanParts(piece, 'x' in main, dest?.let { Squares.parse(it) }, promo, 0, check, mate)
        }
    }
}

// =====================================================================================================
// English
// =====================================================================================================

class EnglishPhrases(mode: TextMode, listener: Side) : Phrases(mode, listener) {
    override val lang = "en"

    override fun pieceName(type: PieceType) = when (type) {
        PieceType.PAWN -> "pawn"
        PieceType.KNIGHT -> "knight"
        PieceType.BISHOP -> "bishop"
        PieceType.ROOK -> "rook"
        PieceType.QUEEN -> "queen"
        PieceType.KING -> "king"
    }

    override fun yourPiece(type: PieceType, square: String) =
        if (type == PieceType.KING) "your king" else "your ${pieceName(type)} on $square"

    override fun theirPiece(type: PieceType, square: String) =
        if (type == PieceType.KING) "the king" else "the ${pieceName(type)} on $square"

    override fun spokenSquare(square: Int) = Squares.name(square)
    override fun number(n: Int) = n.toString()

    override fun spokenMove(m: SanParts): String = buildString {
        when (m.castle) {
            1 -> append("castles kingside")
            2 -> append("castles queenside")
            else -> {
                append(pieceName(m.piece))
                append(if (m.capture) " takes " else " to ")
                append(m.to?.let { Squares.name(it) } ?: "")
                m.promotion?.let { append(", promotes to a ").append(pieceName(it)) }
            }
        }
        if (m.mate) append(", checkmate") else if (m.check) append(", check")
    }

    override fun amount(won: PieceAt?, amount: MaterialAmount): String = when {
        won != null && amount != MaterialAmount.DECISIVE && amount != MaterialAmount.EXCHANGE -> piece(won)
        else -> when (amount) {
            MaterialAmount.PAWN -> "a pawn"
            MaterialAmount.TWO_PAWNS -> "two pawns"
            MaterialAmount.MINOR -> "a piece"
            MaterialAmount.EXCHANGE -> "the exchange (a rook for a minor piece)"
            MaterialAmount.ROOK -> "a rook's worth of material"
            MaterialAmount.QUEEN -> "the queen's worth of material"
            MaterialAmount.DECISIVE -> "a decisive amount of material"
        }
    }

    override fun fact(f: Fact): String? = when (f) {
        Fact.Checkmate -> "It's checkmate!"
        is Fact.Check -> when {
            f.double -> "Double check! The king has to move."
            f.discovered -> "It gives a discovered check."
            else -> "It gives check."
        }
        is Fact.Captures -> when {
            f.net >= com.zorix.chess.core.Attacks.value(f.piece.type) -> "It captures ${piece(f.piece)}, which was not defended."
            f.net > 0 -> "It captures ${piece(f.piece)} and comes out ahead in the exchange."
            else -> "It captures ${piece(f.piece)}, but loses material in the exchange."
        }
        is Fact.Trade -> "It trades pieces: ${piece(f.piece)} is exchanged."
        is Fact.Promotes -> "The pawn promotes to a ${pieceName(f.type)}."
        is Fact.Castles -> "It castles ${if (f.kingside) "kingside" else "queenside"}: the king gets safe and the rooks are connected."
        is Fact.Fork -> "A fork! ${cap(piece(f.attacker))} attacks ${f.targets.take(2).joinToString(" and ") { piece(it) }} at the same time."
        is Fact.Pin -> if (f.behind.type == PieceType.KING) {
            "It pins ${piece(f.pinned)}: it cannot move, because the king is behind it."
        } else {
            "It pins ${piece(f.pinned)}: if it moves, ${piece(f.behind)} behind it is lost."
        }
        is Fact.Skewer -> "A skewer! ${cap(piece(f.front))} is attacked, and when it moves ${piece(f.back)} behind it falls."
        is Fact.DiscoveredAttack -> "A discovered attack: moving this piece opens the line of ${piece(f.attacker)}, which now attacks ${piece(f.target)}."
        is Fact.AttacksUndefended -> "It attacks ${piece(f.target)}, which is not defended enough."
        is Fact.ThreatensMate -> "It threatens mate with ${move(f.mateSan)}."
        is Fact.SavesPiece -> if (f.moved) "It moves the attacked ${pieceName(f.piece.type)} to safety." else "It defends ${piece(f.piece)}, which was under attack."
        is Fact.Develops -> "It develops the ${pieceName(f.piece.type)} and brings it into play."
        Fact.ControlsCenter -> "It fights for the center of the board."
        is Fact.PushesPassedPawn -> "It pushes the passed pawn closer to promotion."
        is Fact.RookOnOpenFile -> "The rook takes the open ${'a' + f.file}-file."
        is Fact.Sacrifice -> "It sacrifices the ${pieceName(f.piece.type)}!"
        is Fact.Hangs -> "It leaves ${piece(f.piece)} undefended."
        Fact.KingWalksEarly -> "Moving the king this early gives up the right to castle."
        Fact.QueenOutEarly -> "Bringing the queen out this early lets the opponent gain time by attacking it."
        is Fact.AllowsMate -> "It allows mate in ${num(f.moves)}, starting with ${move(f.replySan)}."
        is Fact.LosesMaterial -> if (f.how is Fact.Captures && f.how.piece == f.lost) {
            "It leaves ${amount(f.lost, f.amount)} unprotected: after ${move(f.replySan)} it is lost."
        } else buildString {
            append("After ${move(f.replySan)}")
            f.how?.let { h -> fact(h)?.let { append(" (").append(it.trimEnd('.', '!').replaceFirstChar { c -> c.lowercaseChar() }).append(")") } }
            append(" you lose ").append(amount(f.lost, f.amount)).append('.')
        }
        is Fact.WinsMaterial -> "It wins ${amount(f.won, f.amount)}."
        is Fact.ForcedMate -> if (f.moves <= 1) "It mates immediately." else "It leads to a forced mate in ${num(f.moves)}."
        is Fact.ThreatCapture -> "${cap(piece(f.piece))} is under attack${f.bySan?.let { " (${move(it)})" } ?: ""}."
        is Fact.ThreatMate -> "The opponent threatens mate with ${move(f.mateSan)}!"
    }

    private fun cap(s: String) = s.replaceFirstChar { it.uppercaseChar() }

    override fun defaultName() = "my friend"

    override fun headline(quality: CoachQuality, name: String, variant: Int): String {
        val options = when (quality) {
            CoachQuality.BRILLIANT -> listOf("Brilliant, $name!", "What a move, $name! Brilliant!", "Superb, $name — a brilliant idea!")
            CoachQuality.GREAT -> listOf("Great move, $name! It was the only good one.", "Well found, $name — the only strong move!", "Great, $name! You found the key move.")
            CoachQuality.BEST -> listOf("Excellent, $name! That's the best move.", "Perfect, $name — exactly what a master would play.", "Well done, $name! The best move.")
            CoachQuality.EXCELLENT -> listOf("Very good, $name.", "Nice, $name — an excellent move.", "Strong play, $name.")
            CoachQuality.GOOD -> listOf("Good move, $name.", "That works, $name.", "A solid move, $name.")
            CoachQuality.BOOK -> listOf("Opening theory, $name.", "A book move, $name.", "Good, $name — that's opening theory.")
            CoachQuality.INACCURACY -> listOf("$name, that's a little inaccurate.", "Not the most precise move, $name.", "$name, there was something better.")
            CoachQuality.MISTAKE -> listOf("$name, that's a mistake.", "Hmm, $name, that move has a problem.", "$name, careful — that's a mistake.")
            CoachQuality.MISS -> listOf("$name, you missed a chance!", "There was a winning shot, $name!", "$name, look again — you missed something strong.")
            CoachQuality.BLUNDER -> listOf("Careful, $name! That's a blunder.", "Oh no, $name — a blunder.", "$name, that move loses a lot.")
        }
        return options[variant.mod(options.size)]
    }

    override fun better(bestSan: String, reason: String?) =
        "Better was ${move(bestSan)}" + (reason?.let { ": " + it.replaceFirstChar { c -> c.lowercaseChar() } } ?: ".")

    override fun expect(replySan: String) = "Zorix expects ${move(replySan)} next."

    override fun tip(quality: CoachQuality, variant: Int): String? {
        val options = when (quality) {
            CoachQuality.BLUNDER, CoachQuality.MISTAKE -> listOf(
                "Before every move, ask: what does my opponent threaten, and which of my pieces are undefended?",
                "Tip: check every capture and every check for both sides before you move.",
                "Take your time: look at your opponent's last move and ask why it was played.",
            )
            CoachQuality.MISS -> listOf(
                "Tip: always look for checks, captures and threats first — in that order.",
                "When your opponent leaves a piece undefended, look for a way to win it.",
            )
            CoachQuality.INACCURACY -> listOf(
                "Try to improve your least active piece.",
                "Think about where each of your pieces would be best placed.",
            )
            else -> return null
        }
        return options[variant.mod(options.size)]
    }

    override fun opponentPlayed(opponent: String, san: String) = "$opponent played ${move(san)}."
    override fun watchOut(name: String) = "Watch out, $name:"
    override fun bestMoveIs(san: String) = "The best move is ${move(san)}."
    override fun expectedLine(line: String) = "Expected continuation: $line."
    override fun bookMove(opening: String) = "This is the $opening."
    override fun sacrificeWorks() = "The sacrifice works — the attack is worth more than the material."
    override fun onlyMove() = "It was the only move that keeps the advantage."
    override fun equalPosition() = "The position is about equal."
    override fun winning(side: Side) = if (side == listener) "You are winning." else "Your opponent is winning."
    override fun join(sentences: List<String>) = sentences.filter { it.isNotBlank() }.joinToString(" ")
}

// =====================================================================================================
// Persian (فارسی)
// =====================================================================================================

class PersianPhrases(mode: TextMode, listener: Side) : Phrases(mode, listener) {
    override val lang = "fa"

    override fun pieceName(type: PieceType) = when (type) {
        PieceType.PAWN -> "سرباز"
        PieceType.KNIGHT -> "اسب"
        PieceType.BISHOP -> "فیل"
        PieceType.ROOK -> "رخ"
        PieceType.QUEEN -> "وزیر"
        PieceType.KING -> "شاه"
    }

    override fun ltr(text: String) = "$LRI$text$PDI"

    override fun yourPiece(type: PieceType, square: String) =
        if (type == PieceType.KING) "شاه شما" else "${pieceName(type)} شما در $square"

    override fun theirPiece(type: PieceType, square: String) =
        if (type == PieceType.KING) "شاه حریف" else "${pieceName(type)} حریف در $square"

    private val files = listOf("آ", "بی", "سی", "دی", "ای", "اِف", "جی", "اِچ")
    private val words = listOf("صفر", "یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نه", "ده")

    override fun spokenSquare(square: Int) = files[Squares.file(square)] + " " + words[Squares.rank(square) + 1]

    override fun number(n: Int) = if (mode == TextMode.SPEECH) words.getOrElse(n) { n.toString() } else persianDigits(n)

    /** A move as a noun phrase that fits any sentence: "اسب به اِف سه", "زدن با فیل در بی پنج". */
    override fun spokenMove(m: SanParts): String = buildString {
        when (m.castle) {
            1 -> append("قلعه‌ی کوتاه")
            2 -> append("قلعه‌ی بلند")
            else -> {
                val target = m.to?.let { spokenSquare(it) } ?: ""
                if (m.capture) append("زدن با ").append(pieceName(m.piece)).append(" در ").append(target)
                else append(pieceName(m.piece)).append(" به ").append(target)
                m.promotion?.let { append(" و ارتقا به ").append(pieceName(it)) }
            }
        }
        if (m.mate) append(" با کیش و مات") else if (m.check) append(" با کیش")
    }

    override fun amount(won: PieceAt?, amount: MaterialAmount): String = when {
        won != null && amount != MaterialAmount.DECISIVE && amount != MaterialAmount.EXCHANGE -> piece(won)
        else -> when (amount) {
            MaterialAmount.PAWN -> "یک سرباز"
            MaterialAmount.TWO_PAWNS -> "دو سرباز"
            MaterialAmount.MINOR -> "یک مهره‌ی سبک"
            MaterialAmount.EXCHANGE -> "کیفیت (رخ در برابر مهره‌ی سبک)"
            MaterialAmount.ROOK -> "به‌اندازه‌ی یک رخ مهره"
            MaterialAmount.QUEEN -> "به‌اندازه‌ی یک وزیر مهره"
            MaterialAmount.DECISIVE -> "مهره‌ی زیادی"
        }
    }

    override fun fact(f: Fact): String? = when (f) {
        Fact.Checkmate -> "کیش و مات!"
        is Fact.Check -> when {
            f.double -> "این حرکت کیش دوبل می‌دهد و شاه مجبور است جابه‌جا شود."
            f.discovered -> "این حرکت کیش کشف‌شده می‌دهد."
            else -> "این حرکت کیش می‌دهد."
        }
        is Fact.Captures -> when {
            f.net >= com.zorix.chess.core.Attacks.value(f.piece.type) -> "این حرکت ${piece(f.piece)} را می‌زند که بی‌دفاع بود."
            f.net > 0 -> "این حرکت ${piece(f.piece)} را می‌زند و در معاوضه سود می‌کند."
            else -> "این حرکت ${piece(f.piece)} را می‌زند، اما در معاوضه ضرر می‌کند."
        }
        is Fact.Trade -> "با این حرکت مهره‌ها معاوضه می‌شوند."
        is Fact.Promotes -> "سرباز به ${pieceName(f.type)} ارتقا پیدا می‌کند."
        is Fact.Castles -> "با قلعه‌ی ${if (f.kingside) "کوتاه" else "بلند"}، شاه در امان است و رخ‌ها به هم وصل می‌شوند."
        is Fact.Fork -> "چنگال! ${piece(f.attacker)} هم‌زمان به ${f.targets.take(2).joinToString(" و ") { piece(it) }} حمله می‌کند."
        is Fact.Pin -> if (f.behind.type == PieceType.KING) {
            "این حرکت ${piece(f.pinned)} را آچمز می‌کند؛ این مهره نمی‌تواند حرکت کند، چون شاه پشت آن است."
        } else {
            "این حرکت ${piece(f.pinned)} را آچمز می‌کند؛ اگر این مهره حرکت کند، ${piece(f.behind)} از دست می‌رود."
        }
        is Fact.Skewer -> "سیخ! ${piece(f.front)} زیر حمله است و وقتی کنار برود، ${piece(f.back)} از دست می‌رود."
        is Fact.DiscoveredAttack -> "حمله‌ی کشف‌شده! با کنار رفتن این مهره، راه ${piece(f.attacker)} باز می‌شود و به ${piece(f.target)} حمله می‌کند."
        is Fact.AttacksUndefended -> "این حرکت به ${piece(f.target)} حمله می‌کند که به‌اندازه‌ی کافی دفاع نشده است."
        is Fact.ThreatensMate -> "این حرکت تهدید می‌کند که با ${move(f.mateSan)} مات کند."
        is Fact.SavesPiece -> if (f.moved) "این حرکت ${pieceName(f.piece.type)} را از زیر حمله نجات می‌دهد." else "این حرکت از ${piece(f.piece)} دفاع می‌کند که زیر حمله بود."
        is Fact.Develops -> "این حرکت ${pieceName(f.piece.type)} را وارد بازی می‌کند."
        Fact.ControlsCenter -> "این حرکت مرکز صفحه را کنترل می‌کند."
        is Fact.PushesPassedPawn -> "این حرکت سرباز رونده را به ارتقا نزدیک‌تر می‌کند."
        is Fact.RookOnOpenFile -> "رخ ستون باز ${ltr(('a' + f.file).toString())} را در اختیار می‌گیرد."
        is Fact.Sacrifice -> "این حرکت ${pieceName(f.piece.type)} را قربانی می‌کند!"
        is Fact.Hangs -> "این حرکت ${piece(f.piece)} را بی‌دفاع می‌گذارد."
        Fact.KingWalksEarly -> "حرکت دادن شاه در این مرحله، حق قلعه رفتن را از بین می‌برد."
        Fact.QueenOutEarly -> "بیرون آوردن زودهنگام وزیر به حریف اجازه می‌دهد با حمله به آن وقت بخرد."
        is Fact.AllowsMate -> "این حرکت به حریف اجازه می‌دهد در ${num(f.moves)} حرکت مات کند؛ شروعش با ${move(f.replySan)} است."
        is Fact.LosesMaterial -> if (f.how is Fact.Captures && f.how.piece == f.lost) {
            "${amount(f.lost, f.amount)} بی‌دفاع می‌ماند و بعد از ${move(f.replySan)} از دست می‌رود."
        } else {
            "بعد از ${move(f.replySan)}، ${amount(f.lost, f.amount)} از دست می‌رود."
        }
        is Fact.WinsMaterial -> "این حرکت ${amount(f.won, f.amount)} را می‌برد."
        is Fact.ForcedMate -> if (f.moves <= 1) "این حرکت فوراً مات می‌کند." else "این حرکت به مات اجباری در ${num(f.moves)} حرکت می‌رسد."
        is Fact.ThreatCapture -> "${piece(f.piece)} زیر حمله است${f.bySan?.let { "؛ حریف تهدید به ${move(it)} دارد" } ?: ""}."
        is Fact.ThreatMate -> "حریف تهدید می‌کند که با ${move(f.mateSan)} مات کند!"
    }

    override fun defaultName() = "دوست من"

    override fun headline(quality: CoachQuality, name: String, variant: Int): String {
        val options = when (quality) {
            CoachQuality.BRILLIANT -> listOf("درخشان بود $name عزیزم!", "فوق‌العاده $name! یک حرکت درخشان!", "آفرین $name! ایده‌ی درخشانی بود.")
            CoachQuality.GREAT -> listOf("عالی $name! تنها حرکت خوب را پیدا کردی.", "آفرین $name عزیزم، حرکت کلیدی را پیدا کردی!", "خیلی خوب $name! این تنها حرکت قوی بود.")
            CoachQuality.BEST -> listOf("آفرین $name عزیزم! بهترین حرکت همین بود.", "عالی $name! دقیقاً همان حرکتی که یک استاد انجام می‌دهد.", "درست زدی $name جان، بهترین حرکت!")
            CoachQuality.EXCELLENT -> listOf("خیلی خوب $name عزیزم.", "حرکت عالی‌ای بود $name.", "قوی بازی کردی $name جان.")
            CoachQuality.GOOD -> listOf("حرکت خوبی بود $name.", "خوب است $name جان.", "حرکت محکمی بود $name عزیزم.")
            CoachQuality.BOOK -> listOf("$name عزیزم، این حرکت تئوری گشایش است.", "حرکت کتابی، $name.", "خوب است $name، طبق تئوری گشایش.")
            CoachQuality.INACCURACY -> listOf("$name عزیزم، این حرکت کمی نادقیق بود.", "$name جان، حرکت دقیق‌تری هم وجود داشت.", "بد نیست $name، ولی بهترش هم بود.")
            CoachQuality.MISTAKE -> listOf("$name عزیزم، این حرکت اشتباه بود.", "دقت کن $name جان، این حرکت مشکل دارد.", "$name عزیزم، این یک اشتباه است.")
            CoachQuality.MISS -> listOf("$name عزیزم، یک فرصت را از دست دادی!", "$name جان، یک ضربه‌ی برنده وجود داشت!", "دوباره نگاه کن $name، حرکت خیلی قوی‌تری بود.")
            CoachQuality.BLUNDER -> listOf("مراقب باش $name عزیزم! این اشتباه بزرگی بود.", "اوه $name جان، اشتباه فاحش!", "$name عزیزم، این حرکت خیلی گران تمام می‌شود.")
        }
        return options[variant.mod(options.size)]
    }

    override fun better(bestSan: String, reason: String?) =
        "حرکت بهتر ${move(bestSan)} بود." + (reason?.let { " $it" } ?: "")

    override fun expect(replySan: String) = "پیش‌بینی من این است که حرکت بعدی حریف ${move(replySan)} باشد."

    override fun tip(quality: CoachQuality, variant: Int): String? {
        val options = when (quality) {
            CoachQuality.BLUNDER, CoachQuality.MISTAKE -> listOf(
                "قبل از هر حرکت از خودت بپرس: حریف چه تهدیدی دارد و کدام مهره‌ی من بی‌دفاع است؟",
                "نکته: قبل از حرکت، همه‌ی کیش‌ها و زدن‌های هر دو طرف را بررسی کن.",
                "عجله نکن: به آخرین حرکت حریف نگاه کن و بپرس چرا آن را بازی کرد.",
            )
            CoachQuality.MISS -> listOf(
                "نکته: همیشه اول کیش‌ها، بعد زدن‌ها و بعد تهدیدها را بررسی کن.",
                "وقتی حریف مهره‌ای را بی‌دفاع می‌گذارد، دنبال راهی برای بردنش باش.",
            )
            CoachQuality.INACCURACY -> listOf(
                "سعی کن غیرفعال‌ترین مهره‌ات را بهتر کنی.",
                "فکر کن هر مهره‌ات در کدام خانه بهترین جا را دارد.",
            )
            else -> return null
        }
        return options[variant.mod(options.size)]
    }

    override fun opponentPlayed(opponent: String, san: String) = "$opponent بازی کرد: ${move(san)}."
    override fun watchOut(name: String) = "مراقب باش $name عزیزم:"
    override fun bestMoveIs(san: String) = "بهترین حرکت ${move(san)} است."
    override fun expectedLine(line: String) = "ادامه‌ی پیش‌بینی‌شده: $line."
    override fun bookMove(opening: String) = "این گشایش «$opening» است."
    override fun sacrificeWorks() = "این قربانی جواب می‌دهد؛ حمله از مهره‌ی ازدست‌رفته ارزشمندتر است."
    override fun onlyMove() = "این تنها حرکتی بود که برتری را حفظ می‌کرد."
    override fun equalPosition() = "وضعیت تقریباً مساوی است."
    override fun winning(side: Side) = if (side == listener) "شما در وضعیت برنده هستید." else "حریف در وضعیت برنده است."
    override fun join(sentences: List<String>) = sentences.filter { it.isNotBlank() }.joinToString(" ")

    companion object {
        fun persianDigits(n: Int): String = n.toString().map { if (it.isDigit()) '۰' + (it - '0') else it }.joinToString("")
    }
}

// =====================================================================================================
// Kurdish Sorani (کوردی)
// =====================================================================================================

class KurdishPhrases(mode: TextMode, listener: Side) : Phrases(mode, listener) {
    override val lang = "ckb"

    override fun pieceName(type: PieceType) = when (type) {
        PieceType.PAWN -> "سەرباز"
        PieceType.KNIGHT -> "ئەسپ"
        PieceType.BISHOP -> "فیل"
        PieceType.ROOK -> "قەڵا"
        PieceType.QUEEN -> "وەزیر"
        PieceType.KING -> "شا"
    }

    /** "your knight": ئەسپەکەت, قەڵاکەت (after a vowel). */
    private fun yours(type: PieceType): String {
        val n = pieceName(type)
        return if (n.endsWith("ا")) n + "کەت" else n + "ەکەت"
    }

    override fun ltr(text: String) = "$LRI$text$PDI"

    override fun yourPiece(type: PieceType, square: String) =
        if (type == PieceType.KING) "شاکەت" else "${yours(type)} لە $square"

    override fun theirPiece(type: PieceType, square: String) =
        if (type == PieceType.KING) "شای ڕکابەر" else "${pieceName(type)}ی ڕکابەر لە $square"

    private val files = listOf("ئەی", "بی", "سی", "دی", "ئی", "ئێف", "جی", "ئێچ")
    private val words = listOf("سفر", "یەک", "دوو", "سێ", "چوار", "پێنج", "شەش", "حەوت", "هەشت", "نۆ", "دە")

    override fun spokenSquare(square: Int) = files[Squares.file(square)] + " " + words[Squares.rank(square) + 1]

    override fun number(n: Int) = if (mode == TextMode.SPEECH) words.getOrElse(n) { n.toString() } else arabicDigits(n)

    override fun spokenMove(m: SanParts): String = buildString {
        when (m.castle) {
            1 -> append("قەڵابەندی کورت")
            2 -> append("قەڵابەندی درێژ")
            else -> {
                append(pieceName(m.piece)).append(' ')
                val target = m.to?.let { spokenSquare(it) } ?: ""
                if (m.capture) append("گرتنی ")
                append(target)
                m.promotion?.let { append(" و دەبێتە ").append(pieceName(it)) }
            }
        }
        if (m.mate) append("، کش‌مات") else if (m.check) append("، کش")
    }

    override fun amount(won: PieceAt?, amount: MaterialAmount): String = when {
        won != null && amount != MaterialAmount.DECISIVE && amount != MaterialAmount.EXCHANGE -> piece(won)
        else -> when (amount) {
            MaterialAmount.PAWN -> "سەربازێک"
            MaterialAmount.TWO_PAWNS -> "دوو سەرباز"
            MaterialAmount.MINOR -> "مۆرەیەکی سووک"
            MaterialAmount.EXCHANGE -> "قەڵایەک بەرامبەر مۆرەیەکی سووک"
            MaterialAmount.ROOK -> "بە ئەندازەی قەڵایەک مۆرە"
            MaterialAmount.QUEEN -> "بە ئەندازەی وەزیرێک مۆرە"
            MaterialAmount.DECISIVE -> "مۆرەیەکی زۆر"
        }
    }

    override fun fact(f: Fact): String? = when (f) {
        Fact.Checkmate -> "کش‌مات!"
        is Fact.Check -> when {
            f.double -> "کشی دووانە! شا دەبێت بجوڵێت."
            f.discovered -> "کشێکی ئاشکراکراو دەدات."
            else -> "کش دەدات."
        }
        is Fact.Captures -> when {
            f.net >= com.zorix.chess.core.Attacks.value(f.piece.type) -> "${piece(f.piece)} دەگرێت کە پارێزراو نەبوو."
            f.net > 0 -> "${piece(f.piece)} دەگرێت و لە ئاڵوگۆڕەکەدا دەباتەوە."
            else -> "${piece(f.piece)} دەگرێت، بەڵام لە ئاڵوگۆڕەکەدا مۆرە لەدەست دەدات."
        }
        is Fact.Trade -> "مۆرەکان ئاڵوگۆڕ دەکرێن: ${piece(f.piece)} دەگیرێت."
        is Fact.Promotes -> "سەربازەکە دەبێتە ${pieceName(f.type)}."
        is Fact.Castles -> "قەڵابەندی ${if (f.kingside) "کورت" else "درێژ"} دەکات: شا پارێزراو دەبێت و قەڵاکان پێکەوە دەبەسترێن."
        is Fact.Fork -> "دووشاخە! ${piece(f.attacker)} لە هەمان کاتدا هێرش دەکاتە سەر ${f.targets.take(2).joinToString(" و ") { piece(it) }}."
        is Fact.Pin -> if (f.behind.type == PieceType.KING) {
            "${piece(f.pinned)} دەبەستێتەوە: ناتوانێت بجوڵێت، چونکە شا لە پشتییەوەیە."
        } else {
            "${piece(f.pinned)} دەبەستێتەوە: ئەگەر بجوڵێت، ${piece(f.behind)} لە پشتییەوە لەدەست دەچێت."
        }
        is Fact.Skewer -> "شیش! هێرش دەکرێتە سەر ${piece(f.front)} و کاتێک لادەچێت، ${piece(f.back)} لە پشتییەوە لەدەست دەچێت."
        is Fact.DiscoveredAttack -> "هێرشی ئاشکراکراو: بە لاچوونی ئەم مۆرەیە ڕێگای ${piece(f.attacker)} دەکرێتەوە و هێرش دەکاتە سەر ${piece(f.target)}."
        is Fact.AttacksUndefended -> "هێرش دەکاتە سەر ${piece(f.target)} کە بە باشی پارێزراو نییە."
        is Fact.ThreatensMate -> "هەڕەشەی مات دەکات بە ${move(f.mateSan)}."
        is Fact.SavesPiece -> if (f.moved) "${pieceName(f.piece.type)}ە هێرشکراوەکە دەباتە شوێنێکی ئارام." else "بەرگری لە ${piece(f.piece)} دەکات کە لە ژێر هێرشدا بوو."
        is Fact.Develops -> "${pieceName(f.piece.type)} دەهێنێتە ناو یارییەکە (گەشەپێدانی مۆرەکان)."
        Fact.ControlsCenter -> "بۆ کۆنترۆڵی ناوەڕاستی تەختەکە هەوڵ دەدات."
        is Fact.PushesPassedPawn -> "سەربازە ڕاکەرەکە نزیکتر دەکاتەوە لە بەرزبوونەوە."
        is Fact.RookOnOpenFile -> "قەڵاکە ستوونی کراوەی ${ltr(('a' + f.file).toString())} دەگرێت."
        is Fact.Sacrifice -> "${pieceName(f.piece.type)} دەکاتە قوربانی!"
        is Fact.Hangs -> "${piece(f.piece)} بێ پارێزەر بەجێ دەهێڵێت."
        Fact.KingWalksEarly -> "جوڵاندنی شا لەم قۆناغەدا مافی قەڵابەندی لەناو دەبات."
        Fact.QueenOutEarly -> "دەرهێنانی وەزیر زوو، ڕێگە بە ڕکابەر دەدات بە هێرشکردنە سەری کات ببات."
        is Fact.AllowsMate -> "ڕێگە دەدات ڕکابەر لە ${num(f.moves)} جوڵەدا مات بکات، بە ${move(f.replySan)}."
        is Fact.LosesMaterial -> if (f.how is Fact.Captures && f.how.piece == f.lost) {
            "${amount(f.lost, f.amount)} بێ پارێزەر دەمێنێت و دوای ${move(f.replySan)} لەدەست دەچێت."
        } else buildString {
            append("دوای ${move(f.replySan)}")
            f.how?.let { h -> fact(h)?.let { append(" (").append(it.trimEnd('.', '!')).append(")") } }
            append("، ").append(amount(f.lost, f.amount)).append(" لەدەست دەدەیت.")
        }
        is Fact.WinsMaterial -> "${amount(f.won, f.amount)} دەباتەوە."
        is Fact.ForcedMate -> if (f.moves <= 1) "یەکسەر مات دەکات." else "دەگاتە ماتێکی ناچاری لە ${num(f.moves)} جوڵەدا."
        is Fact.ThreatCapture -> "${piece(f.piece)} لە ژێر هێرشدایە${f.bySan?.let { " (${move(it)})" } ?: ""}."
        is Fact.ThreatMate -> "ڕکابەر هەڕەشەی مات دەکات بە ${move(f.mateSan)}!"
    }

    override fun defaultName() = "هاوڕێکەم"

    override fun headline(quality: CoachQuality, name: String, variant: Int): String {
        val options = when (quality) {
            CoachQuality.BRILLIANT -> listOf("نایاب بوو $name گیان!", "سەرسوڕهێنەرە $name! جوڵەیەکی درەوشاوە!", "دەستخۆش $name! بیرۆکەیەکی درەوشاوە بوو.")
            CoachQuality.GREAT -> listOf("زۆر باشە $name! تاکە جوڵەی باشت دۆزییەوە.", "دەستخۆش $name گیان، جوڵە سەرەکییەکەت دۆزییەوە!", "ئافەرین $name! ئەمە تاکە جوڵەی بەهێز بوو.")
            CoachQuality.BEST -> listOf("ئافەرین $name گیان! ئەمە باشترین جوڵە بوو.", "زۆر باشە $name! ڕێک ئەو جوڵەیەی ماستەرێک دەیکات.", "دەستخۆش $name، باشترین جوڵە!")
            CoachQuality.EXCELLENT -> listOf("زۆر باشە $name گیان.", "جوڵەیەکی نایاب بوو $name.", "بەهێز یاریت کرد $name.")
            CoachQuality.GOOD -> listOf("جوڵەیەکی باش بوو $name.", "باشە $name گیان.", "جوڵەیەکی پتەو بوو $name.")
            CoachQuality.BOOK -> listOf("$name گیان، ئەمە تیۆری دەستپێکە.", "جوڵەیەکی کتێبی، $name.", "باشە $name، بەپێی تیۆری دەستپێک.")
            CoachQuality.INACCURACY -> listOf("$name گیان، ئەم جوڵەیە کەمێک ناورد بوو.", "$name، جوڵەیەکی وردتر هەبوو.", "خراپ نییە $name، بەڵام باشتر هەبوو.")
            CoachQuality.MISTAKE -> listOf("$name گیان، ئەم جوڵەیە هەڵە بوو.", "ئاگادار بە $name، ئەم جوڵەیە کێشەی هەیە.", "$name گیان، ئەمە هەڵەیەکە.")
            CoachQuality.MISS -> listOf("$name گیان، دەرفەتێکت لەدەستدا!", "$name، لێدانێکی براوە هەبوو!", "دووبارە سەیر بکە $name، جوڵەیەکی زۆر بەهێزتر هەبوو.")
            CoachQuality.BLUNDER -> listOf("ئاگادار بە $name گیان! ئەمە هەڵەیەکی گەورە بوو.", "ئۆی $name، هەڵەیەکی گەورە!", "$name گیان، ئەم جوڵەیە زۆر گران دەکەوێت.")
        }
        return options[variant.mod(options.size)]
    }

    override fun better(bestSan: String, reason: String?) =
        "باشتر بوو ${move(bestSan)} یاری بکەیت" + (reason?.let { ": $it" } ?: ".")

    override fun expect(replySan: String) = "Zorix پێشبینی دەکات ڕکابەر ${move(replySan)} یاری بکات."

    override fun tip(quality: CoachQuality, variant: Int): String? {
        val options = when (quality) {
            CoachQuality.BLUNDER, CoachQuality.MISTAKE -> listOf(
                "پێش هەر جوڵەیەک لە خۆت بپرسە: ڕکابەر چ هەڕەشەیەکی هەیە و کام مۆرەم بێ پارێزەرە؟",
                "ئامۆژگاری: پێش جوڵە، هەموو کش و گرتنەکانی هەردوو لا بپشکنە.",
                "پەلە مەکە: سەیری دوایین جوڵەی ڕکابەر بکە و بپرسە بۆچی کردی.",
            )
            CoachQuality.MISS -> listOf(
                "ئامۆژگاری: هەمیشە سەرەتا کش، پاشان گرتن و دواتر هەڕەشەکان بپشکنە.",
                "کاتێک ڕکابەر مۆرەیەک بێ پارێزەر بەجێ دەهێڵێت، ڕێگەیەک بۆ بردنەوەی بدۆزەرەوە.",
            )
            CoachQuality.INACCURACY -> listOf(
                "هەوڵ بدە ناچالاکترین مۆرەکەت باشتر بکەیت.",
                "بیر بکەرەوە هەر مۆرەیەکت لە کام خانەدا باشترین شوێنی هەیە.",
            )
            else -> return null
        }
        return options[variant.mod(options.size)]
    }

    override fun opponentPlayed(opponent: String, san: String) = "$opponent جوڵەی ${move(san)} یاری کرد."
    override fun watchOut(name: String) = "ئاگادار بە $name گیان:"
    override fun bestMoveIs(san: String) = "باشترین جوڵە ${move(san)}ە."
    override fun expectedLine(line: String) = "بەردەوامی پێشبینیکراو: $line."
    override fun bookMove(opening: String) = "ئەمە دەستپێکی «$opening»ە."
    override fun sacrificeWorks() = "ئەم قوربانییە کار دەکات؛ هێرشەکە لە مۆرە لەدەستچووەکە بەنرخترە."
    override fun onlyMove() = "ئەمە تاکە جوڵەیەک بوو کە بەرتری دەپاراست."
    override fun equalPosition() = "دۆخەکە نزیکەی یەکسانە."
    override fun winning(side: Side) = if (side == listener) "تۆ لە دۆخی بردنەوەدایت." else "ڕکابەر لە دۆخی بردنەوەدایە."
    override fun join(sentences: List<String>) = sentences.filter { it.isNotBlank() }.joinToString(" ")

    companion object {
        fun arabicDigits(n: Int): String = n.toString().map { if (it.isDigit()) '٠' + (it - '0') else it }.joinToString("")
    }
}
