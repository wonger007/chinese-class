package com.studybook.reader

import org.json.JSONObject
import org.junit.Assert.assertFalse
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
}
