package com.zorix.chess.coach

/**
 * Kurdish (Sorani) for the neural voice.
 *
 * There is no offline Sorani voice that may be used in a paid app, so Kurdish is spoken by the Persian
 * Piper voice. Instead of letting the Persian pronunciation rules guess (they read Kurdish words as
 * Persian ones), Sorani spelling, which writes almost every vowel, is turned into phonemes here and
 * handed to the voice directly. The phonemes are the voice's own symbols (the IPA that espeak-ng
 * writes for Persian), so the voice says each Kurdish sound with the closest sound it knows.
 *
 * The speech engine reads phonemes through a lexicon: every word becomes an ASCII key whose entry
 * lists the word's phoneme tokens in Piper's layout (each token followed by the pad token "_").
 */
object KurdishVoice {
    /** One spoken word: its phonemes and the punctuation that follows it ("", ",", ".", "?", "!"). */
    data class Word(val ipa: String, val punct: String)

    const val BOS_KEY = "qbos"
    const val EOS_KEY = "qeos"

    private val CONSONANTS = mapOf(
        'ب' to "b", 'پ' to "p", 'ت' to "t", 'ج' to "dʒ", 'چ' to "tʃ", 'ح' to "h", 'خ' to "x", 'د' to "d",
        'ر' to "r", 'ڕ' to "r", 'ز' to "z", 'ژ' to "ʒ", 'س' to "s", 'ش' to "ʃ", 'ع' to "ʔ", 'غ' to "ɣ",
        'ف' to "f", 'ڤ' to "v", 'ق' to "q1", 'ک' to "k", 'ك' to "k", 'گ' to "ɡ", 'ل' to "l", 'ڵ' to "l",
        'م' to "m", 'ن' to "n", 'ه' to "h", 'ھ' to "h", 'ة' to "h", 'ء' to "ʔ", 'أ' to "ʔ", 'ط' to "t",
        'ظ' to "z", 'ض' to "z", 'ص' to "s", 'ث' to "s", 'ذ' to "z",
    )

    /** Vowel letters: ا â, ە a/e, ێ ê, ۆ o. */
    private val VOWELS = mapOf('ا' to "ɑ", 'آ' to "ɑ", 'ە' to "a", 'ێ' to "eː", 'ۆ' to "oː")

    /** Short words said without stress, as the Persian voice does with its own little words. */
    private val UNSTRESSED = setOf("و", "لە", "بە", "کە", "بۆ", "تا", "یان", "دە", "ئەو", "ئەم", "با", "نە", "وەک", "هەر", "لێ", "پێ", "تێ", "هەتا", "ی")

    private val ONES = listOf("سفر", "یەک", "دوو", "سێ", "چوار", "پێنج", "شەش", "حەوت", "هەشت", "نۆ")
    private val TEENS = listOf("دە", "یازدە", "دوازدە", "سێزدە", "چواردە", "پازدە", "شازدە", "حەڤدە", "هەژدە", "نۆزدە")
    private val TENS = listOf("", "دە", "بیست", "سی", "چل", "پەنجا", "شەست", "حەفتا", "هەشتا", "نەوەد")

    private val LATIN = mapOf(
        'a' to "a", 'b' to "b", 'c' to "k", 'd' to "d", 'e' to "e", 'f' to "f", 'g' to "ɡ", 'h' to "h", 'i' to "i",
        'j' to "dʒ", 'k' to "k", 'l' to "l", 'm' to "m", 'n' to "n", 'o' to "o", 'p' to "p", 'q' to "k", 'r' to "r",
        's' to "s", 't' to "t", 'u' to "u", 'v' to "v", 'w' to "v", 'x' to "ks", 'y' to "j", 'z' to "z",
    )

    private val PUNCT = mapOf(
        '،' to ",", ',' to ",", '؛' to ",", ';' to ",", ':' to ",", '—' to ",", '–' to ",", '-' to ",", '…' to ",",
        '.' to ".", '!' to "!", '?' to "?", '؟' to "?",
    )

