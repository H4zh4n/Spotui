package com.music.spotui.data.preferences

import android.content.Context
import com.music.spotui.data.entity.SongsModel
import org.json.JSONArray
import org.json.JSONObject

data class LocalPlaylist(
    val id: String,
    val name: String,
    val coverUri: String,
    val songs: List<SongsModel>,
    val createdAt: Long = System.currentTimeMillis()
)

object LocalPlaylistPref {
    private const val PREF = "LocalPlaylists"
    private const val KEY_PLAYLIST_IDS = "local_playlist_ids_order"

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

    private fun LocalPlaylist.toJson(): String = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("coverUri", coverUri)
        put("createdAt", createdAt)
        val songArray = JSONArray()
        songs.forEach { songArray.put(it.toJsonObject()) }
        put("songs", songArray)
    }.toString()

    private fun parsePlaylist(json: String): LocalPlaylist? = runCatching {
        val o = JSONObject(json)
        val songArray = o.getJSONArray("songs")
        val songList = mutableListOf<SongsModel>()
        for (i in 0 until songArray.length()) {
            parseSongFromObj(songArray.getJSONObject(i))?.let { songList.add(it) }
        }
        LocalPlaylist(
            id = o.getString("id"),
            name = o.getString("name"),
            coverUri = o.optString("coverUri", ""),
            songs = songList,
            createdAt = o.optLong("createdAt", System.currentTimeMillis())
        )
    }.getOrNull()

    fun createPlaylist(context: Context, name: String, initialSong: SongsModel? = null): LocalPlaylist {
        val id = "local_pl_" + System.currentTimeMillis()
        val songs = if (initialSong != null) listOf(initialSong) else emptyList()
        val coverUri = initialSong?.coverUri ?: ""
        val playlist = LocalPlaylist(id = id, name = name, coverUri = coverUri, songs = songs)
        savePlaylist(context, playlist)
        return playlist
    }

    fun savePlaylist(context: Context, playlist: LocalPlaylist) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val existingIds = getPlaylistIds(context).toMutableList()
        if (!existingIds.contains(playlist.id)) {
            existingIds.add(0, playlist.id)
            savePlaylistIds(context, existingIds)
        }
        prefs.edit().putString(playlist.id, playlist.toJson()).apply()
    }

    fun getLocalPlaylists(context: Context): List<LocalPlaylist> {
        val ids = getPlaylistIds(context)
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return ids.mapNotNull { id ->
            prefs.getString(id, null)?.let { parsePlaylist(it) }
        }
    }

    fun getLocalPlaylist(context: Context, playlistId: String): LocalPlaylist? {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val json = prefs.getString(playlistId, null) ?: return null
        return parsePlaylist(json)
    }

    fun addSongToPlaylist(context: Context, playlistId: String, song: SongsModel): Boolean {
        val playlist = getLocalPlaylist(context, playlistId) ?: return false
        if (playlist.songs.any { it.id == song.id || (it.spotifyTrackId.isNotBlank() && song.spotifyTrackId.isNotBlank() && it.spotifyTrackId == song.spotifyTrackId) }) {
            return true
        }
        val updatedSongs = playlist.songs + song
        val coverUri = playlist.coverUri.ifBlank { song.coverUri }
        val updatedPlaylist = playlist.copy(songs = updatedSongs, coverUri = coverUri)
        savePlaylist(context, updatedPlaylist)
        return true
    }

    fun removeSongFromPlaylist(context: Context, playlistId: String, songId: Int, spotifyTrackId: String = ""): Boolean {
        val playlist = getLocalPlaylist(context, playlistId) ?: return false
        val updatedSongs = playlist.songs.filterNot { 
            it.id == songId || (spotifyTrackId.isNotBlank() && it.spotifyTrackId == spotifyTrackId) 
        }
        val coverUri = if (updatedSongs.isNotEmpty()) playlist.coverUri.ifBlank { updatedSongs.first().coverUri } else playlist.coverUri
        val updatedPlaylist = playlist.copy(songs = updatedSongs, coverUri = coverUri)
        savePlaylist(context, updatedPlaylist)
        return true
    }

    fun isSongInPlaylist(context: Context, playlistId: String, songId: Int, spotifyTrackId: String = ""): Boolean {
        val playlist = getLocalPlaylist(context, playlistId) ?: return false
        return playlist.songs.any { it.id == songId || (spotifyTrackId.isNotBlank() && it.spotifyTrackId == spotifyTrackId) }
    }

    fun deletePlaylist(context: Context, playlistId: String) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs.edit().remove(playlistId).apply()
        val existingIds = getPlaylistIds(context).toMutableList()
        existingIds.remove(playlistId)
        savePlaylistIds(context, existingIds)
    }

    private fun getPlaylistIds(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PLAYLIST_IDS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(emptyList())
    }

    private fun savePlaylistIds(context: Context, ids: List<String>) {
        val arr = JSONArray()
        ids.forEach { arr.put(it) }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY_PLAYLIST_IDS, arr.toString())
            .apply()
    }
}
