package com.studybook.reader

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Which characters belong to a language, as writing practice decides it (core.js inLanguage): kana are Japanese and
 * hangul Korean, and a Han character needs a reading in the language in writing practice's readings
 * (assets/training/readings, "Jyutping|Pinyin|Japanese|Korean" per character, in files of 1024 code points). So the
 * Practice Selector lists only the language being practised, and languages are never mixed.
 */
class LanguageChars(private val lang: String, private val shard: (Int) -> JSONObject?) {

    fun has(ch: String): Boolean {
        val cp = ch.codePointAt(0)
        return when (Character.UnicodeScript.of(cp)) {
            Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> lang == "ja"
            Character.UnicodeScript.HANGUL -> lang == "ko"
            Character.UnicodeScript.HAN -> {
                val field = FIELDS.indexOf(lang)
                val readings = shard(cp shr SHARD_BITS)?.optString(ch).orEmpty().split('|')
                field >= 0 && readings.getOrNull(field).orEmpty().isNotBlank()
            }
            else -> false
        }
    }

    companion object {
        /** The fields of a readings entry, in order (core.js LANGS[lang].field). */
        private val FIELDS = listOf("yue", "cmn", "ja", "ko")
        private const val SHARD_BITS = 10
        private val shards = ConcurrentHashMap<Int, JSONObject>()

        /** For [lang], reading the app's own readings files (each loaded once). */
        fun of(context: Context, lang: String) = LanguageChars(lang) { id ->
            shards[id] ?: runCatching {
                JSONObject(context.assets.open("training/readings/${id.toString(16)}.json").bufferedReader().use { it.readText() })
            }.getOrNull()?.also { shards[id] = it }
        }

        /** The script a language is written and recognised in: "zh" for Cantonese and Mandarin, else its own. */
        fun script(lang: String): String = if (lang == "ja" || lang == "ko") lang else "zh"

        /**
         * The language a scanned page is in, from what each recogniser read on it (characters by script, as
         * [PdfOcr.readAll]): the read that found the most characters, then Korean if a fifth of them are hangul,
         * Japanese if a fifth are kana (a stray misread kana on a Chinese page isn't enough), else Chinese. Null when
         * nothing was read.
         */
        fun pageScript(reads: Map<String, List<String>>): String? {
            val best = reads.values.maxByOrNull { it.size }?.takeIf { it.isNotEmpty() } ?: return null
            fun share(vararg scripts: Character.UnicodeScript) =
                best.count { Character.UnicodeScript.of(it.codePointAt(0)) in scripts }.toFloat() / best.size
            return when {
                share(Character.UnicodeScript.HANGUL) >= 0.2f -> "ko"
                share(Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA) >= 0.2f -> "ja"
                else -> "zh"
            }
        }

        /** The language's name in English, the language every screen shares. */
        fun name(context: Context, lang: String): String = context.getString(when (lang) {
            "cmn" -> R.string.language_name_cmn
            "ja" -> R.string.language_name_ja
            "ko" -> R.string.language_name_ko
            else -> R.string.language_name_yue
        })
    }
}
