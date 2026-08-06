package com.music.spotui.data.preferences

import android.content.Context
import com.music.spotui.data.entity.SongsModel
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent store for user-imported local audio files (folder / individual
 * songs). Each track is kept as its content URI plus the metadata read at import
 * time, and is surfaced as a [SongsModel] whose `url` is the URI itself — so it
 * plays through the normal player/queue like any other track.
 */
private const val PREF = "LocalLibrary"
private const val KEY = "tracks"

data class LocalTrack(
    val id: Int,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Int,
    val coverUri: String,
)

/** Stable, non-negative id for a local file, matching the app's id convention. */
fun localTrackId(uri: String): Int = "local:$uri".hashCode() and 0x7fffffff

private fun prefs(context: Context) =
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

private fun LocalTrack.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("uri", uri)
    put("title", title)
    put("artist", artist)
    put("album", album)
    put("durationMs", durationMs)
    put("coverUri", coverUri)
}

private fun parse(obj: JSONObject): LocalTrack? = runCatching {
    val uri = obj.optString("uri")
    if (uri.isBlank()) return@runCatching null
    LocalTrack(
        id = obj.optInt("id", localTrackId(uri)),
        uri = uri,
        title = obj.optString("title"),
        artist = obj.optString("artist"),
        album = obj.optString("album"),
        durationMs = obj.optInt("durationMs"),
        coverUri = obj.optString("coverUri"),
    )
}.getOrNull()

fun getLocalTracks(context: Context): List<LocalTrack> {
    val raw = prefs(context).getString(KEY, null) ?: return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { parse(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())
}

/** Add tracks, de-duplicating by URI (keeps insertion order, new ones appended). */
fun addLocalTracks(context: Context, tracks: List<LocalTrack>) {
    if (tracks.isEmpty()) return
    val existing = getLocalTracks(context)
    val seen = existing.mapTo(HashSet()) { it.uri }
    val merged = existing + tracks.filter { seen.add(it.uri) }
    save(context, merged)
}

fun removeLocalTrack(context: Context, uri: String) {
    save(context, getLocalTracks(context).filterNot { it.uri == uri })
}

fun clearLocalTracks(context: Context) {
    prefs(context).edit().remove(KEY).apply()
}

private fun save(context: Context, tracks: List<LocalTrack>) {
    val arr = JSONArray()
    tracks.forEach { arr.put(it.toJson()) }
    prefs(context).edit().putString(KEY, arr.toString()).apply()
}

fun LocalTrack.toSong(): SongsModel = SongsModel(
    id = id,
    title = title.ifBlank { "Unknown title" },
    album = album,
    singer = artist.ifBlank { "Unknown artist" },
    coverUri = coverUri,
    url = uri,
    spotifyTrackId = "",
    explicit = false,
    durationMs = durationMs,
)

fun getLocalSongs(context: Context): List<SongsModel> =
    getLocalTracks(context).map { it.toSong() }
