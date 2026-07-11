package com.music.spotui.data.preferences

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local listening history: one entry per playback start, newest first, capped.
 * Backs the History & stats screen; individual entries can be deleted and the
 * whole log cleared.
 */
data class HistoryEntry(
    val ts: Long,
    val songId: Int,
    val title: String,
    val singer: String,
    val album: String = "",
    val image: String = "",
    val url: String = "",
)

private const val PREF = "ListeningHistory"
private const val KEY = "entries"
private const val MAX_ENTRIES = 500

private fun HistoryEntry.toJson() = JSONObject().apply {
    put("ts", ts); put("songId", songId); put("title", title)
    put("singer", singer); put("album", album); put("image", image)
    put("url", url)
}

private fun JSONObject.toEntry() = HistoryEntry(
    ts = optLong("ts"), songId = optInt("songId", -1), title = optString("title"),
    singer = optString("singer"), album = optString("album"), image = optString("image"),
    url = optString("url"),
)

fun getListeningHistory(context: Context): List<HistoryEntry> {
    val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null)
        ?: return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { arr.getJSONObject(it).toEntry() }
    }.getOrDefault(emptyList())
}

private fun save(context: Context, entries: List<HistoryEntry>) {
    val arr = JSONArray().also { a -> entries.forEach { a.put(it.toJson()) } }
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .edit().putString(KEY, arr.toString()).apply()
}

fun addListeningHistory(context: Context, entry: HistoryEntry) {
    if (entry.title.isBlank()) return
    val existing = getListeningHistory(context)
    // Don't log the same track twice in a row (pause/resume, seek restarts).
    if (existing.firstOrNull()?.songId == entry.songId) return
    save(context, (listOf(entry) + existing).take(MAX_ENTRIES))
}

fun removeListeningHistory(context: Context, entry: HistoryEntry) {
    save(context, getListeningHistory(context).filterNot { it.ts == entry.ts && it.songId == entry.songId })
}

fun clearListeningHistory(context: Context) {
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY).apply()
}
