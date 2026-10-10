package com.zorix.chess.coach

import com.zorix.chess.core.Side

/**
 * Turns display text (lesson texts, explanations) into text the voice can read:
 * chess notation like "Nf3", "exd5+" or "e4" becomes spoken words, bidi marks are removed.
 */
object Speakable {
    private val SAN = Regex("""(?<![A-Za-z0-9])(O-O-O|O-O|[KQRBN][a-h]?[1-8]?x?[a-h][1-8](=[QRBN])?[+#]?|[a-h]x[a-h][1-8](=[QRBN])?[+#]?|[a-h][1-8](=[QRBN])?[+#]?)(?![A-Za-z0-9])""")
    private val MOVE_NUMBER = Regex("""\b\d+\.(\.\.)?\s*""")

    fun of(text: String, lang: String): String {
        val p = Phrases.of(lang, TextMode.SPEECH, Side.WHITE)
        var t = text.replace(Phrases.LRI.toString(), "").replace(Phrases.PDI.toString(), "")
        t = MOVE_NUMBER.replace(t, "")
        // A bare square ("e4") is read as a square; anything with a piece, capture or check as a move.
        t = SAN.replace(t) { m ->
            val v = m.value
            if (v.length == 2) p.sq(com.zorix.chess.core.Squares.parse(v)) else p.move(v)
        }
        return t.replace("—", ",").replace("(", ", ").replace(")", ", ").replace(Regex("\\s+"), " ").trim()
    }
}
