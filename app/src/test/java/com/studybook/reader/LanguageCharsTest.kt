package com.studybook.reader

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The Practice Selector's language filter, on the readings in src/main/assets/training/readings. */
class LanguageCharsTest {
    private fun chars(lang: String) = LanguageChars(lang) { id ->
        File("src/main/assets/training/readings/${id.toString(16)}.json").takeIf { it.isFile }?.let { JSONObject(it.readText()) }
    }

    @Test
    fun eachLanguageKeepsOnlyItsOwnCharacters() {
        val ko = chars("ko")
        assertTrue(ko.has("학"))
        assertTrue(ko.has("學"))
        assertFalse(ko.has("谢"))
        assertFalse(ko.has("あ"))
        val ja = chars("ja")
        assertTrue(ja.has("学"))
        assertTrue(ja.has("っ"))
        assertFalse(ja.has("谢"))
        assertFalse(ja.has("학"))
        val yue = chars("yue")
        assertTrue(yue.has("學"))
        assertFalse(yue.has("あ"))
        assertFalse(yue.has("학"))
        assertFalse(chars("cmn").has("A"))
    }

    @Test
    fun aScannedPageIsInTheLanguageOfItsBestRead() {
        val chinese = "學校老師學生朋友".map { it.toString() }
        // A Chinese page: the Chinese read finds the most; Korean read it as one hangul, Japanese misread some.
        assertEquals("zh", LanguageChars.pageScript(mapOf("zh" to chinese, "ko" to listOf("치"), "ja" to "老師寧生朋友摩校ロ".map { it.toString() })))
        // Japanese: kana among the kanji.
        assertEquals("ja", LanguageChars.pageScript(mapOf("zh" to "学校".map { it.toString() }, "ja" to "学校へいきます".map { it.toString() })))
        // Korean: hangul, with or without hanja.
        assertEquals("ko", LanguageChars.pageScript(mapOf("ko" to "학교學校에갑니다".map { it.toString() }, "zh" to listOf("學", "校"))))
        assertNull(LanguageChars.pageScript(mapOf("zh" to emptyList(), "ko" to emptyList())))
        assertEquals("zh", LanguageChars.script("yue"))
        assertEquals("zh", LanguageChars.script("cmn"))
    }
}
