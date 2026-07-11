package com.music.spotui

import android.app.Application
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeLocale
import com.metrolist.music.utils.cipher.CipherDeobfuscator
import com.music.spotui.data.api.Api
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Locale

@HiltAndroidApp
class MyApplication : Application(){
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        // For non-DI singletons (LyricsApi) that need a Context to refresh the token.
        @JvmStatic
        lateinit var instance: MyApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Surface Spotify REST/GQL logs to logcat for diagnosis.
        com.metrolist.spotify.Spotify.logger = { level, msg ->
            android.util.Log.d("SpotifyREST", "[$level] $msg")
        }
        com.metrolist.spotify.SpotifyCanvas.setLogger { level, msg ->
            android.util.Log.d("SpotifyCanvas", "[$level] $msg")
        }
        // Required by the ported YouTube streaming flow (cipher/PoToken WebViews).
        CipherDeobfuscator.initialize(this)

        // Locale + visitorData must be set or the player can't mint a PoToken,
        // and googlevideo rejects the stream URL with HTTP 403 (tracks stuck at 0:00).
        val locale = Locale.getDefault()
        YouTube.locale = YouTubeLocale(
            gl = locale.country.takeIf { it.isNotBlank() } ?: "US",
            hl = locale.language.takeIf { it.isNotBlank() } ?: "en",
        )
        appScope.launch {
            YouTube.visitorData = YouTube.visitorData().getOrNull() ?: YouTube.visitorData
        }
        // YouTube playback runs anonymously; age-gated official audio falls back
        // to matching normal YouTube uploads instead of requiring sign-in.
        YouTube.cookie = null

        // Warm the Home feed cache so the first navigation to Home is instant.
        // No-op (gracefully) until a Spotify token is available.
        val api = Api(this)
        appScope.launch { runCatching { api.getHomeFeed().collect {} } }
        appScope.launch { runCatching { api.getAlbums().collect {} } }
        appScope.launch { runCatching { api.getArtists().collect {} } }
        // Pre-warm the YouTube playback pipeline so the first tap doesn't pay for
        // cold-starting the player.js parser and BotGuard WebView (~2-4s combined).
        appScope.launch {
            runCatching { com.metrolist.innertube.NewPipeExtractor.init() }
        }
    }
}
