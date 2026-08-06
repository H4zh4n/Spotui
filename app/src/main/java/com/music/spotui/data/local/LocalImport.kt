package com.music.spotui.data.local

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.music.spotui.data.preferences.LocalTrack
import com.music.spotui.data.preferences.localTrackId
import java.io.File

/**
 * Reads user-picked audio files (via the Storage Access Framework) into
 * [LocalTrack]s: takes a persistable read grant so the URIs survive restarts,
 * pulls tag metadata + embedded art with [MediaMetadataRetriever], and (for a
 * folder) walks the tree for supported audio files.
 */
object LocalImport {

    private const val TAG = "LocalImport"

    // Container extensions ExoPlayer's default extractors can play.
    private val AUDIO_EXTS = setOf(
        "flac", "mp3", "wav", "m4a", "aac", "ogg", "oga", "opus", "wma",
        "aiff", "aif", "mka", "mp4", "3gp", "mid", "amr",
    )

    /** Import individually-picked files. */
    fun importFiles(context: Context, uris: List<Uri>): List<LocalTrack> =
        uris.mapNotNull { uri ->
            takePersistable(context, uri)
            extract(context, uri, displayName = fileNameFor(context, uri))
        }

    /** Import every supported audio file under a picked folder (recursively). */
    fun importFolder(context: Context, treeUri: Uri): List<LocalTrack> {
        takePersistable(context, treeUri)
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val files = ArrayList<DocumentFile>()
        walk(root, files)
        return files.mapNotNull { doc ->
            extract(context, doc.uri, displayName = doc.name ?: "")
        }
    }

    private fun walk(dir: DocumentFile, out: MutableList<DocumentFile>) {
        for (f in dir.listFiles()) {
            if (f.isDirectory) {
                walk(f, out)
            } else {
                val name = f.name ?: continue
                if (name.substringAfterLast('.', "").lowercase() in AUDIO_EXTS) out.add(f)
            }
        }
    }

    private fun takePersistable(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onFailure { Log.w(TAG, "couldn't persist permission for $uri: ${it.message}") }
    }

    private fun extract(context: Context, uri: Uri, displayName: String): LocalTrack? {
        val id = localTrackId(uri.toString())
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, uri)
            val fallbackTitle = displayName.substringBeforeLast('.', displayName).ifBlank { "Unknown title" }
            val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() } ?: fallbackTitle
            val artist = (mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST))
                ?.takeIf { it.isNotBlank() } ?: "Unknown artist"
            val album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty()
            val duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toIntOrNull() ?: 0
            val cover = saveEmbeddedArt(context, mmr.embeddedPicture, id)
            LocalTrack(
                id = id,
                uri = uri.toString(),
                title = title,
                artist = artist,
                album = album,
                durationMs = duration,
                coverUri = cover,
            )
        } catch (e: Exception) {
            Log.w(TAG, "skipping unreadable file $uri: ${e.message}")
            // Still import it (ExoPlayer may play it); metadata just falls back to the name.
            LocalTrack(
                id = id,
                uri = uri.toString(),
                title = displayName.substringBeforeLast('.', displayName).ifBlank { "Unknown title" },
                artist = "Unknown artist",
                album = "",
                durationMs = 0,
                coverUri = "",
            )
        } finally {
            runCatching { mmr.release() }
        }
    }

    /** Cache embedded cover art to a file and return its URI string, or "" if none. */
    private fun saveEmbeddedArt(context: Context, art: ByteArray?, id: Int): String {
        if (art == null || art.isEmpty()) return ""
        return runCatching {
            val dir = File(context.cacheDir, "localart").apply { mkdirs() }
            val f = File(dir, "$id.jpg")
            f.writeBytes(art)
            Uri.fromFile(f).toString()
        }.getOrDefault("")
    }

    private fun fileNameFor(context: Context, uri: Uri): String =
        runCatching { DocumentFile.fromSingleUri(context, uri)?.name }.getOrNull()
            ?: uri.lastPathSegment?.substringAfterLast('/').orEmpty()
}
