package com.zorix.chess.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KurdishVoiceTest {
    /** The Kurdish voice's symbols (its tokens.txt without the space and line break). */
    private val known = "_^$!\"-.:،؛؟ءابتجحخدرزسشعغفقلمنهوپچڕژڤکگڵۆیێەئ".toSet()

    @Test
    fun textBecomesTheVoiceLetters() {
        // ئ is read as ي + hamza, Arabic kaf becomes keheh, a number is spelled, the end gets a full stop.
        assertEquals("ئەسپ سێ کە.", KurdishVoice.speechText("ئەسپ ٣ كە", known))
        assertEquals("باشە؟", KurdishVoice.speechText("باشە؟", known))
        // Punctuation on its own joins the word before it; quotes are dropped.
        assertEquals("کش، باشە.", KurdishVoice.speechText("کش ، «باشە»", known))
        // A suffix stays on its number.
        assertEquals("قەڵا پێنجە.", KurdishVoice.speechText("قەڵا ٥ە", known))
        // Latin names are written in Sorani letters.
        assertEquals("میلاد.", KurdishVoice.speechText("Milad", known))
        assertEquals("", KurdishVoice.speechText(" — ", known))
    }

    @Test
    fun numbers() {
        assertEquals("شەش سەد و هەشتا و شەش", KurdishVoice.numberWords(686))
        assertEquals("زۆریکس", KurdishVoice.latinToSorani("Zorix"))
    }

    @Test
    fun longSentencesAreSplitAtCommas() {
        val long = "یەکەم بەشی ئەم ڕستەیە زۆر درێژە و بەردەوام دەبێت، دووەم بەشی ئەم ڕستەیە هەروەها درێژە، کۆتایی؟"
        val parts = KurdishVoice.chunks(long)
        assertTrue(parts.size > 1 && parts.all { it.length <= 71 })
        assertTrue(parts.dropLast(1).all { it.endsWith(".") } && parts.last().endsWith("؟"))
        assertEquals(listOf("کورت."), KurdishVoice.chunks("کورت."))
    }
}
