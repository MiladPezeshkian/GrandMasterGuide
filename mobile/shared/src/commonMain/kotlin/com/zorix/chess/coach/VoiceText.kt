package com.zorix.chess.coach

/** Final touches before text is handed to a voice. */
object VoiceText {
    private val SORANI = mapOf(
        'ڕ' to "ر", 'ڵ' to "ل", 'ۆ' to "و", 'ێ' to "ی", 'ھ' to "ه", 'ڤ' to "و", 'ك' to "ک", 'ي' to "ی",
    )

    /**
     * Text for the Persian voice. Kurdish (Sorani) is read by the Persian voice, so its extra letters
     * become their closest Persian sounds: the vowel ە is read like the Persian final "ه" at the end
     * of a word and like a short "a" (fatha) inside one.
     */
    fun forPersianVoice(text: String): String {
        val out = StringBuilder(text.length + 8)
        for ((i, c) in text.withIndex()) {
            when {
                c == 'ە' -> {
                    val next = text.getOrNull(i + 1)
                    val wordEnd = next == null || !next.isLetter()
                    out.append(if (wordEnd) "ه" else "َ")
                }
                c in SORANI -> out.append(SORANI.getValue(c))
                else -> out.append(c)
            }
        }
        return out.toString()
    }

    /** Splits text into sentences so speech can start before the whole text is synthesized. */
    fun sentences(text: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        for (c in text) {
            current.append(c)
            if (c == '.' || c == '!' || c == '?' || c == '؟' || c == '\n' || c == '…') {
                val s = current.toString().trim()
                if (s.any { it.isLetterOrDigit() }) result += s
                current.clear()
            }
        }
        val rest = current.toString().trim()
        if (rest.any { it.isLetterOrDigit() }) result += rest
        return result
    }
}