    private val TOKEN = Regex("""([ء-ۿ‌]+|[A-Za-z]+)|([،,؛;:—–\-….!?؟])""")

    /** Kurdish number words, e.g. 686 -> "شەش سەد و هەشتا و شەش". */
    fun numberWords(n: Int): String = when {
        n < 10 -> ONES[n]
        n < 20 -> TEENS[n - 10]
        n < 100 -> TENS[n / 10] + if (n % 10 == 0) "" else " و " + ONES[n % 10]
        n < 1000 -> (if (n / 100 == 1) "سەد" else ONES[n / 100] + " سەد") + if (n % 100 == 0) "" else " و " + numberWords(n % 100)
        else -> n.toString().map { ONES[it - '0'] }.joinToString(" ")
    }

    /** The words of [text] (one sentence or more) as phonemes. */
    fun words(text: String): List<Word> {
        val digits = buildString {
            for (c in text) append(
                when (c) {
                    in '٠'..'٩' -> '0' + (c - '٠')
                    in '۰'..'۹' -> '0' + (c - '۰')
                    else -> c
                },
            )
        }
        val spelled = Regex("""\d+""").replace(digits) { m ->
            " " + (m.value.toIntOrNull()?.let(::numberWords) ?: m.value.map { ONES[it - '0'] }.joinToString(" ")) + " "
        }
        val out = ArrayList<Word>()
        for (m in TOKEN.findAll(spelled)) {
            val word = m.groups[1]?.value
            if (word != null) {
                val ipa = if (word.all { it.code < 128 }) latinIpa(word) else wordIpa(word)
                if (ipa.isNotEmpty()) out += Word(ipa, "")
            } else if (out.isNotEmpty()) {
                val p = PUNCT.getValue(m.value[0])
                val last = out.last()
                if (last.punct == "" || last.punct == ",") out[out.lastIndex] = last.copy(punct = p)
            }
        }
        return out
    }

    /** The lexicon key of a word (lower-case ASCII letters, unique for every phoneme string). */
    fun key(word: Word): String = buildString {
        append('w')
        for (b in (word.ipa + word.punct).encodeToByteArray()) {
            val v = b.toInt() and 0xFF
            append('a' + (v shr 4))
            append('a' + (v and 15))
        }
    }

    /** The voice tokens of a word in Piper's layout: a pad before the word and after every phoneme. */
    fun tokens(word: Word): List<String> {
        val result = ArrayList<String>(2 * (word.ipa.length + 1) + 1)
        result += "_"
        for (c in word.ipa + word.punct) {
            result += c.toString()
            result += "_"
        }
        return result
    }

    /** One lexicon line for [word]. */
    fun lexiconLine(word: Word): String = key(word) + " " + tokens(word).joinToString(" ")

    /** The text handed to the speech engine for one sentence: lexicon keys between the start and end keys. */
    fun engineText(words: List<Word>): String = buildString {
        append(BOS_KEY)
        for (w in words) append(' ').append(key(w))
        append(' ').append(EOS_KEY)
    }

    // ------------------------------------------------------------------ Sorani spelling -> phonemes

    private fun isVowelLetter(c: Char?) = c != null && c in VOWELS

    private data class Seg(val vowel: Boolean, val ipa: String)

