package com.music.spotui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.navigation.NavController
import com.metrolist.spotify.Spotify
import com.music.spotui.data.api.SpotifyTokenProvider
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.navigation.Routes
import com.music.spotui.ui.navigation.albumRoute
import com.music.spotui.ui.navigation.artistRoute
import com.music.spotui.ui.navigation.playlistRoute
import com.music.spotui.ui.navigation.showRoute
import com.music.spotui.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Handles incoming Spotify deep links and routes them to playback or UI navigation.
 */
object DeepLinkHandler {
    private const val TAG = "DeepLinkHandler"

    private val _deepLinkFlow = MutableSharedFlow<Uri>(
        replay = 0,
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val deepLinkFlow = _deepLinkFlow.asSharedFlow()

    @Volatile
    private var pendingUri: Uri? = null

    fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        val scheme = data.scheme?.lowercase()
        val host = data.host?.lowercase()

        val isSpotifyLink = (scheme == "spotify") ||
                (host != null && (host == "open.spotify.com" || host.endsWith(".spotify.com") || host.contains("spotify")))

        if (!isSpotifyLink) return

        Log.d(TAG, "Received deep link intent: $data")
        pendingUri = data
        _deepLinkFlow.tryEmit(data)
    }

    fun consumePendingUri(): Uri? {
        val uri = pendingUri
        pendingUri = null
        return uri
    }

    fun processUri(
        uri: Uri,
        context: Context,
        navController: NavController,
        playerViewModel: PlayerViewModel,
        scope: CoroutineScope
    ) {
        scope.launch(Dispatchers.IO) {
            val targetUri = resolveShortenedUriIfNeeded(uri)
            val link = SpotifyDeepLink.parse(targetUri)
            if (link == null) {
                Log.w(TAG, "Unrecognized Spotify deep link: $targetUri")
                return@launch
            }

            Log.d(TAG, "Processing deep link: $link")

            when (link) {
                is SpotifyDeepLink.Track -> {
                    val song = resolveTrackInfo(link.id, context)
                    withContext(Dispatchers.Main) {
                        playerViewModel.updateQueue(listOf(song))
                        playerViewModel.updateSongState(
                            song.coverUri, song.title, song.singer, true, song.id, 0, song.album
                        )
                        SongPlayer.invalidateResolvedStream(song.url)
                        SongPlayer.playSong(song.url, context)
                        navigateToPlayer(navController)
                    }
                }

                is SpotifyDeepLink.Playlist -> {
                    withContext(Dispatchers.Main) {
                        navController.navigate(playlistRoute(link.id)) {
                            launchSingleTop = true
                        }
                    }
                }

                is SpotifyDeepLink.Album -> {
                    val album = if (SpotifyTokenProvider.ensureToken(context)) {
                        runCatching { Spotify.album(link.id).getOrNull() }.getOrNull()
                    } else null

                    val albumName: String
                    val artistName: String
                    if (album != null) {
                        albumName = album.name
                        artistName = album.artists.firstOrNull()?.name.orEmpty()
                    } else {
                        val webMeta = fetchSpotifyEmbedMeta("album", link.id)
                        if (webMeta != null) {
                            albumName = webMeta.title
                            artistName = webMeta.artist
                        } else {
                            val oembed = fetchOEmbedMetadata("https://open.spotify.com/album/${link.id}")
                            albumName = oembed?.title?.ifBlank { null } ?: link.id
                            artistName = oembed?.authorName.orEmpty()
                        }
                    }

                    withContext(Dispatchers.Main) {
                        navController.navigate(albumRoute(albumName, artistName)) {
                            launchSingleTop = true
                        }
                    }
                }

                is SpotifyDeepLink.Artist -> {
                    val artist = if (SpotifyTokenProvider.ensureToken(context)) {
                        runCatching { Spotify.artist(link.id).getOrNull() }.getOrNull()
                    } else null

                    val artistName = if (artist != null) {
                        artist.name
                    } else {
                        val webMeta = fetchSpotifyEmbedMeta("artist", link.id)
                        if (webMeta != null && webMeta.title.isNotBlank()) {
                            webMeta.title
                        } else {
                            val oembed = fetchOEmbedMetadata("https://open.spotify.com/artist/${link.id}")
                            oembed?.title?.ifBlank { null } ?: "Artist"
                        }
                    }

                    withContext(Dispatchers.Main) {
                        navController.navigate(artistRoute(artistName, link.id)) {
                            launchSingleTop = true
                        }
                    }
                }

                is SpotifyDeepLink.Show -> {
                    val show = if (SpotifyTokenProvider.ensureToken(context)) {
                        runCatching { Spotify.show(link.id).getOrNull() }.getOrNull()
                    } else null

                    val showName = if (show != null) {
                        show.name
                    } else {
                        val webMeta = fetchSpotifyEmbedMeta("show", link.id)
                        if (webMeta != null && webMeta.title.isNotBlank()) {
                            webMeta.title
                        } else {
                            val oembed = fetchOEmbedMetadata("https://open.spotify.com/show/${link.id}")
                            oembed?.title.orEmpty()
                        }
                    }

                    withContext(Dispatchers.Main) {
                        navController.navigate(showRoute(link.id, showName)) {
                            launchSingleTop = true
                        }
                    }
                }

                is SpotifyDeepLink.Episode -> {
                    val song = resolveEpisodeInfo(link.id, context)
                    withContext(Dispatchers.Main) {
                        playerViewModel.updateQueue(listOf(song))
                        playerViewModel.updateSongState(
                            song.coverUri, song.title, song.singer, true, song.id, 0, song.album
                        )
                        SongPlayer.invalidateResolvedStream(song.url)
                        SongPlayer.playSong(song.url, context)
                        navigateToPlayer(navController)
                    }
                }
            }
        }
    }

