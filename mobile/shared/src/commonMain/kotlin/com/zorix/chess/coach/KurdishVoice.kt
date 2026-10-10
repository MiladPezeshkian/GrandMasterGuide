package com.zorix.chess.coach

/**
 * Kurdish (Sorani) text for the native Sorani voice (Vekol by Revge), which reads letters.
 *
 * The voice was trained on Sorani spelling, so the text only has to be brought onto the letters it
 * knows: typed variants are folded, numbers are spelled out in Kurdish, Latin names are written in
 * Sorani letters, and every piece ends with a full stop, question or exclamation mark (which tells the
 * voice to finish). Long sentences are split at their commas: a very long input can make the voice
 * add a babbling tail.
 */
object KurdishVoice {
    private val ONES = listOf("سفر", "یەک", "دوو", "سێ", "چوار", "پێنج", "شەش", "حەوت", "هەشت", "نۆ")
    private val TEENS = listOf("دە", "یازدە", "دوازدە", "سێزدە", "چواردە", "پازدە", "شازدە", "حەڤدە", "هەژدە", "نۆزدە")
    private val TENS = listOf("", "دە", "بیست", "سی", "چل", "پەنجا", "شەست", "حەفتا", "هەشتا", "نەوەد")

    /** Kurdish number words, e.g. 686 -> "شەش سەد و هەشتا و شەش". */
    fun numberWords(n: Int): String = when {
        n < 10 -> ONES[n]
        n < 20 -> TEENS[n - 10]
        n < 100 -> TENS[n / 10] + if (n % 10 == 0) "" else " و " + ONES[n % 10]
        n < 1000 -> (if (n / 100 == 1) "سەد" else ONES[n / 100] + " سەد") + if (n % 100 == 0) "" else " و " + numberWords(n % 100)
        else -> n.toString().map { ONES[it - '0'] }.joinToString(" ")
    }

    /** Typed variants folded onto the letters the voice was trained on. */
    private val FOLD = mapOf(
        'ك' to "ک", 'ھ' to "ه", 'ہ' to "ه", 'ۀ' to "ە", 'ة' to "ە", 'ى' to "ی", 'ي' to "ی", 'ﻯ' to "ی", 'ﺉ' to "ئ", 'ٸ' to "ئ",
        'ؤ' to "و", 'أ' to "ا", 'إ' to "ا", 'آ' to "ا", 'ٱ' to "ا", 'ڭ' to "گ", '‌' to "", '‍' to "", 'ـ' to "",
        '“' to " ", '”' to " ", '"' to " ", '«' to " ", '»' to " ", '(' to "، ", ')' to "، ", '—' to "، ", '–' to "، ",
        '-' to " ", '…' to ".", '?' to "؟", ',' to "،", ';' to "؛", '·' to "، ", '/' to " ", ':' to "،",
        // The voice reads ئ as its Unicode decomposition: ي followed by the hamza above.
        'ئ' to "ئ",
    )

    private val PUNCT = setOf('!', '.', '،', '؛', '؟')
    private val SENTENCE_END = setOf('.', '؟', '!')

    /** Latin letters in Sorani spelling (names typed in Latin letters, "Zorix"). */
    private val LATIN = mapOf(
        'a' to "ا", 'b' to "ب", 'c' to "ک", 'd' to "د", 'e' to "ێ", 'f' to "ف", 'g' to "گ", 'h' to "ه", 'i' to "ی",
        'j' to "ج", 'k' to "ک", 'l' to "ل", 'm' to "م", 'n' to "ن", 'o' to "ۆ", 'p' to "پ", 'q' to "ق", 'r' to "ر",
        's' to "س", 't' to "ت", 'u' to "و", 'v' to "ڤ", 'w' to "و", 'x' to "کس", 'y' to "ی", 'z' to "ز",
    )
    private val LATIN_PAIRS = mapOf("sh" to "ش", "ch" to "چ", "kh" to "خ", "gh" to "غ", "zh" to "ژ", "ee" to "ی", "oo" to "وو", "ou" to "و")
    private val LATIN_START = mapOf('a' to "ئا", 'e' to "ئێ", 'i' to "ئی", 'o' to "ئۆ", 'u' to "ئو")

