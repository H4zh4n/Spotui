package com.music.spotui.data.preferences

import android.content.Context
import com.music.spotui.data.entity.LyricLine
import com.music.spotui.data.entity.Lyrics
import org.json.JSONArray
import org.json.JSONObject

private const val PREF_NAME = "LyricsCache"

/**
 * Persistent disk cache for resolved lyrics. Keyed by the same
 * "cleanTitle|primaryArtist" key used by the in-memory cache in [LyricsApi].
 *
 * Only successful [Lyrics] results are persisted — misses are never written so
 * a transient network failure doesn't poison the disk cache and retries happen
 * naturally on the next launch.
 *
 * Lyrics are static content and never expire.
 */
object LyricsCachePref {

    fun get(context: Context, key: String): Lyrics? {
        val json = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(key, null) ?: return null
        return runCatching {
            val o = JSONObject(json)
            val synced = o.getBoolean("synced")
            val arr = o.getJSONArray("lines")
            val lines = ArrayList<LyricLine>(arr.length())
            for (i in 0 until arr.length()) {
                val lo = arr.getJSONObject(i)
                lines.add(LyricLine(timeMs = lo.getLong("t"), text = lo.getString("x")))
            }
            Lyrics(lines = lines, synced = synced)
        }.getOrNull()
    }

    fun put(context: Context, key: String, lyrics: Lyrics) {
        if (lyrics.isEmpty) return
        val o = JSONObject().apply {
            put("synced", lyrics.synced)
            put("cachedAt", System.currentTimeMillis())
            put("lines", JSONArray().apply {
                for (l in lyrics.lines) {
                    put(JSONObject().apply {
                        put("t", l.timeMs)
                        put("x", l.text)
                    })
                }
            })
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(key, o.toString())
            .apply()
    }

    fun remove(context: Context, key: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .remove(key)
            .apply()
    }

    /** Number of entries in the disk cache (for debugging / settings). */
    fun size(context: Context): Int =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).all.size
}
