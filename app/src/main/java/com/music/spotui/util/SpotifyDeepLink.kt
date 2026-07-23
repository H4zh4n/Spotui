package com.music.spotui.util

import android.content.Intent
import android.net.Uri

/**
 * Parses Spotify deep links (web URLs and custom spotify: URIs) into structured targets.
 */
sealed class SpotifyDeepLink {
    data class Track(val id: String) : SpotifyDeepLink()
    data class Playlist(val id: String) : SpotifyDeepLink()
    data class Album(val id: String) : SpotifyDeepLink()
    data class Artist(val id: String) : SpotifyDeepLink()
    data class Show(val id: String) : SpotifyDeepLink()
    data class Episode(val id: String) : SpotifyDeepLink()

    companion object {
        fun parse(intent: Intent?): SpotifyDeepLink? {
            val uri = intent?.data ?: return null
            return parse(uri)
        }

        fun parse(uri: Uri): SpotifyDeepLink? {
            return parseUrlString(uri.toString())
        }

        fun parseUrlString(urlString: String?): SpotifyDeepLink? {
            if (urlString.isNullOrBlank()) return null

            val cleanedUrl = urlString.trim()
            val lower = cleanedUrl.lowercase()

            // Handle spotify: URIs e.g., spotify:track:4uLU6hMCjMI75M1A2tKUQC
            if (lower.startsWith("spotify:")) {
                val ssp = cleanedUrl.substringAfter("spotify:").substringBefore('?').trim('/')
                val parts = ssp.split(":").filter { it.isNotBlank() }
                return parseSegments(parts)
            }

            // Handle HTTP / HTTPS URLs e.g. https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC
            if (lower.startsWith("http://") || lower.startsWith("https://")) {
                val noQuery = cleanedUrl.substringBefore('?').substringBefore('#')
                val schemeEnd = noQuery.indexOf("://")
                if (schemeEnd == -1) return null

                val afterScheme = noQuery.substring(schemeEnd + 3)
                val firstSlash = afterScheme.indexOf('/')
                if (firstSlash == -1) return null

                val host = afterScheme.substring(0, firstSlash).lowercase()
                val validHosts = setOf(
                    "open.spotify.com",
                    "spotify.com",
                    "www.spotify.com",
                    "spotify.link",
                    "spotify.app.link",
                    "spotify-alternate.app.link",
                    "spotify.test-app.link",
                    "spotify-alternate.test-app.link"
                )
                if (host !in validHosts) {
                    return null
                }

                val path = afterScheme.substring(firstSlash).trim('/')
                val segments = path.split("/").filter { it.isNotBlank() }
                return parseSegments(segments)
            }

            return null
        }

        private fun parseSegments(rawSegments: List<String>): SpotifyDeepLink? {
            val segments = rawSegments.filterNot { it.lowercase().startsWith("intl-") }
            if (segments.size < 2) return null

            return when {
                segments.size >= 2 && segments[0].equals("track", ignoreCase = true) -> {
                    SpotifyDeepLink.Track(segments[1])
                }
                segments.size >= 4 && segments[0].equals("user", ignoreCase = true) && segments[2].equals("playlist", ignoreCase = true) -> {
                    SpotifyDeepLink.Playlist(segments[3])
                }
                segments.size >= 2 && segments[0].equals("playlist", ignoreCase = true) -> {
                    SpotifyDeepLink.Playlist(segments[1])
                }
                segments.size >= 2 && segments[0].equals("album", ignoreCase = true) -> {
                    SpotifyDeepLink.Album(segments[1])
                }
                segments.size >= 2 && segments[0].equals("artist", ignoreCase = true) -> {
                    SpotifyDeepLink.Artist(segments[1])
                }
                segments.size >= 2 && segments[0].equals("show", ignoreCase = true) -> {
                    SpotifyDeepLink.Show(segments[1])
                }
                segments.size >= 2 && segments[0].equals("episode", ignoreCase = true) -> {
                    SpotifyDeepLink.Episode(segments[1])
                }
                else -> null
            }
        }
    }
}
