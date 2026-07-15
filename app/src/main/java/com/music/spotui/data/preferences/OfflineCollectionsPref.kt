package com.music.spotui.data.preferences

import android.content.Context
import com.music.spotui.data.entity.SongsModel
import org.json.JSONArray
import org.json.JSONObject

data class OfflineCollection(
    val id: String,
    val name: String,
    val coverUri: String,
    val artists: String,
    val isPlaylist: Boolean,
    val songs: List<SongsModel>
)

object OfflineCollectionsPref {
    private const val PREF = "OfflineCollections"

    private fun SongsModel.toJsonObject(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("album", album)
        put("singer", singer)
        put("coverUri", coverUri)
        put("url", url)
        put("spotifyTrackId", spotifyTrackId)
        put("explicit", explicit)
        put("durationMs", durationMs)
        put("artistIds", artistIds)
    }

    private fun parseSongFromObj(o: JSONObject): SongsModel? = runCatching {
        SongsModel(
            id = o.getInt("id"),
            title = o.getString("title"),
            album = o.optString("album"),
            singer = o.getString("singer"),
            coverUri = o.optString("coverUri"),
            url = o.getString("url"),
            spotifyTrackId = o.optString("spotifyTrackId"),
            explicit = o.optBoolean("explicit", false),
            durationMs = o.optInt("durationMs", 0),
            artistIds = o.optString("artistIds", ""),
        )
    }.getOrNull()

    private fun OfflineCollection.toJson(): String = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("coverUri", coverUri)
        put("artists", artists)
        put("isPlaylist", isPlaylist)
        val songArray = JSONArray()
        songs.forEach { songArray.put(it.toJsonObject()) }
        put("songs", songArray)
    }.toString()

    private fun parseCollection(json: String): OfflineCollection? = runCatching {
        val o = JSONObject(json)
        val songArray = o.getJSONArray("songs")
        val songList = mutableListOf<SongsModel>()
        for (i in 0 until songArray.length()) {
            parseSongFromObj(songArray.getJSONObject(i))?.let { songList.add(it) }
        }
        OfflineCollection(
            id = o.getString("id"),
            name = o.getString("name"),
            coverUri = o.getString("coverUri"),
            artists = o.getString("artists"),
            isPlaylist = o.getBoolean("isPlaylist"),
            songs = songList
        )
    }.getOrNull()

    fun saveCollection(
        context: Context,
        id: String,
        name: String,
        coverUri: String,
        artists: String,
        isPlaylist: Boolean,
        songs: List<SongsModel>
    ) {
        val col = OfflineCollection(id, name, coverUri, artists, isPlaylist, songs)
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(id, col.toJson())
            .apply()
    }

    fun getCollection(context: Context, id: String): OfflineCollection? {
        val json = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(id, null) ?: return null
        return parseCollection(json)
    }

    fun getOfflineCollections(context: Context): List<OfflineCollection> {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val allCollections = prefs.all.values.mapNotNull { (it as? String)?.let { parseCollection(it) } }
        // Filter: only return collections that have at least one downloaded song
        return allCollections.filter { col ->
            col.songs.any { song ->
                com.music.spotui.data.preferences.isDownloaded(context, song.id.toString())
            }
        }
    }

    fun onTrackDownloaded(context: Context, song: SongsModel) {
        // If the track belongs to an album, ensure we have an offline collection for that album
        if (song.album.isNotBlank()) {
            val albumId = "album:${song.album}|${song.singer}"
            val existing = getCollection(context, albumId)
            if (existing == null) {
                // Save a basic offline album collection containing this track
                saveCollection(
                    context = context,
                    id = albumId,
                    name = song.album,
                    coverUri = song.coverUri,
                    artists = song.singer,
                    isPlaylist = false,
                    songs = listOf(song)
                )
            } else {
                // If it exists, but doesn't have the song in its list (for some reason), append it.
                if (existing.songs.none { it.id == song.id }) {
                    val updatedSongs = existing.songs + song
                    saveCollection(
                        context = context,
                        id = albumId,
                        name = existing.name,
                        coverUri = existing.coverUri,
                        artists = existing.artists,
                        isPlaylist = false,
                        songs = updatedSongs
                    )
                }
            }
        }
    }

    fun clearAll(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