    private fun navigateToPlayer(navController: NavController) {
        if (navController.currentDestination?.route != Routes.Player.route) {
            navController.navigate(Routes.Player.route) {
                launchSingleTop = true
            }
        }
    }

    private suspend fun resolveTrackInfo(trackId: String, context: Context): SongsModel {
        if (SpotifyTokenProvider.ensureToken(context)) {
            val track = runCatching { Spotify.track(trackId).getOrNull() }.getOrNull()
            if (track != null) {
                val singer = track.artists.joinToString(", ") { it.name }
                val cover = track.album?.images?.firstOrNull()?.url ?: ""
                return SongsModel(
                    id = (("track:" + trackId).hashCode() and 0x7fffffff),
                    title = track.name.take(128),
                    album = track.album?.name ?: "",
                    singer = singer,
                    coverUri = cover,
                    url = SongPlayer.buildSpotifyPlayQuery(trackId, track.name, singer),
                    spotifyTrackId = trackId,
                    explicit = track.explicit,
                    durationMs = track.durationMs,
                    artistIds = track.artists.joinToString(",") { it.id.orEmpty() }
                )
            }
        }

        // Web embed metadata resolution (extracts exact track title, artist name, cover art, duration, explicit flag without requiring login)
        val webMeta = fetchSpotifyEmbedMeta("track", trackId)
        if (webMeta != null && webMeta.title.isNotBlank()) {
            return SongsModel(
                id = (("track:" + trackId).hashCode() and 0x7fffffff),
                title = webMeta.title,
                album = "",
                singer = webMeta.artist,
                coverUri = webMeta.coverUri,
                url = SongPlayer.buildSpotifyPlayQuery(trackId, webMeta.title, webMeta.artist),
                spotifyTrackId = trackId,
                explicit = webMeta.explicit,
                durationMs = webMeta.durationMs
            )
        }

        // Public Spotify oEmbed API fallback
        val oembed = fetchOEmbedMetadata("https://open.spotify.com/track/$trackId")
        val title = oembed?.title?.ifBlank { null } ?: "Track $trackId"
        val singer = oembed?.authorName.orEmpty()
        val cover = oembed?.thumbnailUrl.orEmpty()

        return SongsModel(
            id = (("track:" + trackId).hashCode() and 0x7fffffff),
            title = title,
            album = "",
            singer = singer,
            coverUri = cover,
            url = SongPlayer.buildSpotifyPlayQuery(trackId, title, singer),
            spotifyTrackId = trackId
        )
    }

