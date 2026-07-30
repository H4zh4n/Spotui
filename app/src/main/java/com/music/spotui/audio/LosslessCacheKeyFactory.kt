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
        // Fallback: use bare URL path without query parameters if no track id
        val uri = Uri.parse(url)
        val scheme = uri.scheme
        val host = uri.host.orEmpty()
        val path = uri.path.orEmpty()
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
