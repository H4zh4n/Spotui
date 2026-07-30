package com.music.spotui.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaMetadata
import com.music.spotui.data.preferences.getDownloadedEntries
import java.io.File

object ArtworkHelper {

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

        // 1. Check for a saved local download cover file by songId
        if (!songId.isNullOrBlank()) {
            val dir = File(context.filesDir, "downloads")
            val localCover = File(dir, "${songId}_cover.jpg")
            if (localCover.exists() && localCover.length() > 0) {
                return builder.setArtworkUri(Uri.fromFile(localCover))
            }
            val localCoverAlt = File(dir, "${songId}.jpg")
            if (localCoverAlt.exists() && localCoverAlt.length() > 0) {
                return builder.setArtworkUri(Uri.fromFile(localCoverAlt))
            }
        }

        // 2. Match by streamUrl / query in downloaded entries
        if (cleanStream.isNotBlank()) {
            val dlEntries = getDownloadedEntries(context)
            val matchedEntry = dlEntries.firstOrNull { (song, path) -> song.url == cleanStream || path == cleanStream }
            if (matchedEntry != null) {
                val dir = File(context.filesDir, "downloads")
                val localCover = File(dir, "${matchedEntry.first.id}_cover.jpg")
                if (localCover.exists() && localCover.length() > 0) {
                    return builder.setArtworkUri(Uri.fromFile(localCover))
                }
            }
        }

        // 3. Check if streamUrl is a local audio file with embedded cover art
        if (cleanStream.isNotBlank() && (cleanStream.startsWith("/") || cleanStream.startsWith("file:") || cleanStream.startsWith("content:"))) {
            val retriever = MediaMetadataRetriever()
            try {
                if (cleanStream.startsWith("content:")) {
                    retriever.setDataSource(context, Uri.parse(cleanStream))
                } else {
                    val path = if (cleanStream.startsWith("file://")) Uri.parse(cleanStream).path ?: cleanStream else cleanStream
                    retriever.setDataSource(path)
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

        // 5. Check Glide disk cache without network access (onlyRetrieveFromCache = true)
        if (cleanCover.isNotBlank()) {
            val cachedFile = runCatching {
                com.bumptech.glide.Glide.with(context.applicationContext)
                    .downloadOnly()
                    .load(cleanCover)
                    .onlyRetrieveFromCache(true)
                    .submit()
                    .get()
            }.getOrNull()

            if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0) {
                return builder.setArtworkUri(Uri.fromFile(cachedFile))
            }

            // 6. Fallback to remote URI (used when online)
            return builder.setArtworkUri(Uri.parse(cleanCover))
        }

        return builder
    }
}
