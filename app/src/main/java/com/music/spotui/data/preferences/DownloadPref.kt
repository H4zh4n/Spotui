package com.music.spotui.data.preferences

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.music.spotui.data.entity.SongsModel
import org.json.JSONObject
import java.io.File

/**
 * Tracks downloaded (offline) tracks. Each entry is keyed by the song id and stores
 * the full [SongsModel] plus the local file path the audio was saved to, so the
 * Downloads screen can list them offline and [com.music.spotui.di.SongPlayer]
 * can play the local file instead of re-resolving + streaming from YouTube.
 */
private const val PREF = "Downloads"
private const val KEY_DL_SORT = "download_sort_option"
private const val KEY_DL_DESC = "download_sort_desc"

enum class DownloadSortOption(val label: String) {
    DATE("Added"),
    TITLE("Title"),
    ARTIST("Artist")
}

fun getDownloadsSortOption(context: Context): DownloadSortOption {
    val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    val saved = prefs.getString(KEY_DL_SORT, DownloadSortOption.DATE.name)
    return runCatching { DownloadSortOption.valueOf(saved!!) }.getOrDefault(DownloadSortOption.DATE)
}

fun isDownloadsSortDescending(context: Context): Boolean {
    return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .getBoolean(KEY_DL_DESC, true) // Default true for DATE (recent)
}

fun setDownloadsSortOption(context: Context, option: DownloadSortOption, desc: Boolean) {
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
        .putString(KEY_DL_SORT, option.name)
        .putBoolean(KEY_DL_DESC, desc)
        .apply()
}

private fun SongsModel.toJson(filePath: String): String = JSONObject().apply {
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
    put("filePath", filePath)
}.toString()

private fun parse(json: String): Pair<SongsModel, String>? = runCatching {
    val o = JSONObject(json)
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
    ) to o.optString("filePath")
}.getOrNull()

fun addDownload(context: Context, song: SongsModel, filePath: String) {
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
        .putString(song.id.toString(), song.toJson(filePath))
        .apply()
}

fun removeDownload(context: Context, songId: String) {
    val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    prefs.getString(songId, null)?.let { json ->
        parse(json)?.second?.let { path -> runCatching { File(path).delete() } }
    }
    prefs.edit().remove(songId).apply()
}

fun isDownloaded(context: Context, songId: String): Boolean =
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE).contains(songId)

fun getDownloadedSongs(context: Context): List<SongsModel> {
    val entries = getDownloadedEntries(context)
    val sortOption = getDownloadsSortOption(context)
    val descending = isDownloadsSortDescending(context)
    return when (sortOption) {
        DownloadSortOption.TITLE -> if (descending) entries.map { it.first }.sortedByDescending { it.title.lowercase() } else entries.map { it.first }.sortedBy { it.title.lowercase() }
        DownloadSortOption.ARTIST -> if (descending) entries.map { it.first }.sortedByDescending { it.singer.lowercase() } else entries.map { it.first }.sortedBy { it.singer.lowercase() }
        DownloadSortOption.DATE -> if (descending) entries.sortedByDescending { (_, path) -> File(path).lastModified() }.map { it.first } else entries.sortedBy { (_, path) -> File(path).lastModified() }.map { it.first }
    }
}

/** Local file path for a track query (SongsModel.url), if downloaded and present on disk. */
fun downloadedPathForQuery(context: Context, query: String): String? {
    val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    for (v in prefs.all.values) {
        val (song, path) = (v as? String)?.let(::parse) ?: continue
        if (song.url == query && path.isNotBlank() && File(path).exists()) return path
    }
    return null
}

/** Every downloaded track paired with its on-disk file path. */
fun getDownloadedEntries(context: Context): List<Pair<SongsModel, String>> =
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .all.values.mapNotNull { (it as? String)?.let(::parse) }

/** Delete every downloaded file and forget all download entries. Returns count removed. */
fun clearAllDownloads(context: Context): Int {
    val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    val entries = getDownloadedEntries(context)
    entries.forEach { (_, path) -> if (path.isNotBlank()) runCatching { File(path).delete() } }
    val currentSort = getDownloadsSortOption(context)
    val currentDesc = isDownloadsSortDescending(context)
    prefs.edit().clear().apply()
    setDownloadsSortOption(context, currentSort, currentDesc)
    OfflineCollectionsPref.clearAll(context)
    return entries.size
}

private fun sanitizeFileName(name: String): String =
    name.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim().take(120).ifBlank { "track" }

/**
 * Copy every downloaded track out of the app's private storage into the shared
 * **Music/spotui** folder as `Artist - Title.<ext>`, so files show up in normal
 * file managers / music apps (no root needed). Uses MediaStore on API 29+.
 * Returns (exportedCount, destinationLabel).
 */
fun exportDownloads(context: Context): Pair<Int, String> {
    val entries = getDownloadedEntries(context).filter { it.second.isNotBlank() && File(it.second).exists() }
    if (entries.isEmpty()) return 0 to "No downloaded files to export"
    val count = entries.count { (song, path) -> exportFile(context, song, path) }
    return count to "Music/spotui"
}

/** Export a single downloaded track to public Music/spotui. Returns true on success. */
fun exportDownload(context: Context, song: SongsModel): Boolean {
    val path = getDownloadedEntries(context).firstOrNull { it.first.id == song.id }?.second
        ?.takeIf { it.isNotBlank() && File(it).exists() } ?: return false
    return exportFile(context, song, path)
}

/** Copy one private download file into shared Music/spotui as "Artist - Title.<ext>". */
private fun exportFile(context: Context, song: SongsModel, path: String): Boolean = runCatching {
    val src = File(path)
    if (!src.exists()) return false
    val ext = src.extension.ifBlank { "flac" }
    val mime = when (ext.lowercase()) {
        "flac" -> "audio/flac"
        "m4a", "mp4" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        else -> "audio/*"
    }
    val displayName = "${sanitizeFileName("${song.singer} - ${song.title}")}.$ext"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/spotui")
        }
        val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false
        context.contentResolver.openOutputStream(uri)?.use { out ->
            src.inputStream().use { it.copyTo(out) }
        } ?: return false
    } else {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "spotui",
        ).apply { mkdirs() }
        src.inputStream().use { input -> File(dir, displayName).outputStream().use { input.copyTo(it) } }
    }
    true
}.getOrDefault(false)