    /** A Latin word in Sorani letters, e.g. "Milad" -> "میلاد", "Zorix" -> "زۆریکس". */
    fun latinToSorani(word: String): String {
        val w = word.lowercase()
        return buildString {
            var i = 0
            while (i < w.length) {
                val pair = if (i + 1 < w.length) LATIN_PAIRS[w.substring(i, i + 2)] else null
                when {
                    pair != null -> { append(pair); i += 2; continue }
                    i == 0 && w[i] in LATIN_START -> append(LATIN_START.getValue(w[i]))
                    else -> append(LATIN[w[i]] ?: "")
                }
                i++
            }
        }
    }

    /**
     * The text the voice reads for [text] (a sentence or a piece of one): only letters and marks in
     * [known] (the voice's symbols), single spaces, and a closing full stop, question or exclamation
     * mark. Empty when nothing is left to say.
     */
    fun speechText(text: String, known: Set<Char>): String {
        val latin = Regex("[A-Za-z]+").replace(text) { " " + latinToSorani(it.value) + " " }
        val folded = buildString { for (c in latin) append(FOLD[c] ?: c.toString()) }
        val digits = buildString {
            for (c in folded) append(
                when (c) {
                    in '٠'..'٩' -> '0' + (c - '٠')
                    in '۰'..'۹' -> '0' + (c - '۰')
                    else -> c
                },
            )
        }
        fun spell(digitsOnly: String) = digitsOnly.toIntOrNull()?.let(::numberWords) ?: digitsOnly.map { ONES[it - '0'] }.joinToString(" ")
        // A suffix stays on its number ("٥ە" -> "پێنجە").
        val spelled = Regex("""\d+""").replace(Regex("""\d+(?=\p{L})""").replace(digits) { " " + spell(it.value) }) { " " + spell(it.value) + " " }
        val words = ArrayList<String>()
        for (chunk in spelled.split(' ', '\n', '\t')) {
            val w = chunk.filter { it in known && it != ' ' }
            if (w.isEmpty()) continue
            if (w.all { it in PUNCT }) {
                // Punctuation on its own belongs to the word before it.
                if (words.isNotEmpty() && words.last().last() !in PUNCT) words[words.lastIndex] = words.last() + w.first()
            } else {
                words += w
            }
        }
        if (words.isEmpty()) return ""
        val joined = words.joinToString(" ").trimEnd { it in PUNCT && it !in SENTENCE_END }
        return if (joined.last() in SENTENCE_END) joined else "$joined."
    }

    /**
     * A sentence split at its commas into pieces of at most [max] characters, each closed with a full
     * stop (the last keeps a question or exclamation mark). The first piece may be shorter
     * ([firstMax]) so speech starts sooner.
     */
    fun chunks(sentence: String, max: Int = 70, firstMax: Int = 45): List<String> {
        val text = sentence.trim()
        if (text.isEmpty()) return emptyList()
        if (text.length <= firstMax) return listOf(text)
        val pieces = ArrayList<String>()
        var buf = StringBuilder()
        for (part in Regex("""(?<=[،؛,;:])""").split(text)) {
            val limit = if (pieces.isEmpty()) firstMax else max
            if (buf.isNotEmpty() && buf.length + part.length > limit) {
                pieces += buf.toString()
                buf = StringBuilder()
            }
            buf.append(part)
        }
        pieces += buf.toString()
        val bare = pieces.map { it.trim().trimEnd('،', '؛', ',', ';', ':', '.', '؟', '?', '!').trim() }.filter { it.isNotEmpty() }
        if (bare.isEmpty()) return emptyList()
        val end = text.last().takeIf { it == '؟' || it == '?' || it == '!' } ?: '.'
        return bare.mapIndexed { i, c -> c + if (i == bare.lastIndex) end else '.' }
    }
}
