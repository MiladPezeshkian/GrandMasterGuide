package com.zorix.chess.coach

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class KurdishVoiceTest {
    @Test
    fun soraniSpellingBecomesPhonemes() {
        assertEquals("ˈasp", KurdishVoice.wordIpa("ئەسپ"))
        assertEquals("sˈeː", KurdishVoice.wordIpa("سێ"))
        assertEquals("dˈuː", KurdishVoice.wordIpa("دوو"))
        assertEquals("tʃuˈɑr", KurdishVoice.wordIpa("چوار"))
        assertEquals("vazˈir", KurdishVoice.wordIpa("وەزیر"))
        assertEquals("q1alˈɑ", KurdishVoice.wordIpa("قەڵا"))
        assertEquals("bɑʃterˈa", KurdishVoice.wordIpa("باشترە"))   // the unwritten short vowel
        assertEquals("matersidɑjˈa", KurdishVoice.wordIpa("مەترسیدایە"))
        assertEquals("keʃmˈɑt", KurdishVoice.wordIpa("کش‌مات"))
        assertEquals("la", KurdishVoice.wordIpa("لە"))               // little words carry no stress
        assertEquals("u", KurdishVoice.wordIpa("و"))
    }

    @Test
    fun numbersAndPunctuation() {
        assertEquals("شەش سەد و هەشتا و شەش", KurdishVoice.numberWords(686))
        val words = KurdishVoice.words("ئەسپ ٣، باشە؟")
        assertEquals(listOf("ˈasp", "sˈeː", "bɑʃˈa"), words.map { it.ipa })
        assertEquals(listOf("", ",", "?"), words.map { it.punct })
    }

    @Test
    fun lexiconKeysAreAsciiAndDistinct() {
        val a = KurdishVoice.Word("ˈasp", "")
        val b = KurdishVoice.Word("ˈasp", ".")
        for (w in listOf(a, b)) assertTrue(KurdishVoice.key(w).all { it in 'a'..'z' })
        assertTrue(KurdishVoice.key(a) != KurdishVoice.key(b))
        assertEquals(listOf("_", "ˈ", "_", "a", "_", "s", "_", "p", "_", ".", "_"), KurdishVoice.tokens(b))
        val text = KurdishVoice.engineText(listOf(a, b))
        assertTrue(text.startsWith(KurdishVoice.BOS_KEY) && text.endsWith(KurdishVoice.EOS_KEY))
    }

    @Test
    fun lettersForTheNativeVoice() {
        val known = "_^$ !\"-.:،؛؟ءابتجحخدرزسشعغفقلمنهوپچڕژڤکگڵۆیێە\u064A\u0654".toSet()
        // ئ is read as ي + hamza, Arabic kaf becomes keheh, a number is spelled, the end gets a full stop.
        val words = KurdishVoice.letterWords("ئەسپ ٣ كە", known)
        assertEquals(listOf("\u064A\u0654ەسپ", "سێ", "کە."), words.map { it.ipa })
        assertEquals(listOf("باشە؟"), KurdishVoice.letterWords("باشە؟", known).map { it.ipa })
        // Punctuation on its own joins the word before it.
        assertEquals(listOf("کش،", "باشە."), KurdishVoice.letterWords("کش ، باشە", known).map { it.ipa })
    }

    @Test
    fun longSentencesAreSplitAtCommas() {
        val long = "یەکەم بەشی ئەم ڕستەیە زۆر درێژە و بەردەوام دەبێت، دووەم بەشی ئەم ڕستەیە هەروەها درێژە، کۆتایی؟"
        val parts = KurdishVoice.letterChunks(long)
        assertTrue(parts.size > 1 && parts.all { it.length <= 70 + 1 })
        assertTrue(parts.dropLast(1).all { it.endsWith(".") } && parts.last().endsWith("؟"))
        assertEquals(listOf("کورت."), KurdishVoice.letterChunks("کورت."))
    }
}
