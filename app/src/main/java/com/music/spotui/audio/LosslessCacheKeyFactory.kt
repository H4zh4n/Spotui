package com.music.spotui.audio

import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheKeyFactory

object LosslessCacheKeyFactory : CacheKeyFactory {
    private const val SPOTIFY_PREFIX = "spotify:track:"

    fun buildCacheKey(spotifyTrackId: String?, url: String): String {
        val uri = Uri.parse(url)
        val host = uri.host.orEmpty()
        val path = uri.path.orEmpty()

        // YouTube dynamic stream URLs: must NOT use fixed spotui-flac cache keys,
        // because dynamic URLs for YouTube change and expire across resolutions.
        if (host.contains("googlevideo.com") || host.contains("youtube.com")) {
            val videoId = uri.getQueryParameter("docid") ?: uri.getQueryParameter("id")
            return if (!videoId.isNullOrBlank()) {
                "spotui-yt:$videoId:$path"
            } else {
                "spotui-yt-path:$path"
            }
        }

        val cleanId = spotifyTrackId?.removePrefix(SPOTIFY_PREFIX)?.substringBefore('|')?.trim()
        if (!cleanId.isNullOrBlank()) {
            return "spotui-flac:$cleanId"
        }

        val scheme = uri.scheme
        return if (scheme != null && host.isNotBlank()) {
            "spotui-uri:$scheme://$host$path"
        } else {
            "spotui-raw:$url"
        }
    }

    override fun buildCacheKey(dataSpec: DataSpec): String {
        dataSpec.key?.takeIf { it.isNotBlank() }?.let { return it }
        return buildCacheKey(null, dataSpec.uri.toString())
    }
}