    private fun resolveEpisodeInfo(episodeId: String, context: Context): SongsModel {
        val webMeta = fetchSpotifyEmbedMeta("episode", episodeId)
        val title = webMeta?.title?.ifBlank { null } ?: "Podcast Episode"
        val singer = webMeta?.artist.orEmpty()
        val cover = webMeta?.coverUri.orEmpty()

        return SongsModel(
            id = (("episode:$episodeId").hashCode() and 0x7fffffff),
            title = title,
            album = "Podcast",
            singer = singer,
            coverUri = cover,
            url = "episode:$episodeId",
            spotifyTrackId = episodeId,
            durationMs = webMeta?.durationMs ?: 0
        )
    }

    private data class SpotifyWebMeta(
        val title: String,
        val artist: String,
        val coverUri: String,
        val durationMs: Int = 0,
        val explicit: Boolean = false
    )

    private fun fetchSpotifyEmbedMeta(type: String, id: String): SpotifyWebMeta? {
        return try {
            val url = "https://open.spotify.com/embed/$type/$id"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")

            if (conn.responseCode == 200) {
                val html = conn.inputStream.bufferedReader().use { it.readText() }
                val scriptMatch = Regex("""<script[^>]*id=["']__NEXT_DATA__["'][^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL).find(html)
                if (scriptMatch != null) {
                    val jsonText = scriptMatch.groupValues[1]
                    val root = JSONObject(jsonText)
                    val props = root.optJSONObject("props")
                    val pageProps = props?.optJSONObject("pageProps")
                    val state = pageProps?.optJSONObject("state")
                    val data = state?.optJSONObject("data")
                    val entity = data?.optJSONObject("entity")
                    if (entity != null) {
                        val title = entity.optString("title", "").ifBlank { entity.optString("name", "") }

                        val artistsArray = entity.optJSONArray("artists")
                        val artistList = mutableListOf<String>()
                        if (artistsArray != null) {
                            for (i in 0 until artistsArray.length()) {
                                val aObj = artistsArray.optJSONObject(i)
                                val name = aObj?.optString("name", "")
                                if (!name.isNullOrBlank()) artistList.add(name)
                            }
                        }
                        val artist = artistList.joinToString(", ")
                        val durationMs = entity.optInt("duration", 0)
                        val isExplicit = entity.optBoolean("isExplicit", false)

                        var coverUri = ""
                        val visualIdentity = entity.optJSONObject("visualIdentity")
                        val images = visualIdentity?.optJSONArray("image")
                        if (images != null && images.length() > 0) {
                            coverUri = images.optJSONObject(0)?.optString("url", "").orEmpty()
                        }
                        if (coverUri.isBlank()) {
                            val coverArt = entity.optJSONObject("coverArt")
                            val sources = coverArt?.optJSONArray("sources")
                            if (sources != null && sources.length() > 0) {
                                coverUri = sources.optJSONObject(0)?.optString("url", "").orEmpty()
                            }
                        }

                        if (title.isNotBlank()) {
                            return SpotifyWebMeta(
                                title = title,
                                artist = artist,
                                coverUri = coverUri,
                                durationMs = durationMs,
                                explicit = isExplicit
                            )
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch Spotify embed meta for $type/$id", e)
            null
        }
    }

    private data class OEmbedMetadata(
        val title: String,
        val authorName: String,
        val thumbnailUrl: String
    )

    private fun fetchOEmbedMetadata(spotifyUrl: String): OEmbedMetadata? {
        return try {
            val encodedUrl = URLEncoder.encode(spotifyUrl, "UTF-8")
            val oembedEndpoint = "https://open.spotify.com/oembed?url=$encodedUrl"
            val conn = URL(oembedEndpoint).openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            if (conn.responseCode == 200) {
                val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonText)
                val title = json.optString("title", "").trim()
                val author = json.optString("author_name", "").trim()
                val thumbnail = json.optString("thumbnail_url", "").trim()
                if (title.isNotBlank() || author.isNotBlank()) {
                    OEmbedMetadata(title, author, thumbnail)
                } else null
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch oEmbed metadata for $spotifyUrl", e)
            null
        }
    }

    private fun resolveShortenedUriIfNeeded(uri: Uri): Uri {
        val host = uri.host?.lowercase() ?: return uri
        if (host.contains("link")) {
            try {
                val conn = URL(uri.toString()).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.requestMethod = "HEAD"
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (!location.isNullOrBlank()) {
                    return Uri.parse(location)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to resolve redirect for $uri", e)
            }
        }
        return uri
    }
}
