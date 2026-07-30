package com.music.spotui.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Looper
import androidx.media3.common.MediaMetadata
import com.music.spotui.data.preferences.getDownloadedEntries
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

object ArtworkHelper {

    private fun cleanSongId(songId: String?): String? =
        songId?.removePrefix("song/")?.removePrefix("playlist/")?.removePrefix("album/")?.trim()?.takeIf { it.isNotBlank() }

    /**
     * Attaches the best available artwork (local downloaded cover file, embedded ID3 picture,
     * Glide disk cache, or remote URI) to a [MediaMetadata.Builder] for system notifications.
     */
    fun attachArtwork(
        builder: MediaMetadata.Builder,
        context: Context,
        coverUri: String,
        songId: String? = null,
        streamUrl: String? = null
    ): MediaMetadata.Builder {
        val cleanCover = coverUri.trim()
        val cleanStream = streamUrl?.trim().orEmpty()
        val rawPath = if (cleanStream.startsWith("file://")) Uri.parse(cleanStream).path.orEmpty() else cleanStream
        val rawId = cleanSongId(songId)

        val dir = File(context.filesDir, "downloads")

        // 1. Check for a saved local download cover file by songId
        if (!rawId.isNullOrBlank()) {
            val localCover = File(dir, "${rawId}_cover.jpg")
            if (localCover.exists() && localCover.length() > 0) {
                return builder.setArtworkUri(Uri.fromFile(localCover))
            }
            val localCoverAlt = File(dir, "${rawId}.jpg")
            if (localCoverAlt.exists() && localCoverAlt.length() > 0) {
                return builder.setArtworkUri(Uri.fromFile(localCoverAlt))
            }
        }

        // 2. Match by streamUrl / query / path in downloaded entries
        if (cleanStream.isNotBlank()) {
            val dlEntries = runCatching { getDownloadedEntries(context) }.getOrDefault(emptyList())
            val matchedEntry = dlEntries.firstOrNull { (song, path) ->
                song.url == cleanStream || path == cleanStream || (rawPath.isNotBlank() && path == rawPath)
            }
            if (matchedEntry != null) {
                val localCover = File(dir, "${matchedEntry.first.id}_cover.jpg")
                if (localCover.exists() && localCover.length() > 0) {
                    return builder.setArtworkUri(Uri.fromFile(localCover))
                }
                // Try eager cover cache for this downloaded song if not present
                ensureDownloadedCover(context, matchedEntry.first)
            }
        }

        // 3. Check if streamUrl is a local audio file with embedded cover art
        if (rawPath.isNotBlank() && (rawPath.startsWith("/") || cleanStream.startsWith("content:"))) {
            val retriever = MediaMetadataRetriever()
            try {
                if (cleanStream.startsWith("content:")) {
                    retriever.setDataSource(context, Uri.parse(cleanStream))
                } else {
                    retriever.setDataSource(rawPath)
                }
                val embedded = retriever.embeddedPicture
                if (embedded != null && embedded.isNotEmpty()) {
                    return builder.setArtworkData(embedded, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                }
            } catch (e: Exception) {
                // Ignore retriever failure for unparseable streams
            } finally {
                runCatching { retriever.release() }
            }
        }

        // 4. Check if coverUri is already a local file or content URI
        if (cleanCover.startsWith("file:") || cleanCover.startsWith("content:")) {
            return builder.setArtworkUri(Uri.parse(cleanCover))
        }

        // 5. Check Glide disk cache safely (Glide submit().get() cannot run on main thread)
        if (cleanCover.isNotBlank()) {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                val cachedFile = runCatching {
                    com.bumptech.glide.Glide.with(context.applicationContext)
                        .downloadOnly()
                        .load(cleanCover)
                        .onlyRetrieveFromCache(true)
                        .submit()
                        .get()
                }.getOrNull()

                if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0) {
                    if (!rawId.isNullOrBlank()) {
                        val dest = File(dir, "${rawId}_cover.jpg")
                        if (!dest.exists()) runCatching { cachedFile.copyTo(dest, overwrite = true) }
                    }
                    return builder.setArtworkUri(Uri.fromFile(cachedFile))
                }
            }

            // 6. Fallback to remote URI
            return builder.setArtworkUri(Uri.parse(cleanCover))
        }

        return builder
    }

    /** Ensure a downloaded song has its cover image cached/saved locally to disk. */
    fun ensureDownloadedCover(context: Context, song: com.music.spotui.data.entity.SongsModel) {
        if (song.coverUri.isBlank()) return
        val dir = File(context.filesDir, "downloads").apply { mkdirs() }
        val coverFile = File(dir, "${song.id}_cover.jpg")
        if (coverFile.exists() && coverFile.length() > 0) return

        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val file = runCatching {
                com.bumptech.glide.Glide.with(appContext)
                    .downloadOnly()
                    .load(song.coverUri)
                    .submit()
                    .get()
            }.getOrNull()
            if (file != null && file.exists()) {
                runCatching { file.copyTo(coverFile, overwrite = true) }
            } else {
                runCatching {
                    val conn = (java.net.URL(song.coverUri).openConnection() as java.net.HttpURLConnection).apply {
                        connectTimeout = 10000
                        readTimeout = 10000
                        instanceFollowRedirects = true
                    }
                    if (conn.responseCode in 200..299) {
                        conn.inputStream.use { input ->
                            coverFile.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                }
            }
        }
    }
}
