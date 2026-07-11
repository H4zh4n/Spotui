package com.music.spotui.data.preferences

import android.content.Context
import com.music.spotui.data.entity.SongsModel
import org.json.JSONArray
import org.json.JSONObject

// Persists the last playing track + position so a fresh app launch can restore
// the session (mini player shows the track, play resumes where it stopped).

private const val PREF = "PlaybackState"
private const val KEY_SONG = "song"
private const val KEY_POSITION = "positionMs"
private const val KEY_QUEUE = "queue"

private fun songToJson(song: SongsModel): JSONObject {
    return JSONObject().apply {
        put("id", song.id)
        put("title", song.title)
        put("album", song.album)
        put("singer", song.singer)
        put("coverUri", song.coverUri)
        put("url", song.url)
        put("spotifyTrackId", song.spotifyTrackId)
        put("explicit", song.explicit)
        put("durationMs", song.durationMs)
    }
}

private fun jsonToSong(o: JSONObject): SongsModel {
    return SongsModel(
        id = o.optInt("id", -1),
        title = o.optString("title"),
        album = o.optString("album"),
        singer = o.optString("singer"),
        coverUri = o.optString("coverUri"),
        url = o.optString("url"),
        spotifyTrackId = o.optString("spotifyTrackId"),
        explicit = o.optBoolean("explicit", false),
        durationMs = o.optInt("durationMs", 0),
    )
}

/** Saves the track (resets the stored position when the track changed). */
fun saveLastPlayback(context: Context, song: SongsModel) {
    if (song.title.isBlank() || song.url.isBlank()) return
    val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    val prevId = runCatching { p.getString(KEY_SONG, null)?.let { JSONObject(it).optInt("id", -1) } }
        .getOrNull() ?: -1
    val json = songToJson(song)
    p.edit().apply {
        putString(KEY_SONG, json.toString())
        if (prevId != song.id) putLong(KEY_POSITION, 0L)
    }.apply()
}

/** Updates just the position of the stored track (called periodically). */
fun saveLastPosition(context: Context, positionMs: Long) {
    if (positionMs <= 0) return
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .edit().putLong(KEY_POSITION, positionMs).apply()
}

/** The last track and position, or null if nothing was ever played. */
fun loadLastPlayback(context: Context): Pair<SongsModel, Long>? {
    val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    val raw = p.getString(KEY_SONG, null) ?: return null
    return runCatching {
        val o = JSONObject(raw)
        val song = jsonToSong(o)
        if (song.title.isBlank() || song.url.isBlank()) null
        else song to p.getLong(KEY_POSITION, 0L)
    }.getOrNull()
}

/** Saves the current playback queue. */
fun saveLastQueue(context: Context, queue: List<SongsModel>) {
    val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    val array = JSONArray()
    for (song in queue) {
        if (song.title.isNotBlank() && song.url.isNotBlank()) {
            array.put(songToJson(song))
        }
    }
    p.edit().putString(KEY_QUEUE, array.toString()).apply()
}

/** Loads the last saved playback queue. */
fun loadLastQueue(context: Context): List<SongsModel> {
    val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    val raw = p.getString(KEY_QUEUE, null) ?: return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        val list = mutableListOf<SongsModel>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val song = jsonToSong(o)
            if (song.title.isNotBlank() && song.url.isNotBlank()) {
                list.add(song)
            }
        }
        list
    }.getOrNull() ?: emptyList()
}