    fun wordIpa(raw: String): String {
        val w = raw.replace("‌", "").replace("ـ", "").replace('ي', 'ی').replace('ك', 'ک').replace('ى', 'ی')
        if (w.isEmpty()) return ""
        if (w == "و") return "u"
        val segs = ArrayList<Seg>()
        var i = 0
        while (i < w.length) {
            val c = w[i]
            val prev = w.getOrNull(i - 1)
            val next = w.getOrNull(i + 1)
            when {
                c == 'ئ' -> Unit // carries the vowel that follows; silent
                c == 'و' && next == 'و' -> {
                    segs += Seg(true, "uː")
                    i++
                }
                c == 'و' -> segs += when {
                    prev == 'ئ' && next != null && !isVowelLetter(next) -> Seg(true, "u")
                    segs.lastOrNull()?.vowel == false && next != null && (isVowelLetter(next) || next == 'ی') && !isVowelLetter(prev) ->
                        Seg(true, "u") // consonant + w + vowel, as in چوار, خوێن
                    isVowelLetter(next) || next == 'ی' || next == 'و' || isVowelLetter(prev) || (i == 0 && next != null) -> Seg(false, "v")
                    else -> Seg(true, "u")
                }
                c == 'ی' -> {
                    val nextVowel = isVowelLetter(next) || next == 'و'
                    when {
                        (i == 0 && nextVowel) || isVowelLetter(prev) -> segs += Seg(false, "j")
                        nextVowel && segs.lastOrNull()?.vowel == true -> segs += Seg(false, "j")
                        nextVowel && segs.lastOrNull()?.vowel == false -> {
                            segs += Seg(true, "i")
                            segs += Seg(false, "j")
                        }
                        else -> segs += Seg(true, "i")
                    }
                }
                c in VOWELS -> {
                    val v = VOWELS.getValue(c)
                    // ê and o inside a word are short in the Persian voice's speech, long at the end.
                    segs += Seg(true, if ((c == 'ێ' || c == 'ۆ') && next != null) v.substring(0, 1) else v)
                }
                c in CONSONANTS -> segs += Seg(false, CONSONANTS.getValue(c))
                else -> Unit
            }
            i++
        }
        if (segs.isEmpty()) return ""
        // The unwritten short vowel (bizroke): two consonants at the start of a word, three in a row
        // inside one, or a word without any vowel get an "e" where Kurdish says its short "i".
        val withStart = ArrayList<Seg>(segs.size + 4)
        for ((k, s) in segs.withIndex()) {
            withStart += s
            if (k == 0 && !s.vowel && segs.getOrNull(1)?.vowel == false) withStart += Seg(true, "e")
        }
        val fixed = ArrayList<Seg>(withStart.size + 4)
        for (s in withStart) {
            fixed += s
            val n = fixed.size
            if (n >= 3 && !fixed[n - 1].vowel && !fixed[n - 2].vowel && !fixed[n - 3].vowel) {
                val last = fixed.removeAt(n - 1)
                if (fixed[n - 2].ipa in SONORANTS) {
                    val mid = fixed.removeAt(n - 2)
                    fixed += Seg(true, "e")
                    fixed += mid
                } else {
                    fixed += Seg(true, "e")
                }
                fixed += last
            }
        }
        if (fixed.none { it.vowel }) fixed.add(1.coerceAtMost(fixed.size), Seg(true, "e"))
        val stressAt = if (w in UNSTRESSED) -1 else fixed.indexOfLast { it.vowel }
        return buildString {
            for ((k, s) in fixed.withIndex()) {
                if (k == stressAt) append('ˈ')
                append(s.ipa)
            }
        }
    }

    private val SONORANTS = setOf("r", "l", "n", "m", "v", "j")

    /** Latin words (names such as "Zorix" or the player's name typed in Latin letters). */
    fun latinIpa(raw: String): String {
        val w = raw.lowercase()
        val stress = w.indexOfLast { it in "aeiou" }
        return buildString {
            var i = 0
            while (i < w.length) {
                val two = if (i + 1 < w.length) w.substring(i, i + 2) else ""
                when (two) {
                    "sh" -> { append("ʃ"); i += 2; continue }
                    "ch" -> { append("tʃ"); i += 2; continue }
                    "kh" -> { append("x"); i += 2; continue }
                }
                if (i == stress) append('ˈ')
                append(LATIN[w[i]] ?: "")
                i++
            }
        }
    }
}
