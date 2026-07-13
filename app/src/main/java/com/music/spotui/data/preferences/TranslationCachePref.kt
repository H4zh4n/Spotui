package com.music.spotui.data.preferences

import android.content.Context
import com.music.spotui.data.entity.LyricLine
import org.json.JSONArray
import org.json.JSONObject

private const val PREF_NAME = "TranslationCache"

// Same normalisation as LyricsApi.cleanTitle so the cache key maps to the same song.
private val featTag = Regex("""\s*[(\[][^)\]]*(feat\.?|ft\.?|with )[^)\]]*[)\]]""", RegexOption.IGNORE_CASE)
private val bracketTag = Regex("""\s*[(\[][^)\]]*(remaster|remastered|live|version|edit|mono|stereo|deluxe|bonus)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE)
private fun cleanTitle(title: String) =
    title.substringBefore(" - ")
        .replace(featTag, "")
        .replace(bracketTag, "")
        .trim()

fun translationCacheKey(title: String, artist: String, sourceLang: String, targetLang: String): String =
    "${cleanTitle(title).lowercase()}|${artist.substringBefore(",").trim().lowercase()}|$sourceLang|$targetLang"

/**
 * Persistent disk cache for translated lyrics. Keyed by the same song key
 * used in [LyricsApi] plus the language pair so different target languages
 * are cached independently.
 */
object TranslationCachePref {

    fun get(context: Context, key: String): List<LyricLine>? {
        val json = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(key, null) ?: return null
        return runCatching {
            val arr = JSONArray(json)
            val lines = ArrayList<LyricLine>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                lines.add(
                    LyricLine(
                        timeMs = o.getLong("t"),
                        text = o.getString("x"),
                        translatedText = o.optString("tr", null as String?),
                    )
                )
            }
            lines
        }.getOrNull()
    }

    fun put(context: Context, key: String, lines: List<LyricLine>) {
        if (lines.isEmpty()) return
        val arr = JSONArray()
        for (l in lines) {
            val o = JSONObject().apply {
                put("t", l.timeMs)
                put("x", l.text)
                if (!l.translatedText.isNullOrBlank()) put("tr", l.translatedText)
            }
            arr.put(o)
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(key, arr.toString())
            .apply()
    }

    fun remove(context: Context, key: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .remove(key)
            .apply()
    }
}