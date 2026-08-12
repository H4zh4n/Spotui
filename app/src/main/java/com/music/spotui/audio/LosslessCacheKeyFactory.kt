package com.music.spotui.audio

import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheKeyFactory

object LosslessCacheKeyFactory : CacheKeyFactory {
    private const val SPOTIFY_PREFIX = "spotify:track:"

    fun buildCacheKey(spotifyTrackId: String?, url: String): String {
        val cleanId = spotifyTrackId?.removePrefix(SPOTIFY_PREFIX)?.substringBefore('|')?.trim()
        if (!cleanId.isNullOrBlank()) {
            return "spotui-flac:$cleanId"
        }

        val uri = Uri.parse(url)
        val host = uri.host.orEmpty()
        val path = uri.path.orEmpty()

        if (host.contains("googlevideo.com") || host.contains("youtube.com")) {
            val videoId = uri.getQueryParameter("docid")
                ?: uri.getQueryParameter("id")
                ?: uri.getQueryParameter("v")
            if (!videoId.isNullOrBlank()) {
                return "spotui-yt:$videoId:$path"
            }
        }

        val urlHash = java.util.zip.CRC32().apply { update(url.toByteArray()) }.value
        return "spotui-raw:$urlHash"
    }

    override fun buildCacheKey(dataSpec: DataSpec): String {
        dataSpec.key?.takeIf { it.isNotBlank() }?.let { return it }
        return buildCacheKey(null, dataSpec.uri.toString())
    }
}
