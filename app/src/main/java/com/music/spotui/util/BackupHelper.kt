package com.music.spotui.util

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.music.spotui.data.api.Api
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.data.preferences.BackupPref
import com.music.spotui.data.preferences.LocalPlaylist
import com.music.spotui.data.preferences.LocalPlaylistPref
import com.music.spotui.data.preferences.StreamQuality
import com.music.spotui.data.preferences.addLikedSongId
import com.music.spotui.data.preferences.getCellularQuality
import com.music.spotui.data.preferences.getCrossfadeMs
import com.music.spotui.data.preferences.getDownloadQuality
import com.music.spotui.data.preferences.getLikedSongIds
import com.music.spotui.data.preferences.getUpdateRepoUrl
import com.music.spotui.data.preferences.getWifiQuality
import com.music.spotui.data.preferences.isAutoPlayEnabled
import com.music.spotui.data.preferences.isCrossfadeDjMode
import com.music.spotui.data.preferences.isLibraryGridView
import com.music.spotui.data.preferences.isVideoFallbackEnabled
import com.music.spotui.data.preferences.setAutoPlayEnabled
import com.music.spotui.data.preferences.setCellularQuality
import com.music.spotui.data.preferences.setCrossfadeDjMode
import com.music.spotui.data.preferences.setCrossfadeMs
import com.music.spotui.data.preferences.setDownloadQuality
import com.music.spotui.data.preferences.setLibraryGridView
import com.music.spotui.data.preferences.setUpdateRepoUrl
import com.music.spotui.data.preferences.setVideoFallbackEnabled
import com.music.spotui.data.preferences.setWifiQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupHelper {
    private const val TAG = "BackupHelper"
    private const val AUTO_BACKUP_FILENAME = "spotui_autobackup.json"

    fun createBackupJson(context: Context): String {
        val root = JSONObject().apply {
            put("app", "spotui")
            put("version", 1)
            put("type", "spotui_backup")
            put("timestamp", System.currentTimeMillis())

            val data = JSONObject().apply {
                // 1. Local Playlists
                val playlistsArray = JSONArray()
                LocalPlaylistPref.getLocalPlaylists(context).forEach { pl ->
                    val plObj = JSONObject().apply {
                        put("id", pl.id)
                        put("name", pl.name)
                        put("coverUri", pl.coverUri)
                        put("createdAt", pl.createdAt)
                        val songsArray = JSONArray()
                        pl.songs.forEach { song ->
                            songsArray.put(JSONObject().apply {
                                put("id", song.id)
                                put("title", song.title)
                                put("album", song.album)
                                put("singer", song.singer)
                                put("coverUri", song.coverUri)
                                put("url", song.url)
                                put("spotifyTrackId", song.spotifyTrackId)
                                put("explicit", song.explicit)
                                put("durationMs", song.durationMs)
                                put("artistIds", song.artistIds)
                            })
                        }
                        put("songs", songsArray)
                    }
                    playlistsArray.put(plObj)
                }
                put("localPlaylists", playlistsArray)

                // 2. Liked Songs
                val likedArray = JSONArray()
                getLikedSongIds(context).forEach { likedArray.put(it) }
                put("likedSongs", likedArray)

                // 3. Settings
                val settingsObj = JSONObject().apply {
                    put("wifiQuality", getWifiQuality(context).name)
                    put("cellularQuality", getCellularQuality(context).name)
                    put("downloadQuality", getDownloadQuality(context).name)
                    put("crossfadeMs", getCrossfadeMs(context))
                    put("crossfadeDjMode", isCrossfadeDjMode(context))
                    put("videoFallback", isVideoFallbackEnabled(context))
                    put("autoPlay", isAutoPlayEnabled(context))
                    put("libraryGridView", isLibraryGridView(context))
                    put("updateRepoUrl", getUpdateRepoUrl(context))
                }
                put("settings", settingsObj)
            }
            put("data", data)
        }
        return root.toString(2)
    }

    /**
     * Validates if [jsonString] is a genuine Spotui backup file.
     * Returns the root [JSONObject] if valid, or null if invalid.
     */
    fun validateBackupJson(jsonString: String): JSONObject? {
        return runCatching {
            val root = JSONObject(jsonString)
            val app = root.optString("app")
            val type = root.optString("type")
            val version = root.optInt("version", -1)
            if (app == "spotui" && type == "spotui_backup" && version >= 1 && root.has("data")) {
                root
            } else {
                null
            }
        }.getOrNull()
    }

    fun writeBackupToDirectory(context: Context, filename: String, jsonString: String): Boolean {
        val dirUriStr = BackupPref.getDirectoryUri(context) ?: return false
        return runCatching {
            val treeUri = Uri.parse(dirUriStr)
            val dir = DocumentFile.fromTreeUri(context, treeUri) ?: return false
            if (!dir.canWrite()) {
                Log.w(TAG, "Cannot write to directory: $dirUriStr")
                return false
            }

            var file = dir.findFile(filename)
            if (file == null) {
                file = dir.createFile("application/json", filename)
            }
            if (file == null) return false

            context.contentResolver.openOutputStream(file.uri, "wt")?.use { os ->
                os.write(jsonString.toByteArray(Charsets.UTF_8))
                os.flush()
            }
            true
        }.getOrElse {
            Log.e(TAG, "Failed writing backup to directory", it)
            false
        }
    }

    suspend fun performAutoBackup(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!BackupPref.isAutoBackupEnabled(context)) return@withContext false
        val dirUriStr = BackupPref.getDirectoryUri(context) ?: return@withContext false
        val json = createBackupJson(context)
        val success = writeBackupToDirectory(context, AUTO_BACKUP_FILENAME, json)
        if (success) {
            Log.d(TAG, "Auto-backup performed successfully")
        }
        success
    }

    suspend fun performManualBackup(context: Context): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val dirUriStr = BackupPref.getDirectoryUri(context)
        if (dirUriStr.isNullOrBlank()) {
            return@withContext Pair(false, "Please select a backup folder first")
        }
        val timestampStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "spotui_backup_$timestampStr.json"
        val json = createBackupJson(context)

        val successManual = writeBackupToDirectory(context, filename, json)
        performAutoBackup(context)

        if (successManual) {
            val folderName = getFolderDisplayName(context, dirUriStr)
            Pair(true, "Backup saved as $filename in $folderName")
        } else {
            Pair(false, "Failed to write backup file to selected directory")
        }
    }

    suspend fun restoreFromFileUri(context: Context, fileUri: Uri): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val jsonString = runCatching {
            context.contentResolver.openInputStream(fileUri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            }
        }.getOrNull()

        if (jsonString.isNullOrBlank()) {
            return@withContext Pair(false, "Could not read selected file")
        }

        val root = validateBackupJson(jsonString)
            ?: return@withContext Pair(false, "Invalid backup file: Not a valid Spotui backup")

        runCatching {
            val data = root.getJSONObject("data")
            var restoredPlaylists = 0
            var restoredLiked = 0

            // 1. Restore Local Playlists
            if (data.has("localPlaylists")) {
                val arr = data.getJSONArray("localPlaylists")
                for (i in 0 until arr.length()) {
                    val plObj = arr.getJSONObject(i)
                    val songsArr = plObj.getJSONArray("songs")
                    val songs = mutableListOf<SongsModel>()
                    for (j in 0 until songsArr.length()) {
                        val s = songsArr.getJSONObject(j)
                        songs.add(SongsModel(
                            id = s.getInt("id"),
                            title = s.getString("title"),
                            album = s.optString("album"),
                            singer = s.getString("singer"),
                            coverUri = s.optString("coverUri"),
                            url = s.getString("url"),
                            spotifyTrackId = s.optString("spotifyTrackId"),
                            explicit = s.optBoolean("explicit", false),
                            durationMs = s.optInt("durationMs", 0),
                            artistIds = s.optString("artistIds", ""),
                        ))
                    }
                    val pl = LocalPlaylist(
                        id = plObj.getString("id"),
                        name = plObj.getString("name"),
                        coverUri = plObj.optString("coverUri"),
                        songs = songs,
                        createdAt = plObj.optLong("createdAt", System.currentTimeMillis())
                    )
                    LocalPlaylistPref.savePlaylist(context, pl)
                    restoredPlaylists++
                }
            }

            // 2. Restore Liked Songs
            if (data.has("likedSongs")) {
                val arr = data.getJSONArray("likedSongs")
                for (i in 0 until arr.length()) {
                    addLikedSongId(context, arr.getInt(i).toString())
                    restoredLiked++
                }
            }

            // 3. Restore Settings
            if (data.has("settings")) {
                val s = data.getJSONObject("settings")
                if (s.has("wifiQuality")) runCatching { setWifiQuality(context, StreamQuality.valueOf(s.getString("wifiQuality"))) }
                if (s.has("cellularQuality")) runCatching { setCellularQuality(context, StreamQuality.valueOf(s.getString("cellularQuality"))) }
                if (s.has("downloadQuality")) runCatching { setDownloadQuality(context, StreamQuality.valueOf(s.getString("downloadQuality"))) }
                if (s.has("crossfadeMs")) setCrossfadeMs(context, s.getInt("crossfadeMs"))
                if (s.has("crossfadeDjMode")) setCrossfadeDjMode(context, s.getBoolean("crossfadeDjMode"))
                if (s.has("videoFallback")) setVideoFallbackEnabled(context, s.getBoolean("videoFallback"))
                if (s.has("autoPlay")) setAutoPlayEnabled(context, s.getBoolean("autoPlay"))
                if (s.has("libraryGridView")) setLibraryGridView(context, s.getBoolean("libraryGridView"))
                if (s.has("updateRepoUrl")) setUpdateRepoUrl(context, s.getString("updateRepoUrl"))
            }

            Api.HomeCache.clear()
            Pair(true, "Restored $restoredPlaylists playlist(s) and $restoredLiked liked song(s)!")
        }.getOrElse { e ->
            Log.e(TAG, "Error restoring backup", e)
            Pair(false, "Failed to restore backup: ${e.message}")
        }
    }

    fun getFolderDisplayName(context: Context, dirUriStr: String?): String {
        if (dirUriStr.isNullOrBlank()) return "Not configured"
        return runCatching {
            val uri = Uri.parse(dirUriStr)
            val doc = DocumentFile.fromTreeUri(context, uri)
            doc?.name ?: uri.lastPathSegment ?: "Selected Folder"
        }.getOrDefault("Selected Folder")
    }
}
