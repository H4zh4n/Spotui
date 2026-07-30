package com.music.spotui.audio

import com.music.spotui.providers.DeezerAudioProvider
import com.music.spotui.providers.QobuzAudioProvider
import com.music.spotui.providers.SoundCloudAudioProvider
import com.music.spotui.providers.TidalAudioProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object ProviderHealthChecker {
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    enum class Status {
        ONLINE,
        REACHABLE,
        OFFLINE,
    }

    data class Target(
        val id: String,
        val group: String,
        val name: String,
        val endpoint: String,
        val detail: String,
    )

    data class Result(
        val target: Target,
        val status: Status,
        val latencyMs: Long?,
        val message: String,
    )

    fun defaultTargets(): List<Target> {
        return listOf(
            Target(
                id = "amazon_resolver",
                group = "Amazon Music",
                name = "Amazon Music Resolver",
                endpoint = com.music.spotui.providers.AmazonAudioProvider.DEFAULT_RESOLVE_API_URL,
                detail = "24-bit Ultra HD & 16-bit FLAC Stream Resolver",
            ),
            Target(
                id = "qobuz_kenny",
                group = "Qobuz",
                name = "Kenny Mirror",
                endpoint = QobuzAudioProvider.DEFAULT_KENNY_BASE_URL,
                detail = "24-bit Hi-Res & 16-bit FLAC Stream Resolver",
            ),
            Target(
                id = "tidal_bini",
                group = "TIDAL",
                name = "Bini Hi-Fi API",
                endpoint = TidalAudioProvider.DEFAULT_RESOLVER_BASE_URL,
                detail = "24-bit Hi-Res & 16-bit FLAC Stream Resolver",
            ),
            Target(
                id = "deezer_resolver",
                group = "Deezer",
                name = "Deezer API Resolver",
                endpoint = DeezerAudioProvider.DEFAULT_RESOLVER_BASE_URL,
                detail = "16-bit CD Quality FLAC Stream Resolver",
            ),
            Target(
                id = "soundcloud_api",
                group = "SoundCloud",
                name = "SoundCloud Web API",
                endpoint = "https://api-v2.soundcloud.com",
                detail = "SoundCloud HLS & Progressive Stream Resolver",
            ),
            Target(
                id = "spotiflac_proxy",
                group = "SpotiFLAC",
                name = "Community Proxy (spotbye.qzz.io)",
                endpoint = "https://spotbye.qzz.io/api/status",
                detail = "Community FLAC Proxy Status Endpoint",
            ),
            Target(
                id = "youtube_music",
                group = "YouTube Music",
                name = "YouTube Music",
                endpoint = "https://music.youtube.com/",
                detail = "Fallback Web Stream & Playback Metadata",
            ),
        )
    }

    suspend fun checkAll(targets: List<Target> = defaultTargets()): List<Result> =
        withContext(Dispatchers.IO) {
            coroutineScope {
                targets.map { target ->
                    async { checkTarget(target) }
                }.awaitAll()
            }
        }

    private fun checkTarget(target: Target): Result {
        val startTime = System.currentTimeMillis()
        val request = Request.Builder()
            .url(target.endpoint)
            .get()
            .header("User-Agent", USER_AGENT)
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                val elapsed = System.currentTimeMillis() - startTime
                if (response.isSuccessful || response.code == 404 || response.code == 401 || response.code == 403) {
                    Result(
                        target = target,
                        status = if (response.isSuccessful) Status.ONLINE else Status.REACHABLE,
                        latencyMs = elapsed,
                        message = "HTTP ${response.code}",
                    )
                } else {
                    Result(
                        target = target,
                        status = Status.OFFLINE,
                        latencyMs = elapsed,
                        message = "HTTP ${response.code}",
                    )
                }
            }
        }.getOrElse { error ->
            Result(
                target = target,
                status = Status.OFFLINE,
                latencyMs = null,
                message = error.message ?: "Connection error",
            )
        }
    }
}
