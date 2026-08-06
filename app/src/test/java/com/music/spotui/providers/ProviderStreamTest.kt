package com.music.spotui.providers

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.net.ConnectivityManager
import com.metrolist.innertube.YouTube
import com.metrolist.music.constants.AudioQuality
import com.metrolist.music.utils.YTPlayerUtils
import com.metrolist.spotify.SpotiFlac
import com.music.spotui.deezer.DeezerSource
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.runBlocking

/**
 * Diagnostic & Integration Test Suite for all Audio Providers in Spotui.
 *
 * Tests resolution and streamability across 10 popular test songs for:
 * 1. Amazon Music (AmazonAudioProvider)
 * 2. Qobuz (QobuzAudioProvider)
 * 3. TIDAL (TidalAudioProvider)
 * 4. Deezer Mirror (DeezerAudioProvider)
 * 5. Deezer Direct (DeezerSource)
 * 6. SpotiFLAC Community Proxy (SpotiFlac)
 * 7. SoundCloud (SoundCloudAudioProvider)
 * 8. YouTube Music (YouTube Innertube + YTPlayerUtils)
 *
 * Command to run this test suite on demand:
 * .\gradlew.bat testDebugUnitTest --tests "com.music.spotui.providers.ProviderStreamTest"
 */
class ProviderStreamTest {

    data class TestSong(
        val title: String,
        val artist: String,
        val album: String,
        val spotifyId: String,
        val isrc: String,
        val durationMs: Long
    )

    data class ProviderResult(
        val providerName: String,
        val resolved: Boolean,
        val streamable: Boolean,
        val qualityOrDetails: String,
        val latencyMs: Long,
        val note: String = ""
    )

    private val testSongs = listOf(
        TestSong("Blinding Lights", "The Weeknd", "After Hours", "0VjIjW4GlUZAMYd2vXMi3b", "USUG11904206", 200040L),
        TestSong("Shape of You", "Ed Sheeran", "÷ (Deluxe)", "7qiZf249yMsMCwbStz1vcc", "GBAHS1600463", 233712L),
        TestSong("Bohemian Rhapsody", "Queen", "A Night At The Opera", "7tF18EA8yLwFi2C2zWvJxF", "GBAYE6700035", 354947L),
        TestSong("Billie Jean", "Michael Jackson", "Thriller", "5Qb1MhLhvdKeq4iZpC4B0e", "USSM18200021", 294000L),
        TestSong("Smells Like Teen Spirit", "Nirvana", "Nevermind", "5810g2Gfl2iV2RjA5666Kb", "USDG19100085", 301240L),
        TestSong("As It Was", "Harry Styles", "Harry's House", "4DSuw0g3u9x42t8wFSt75T", "USSM12200629", 167303L),
        TestSong("Hotel California", "Eagles", "Hotel California", "40riA5V2Wfk2hKA1wKf1i3", "USEE17600008", 391373L),
        TestSong("Levitating", "Dua Lipa", "Future Nostalgia", "463SpQg9Lx5vvy7306Y9L3", "GBK3W2000346", 203807L),
        TestSong("STAY", "The Kid LAROI, Justin Bieber", "F*CK LOVE 3: OVER YOU", "5HCyWikKppZ13Bpe8vPDK2", "USSM12104523", 141800L),
        TestSong("Rolling in the Deep", "Adele", "21", "1r8L9v9o49z8n81V06wV7t", "GBBKS1000318", 228093L)
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(7, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val dummyContext: Context = FakeContext()

    private inline fun <T> runWithTimeout(timeoutMs: Long, crossinline block: suspend () -> T): T? {
        val executor = Executors.newSingleThreadExecutor()
        val future = executor.submit(Callable {
            runBlocking { block() }
        })
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            null
        } catch (e: Exception) {
            future.cancel(true)
            throw e.cause ?: e
        } finally {
            executor.shutdownNow()
        }
    }

    private fun checkStreamUrl(url: String): Pair<Boolean, String> {
        if (url.isBlank()) return Pair(false, "Empty URL")
        if (url.startsWith("deezer://")) return Pair(true, "Internal Deezer Decrypt Schema")

        return try {
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .addHeader("Range", "bytes=0-1023")
                .get()
                .build()

            httpClient.newCall(req).execute().use { response ->
                val code = response.code
                val isSuccess = code in 200..299 || code in 300..308
                val body = response.body
                val contentLength = body?.contentLength() ?: 0L
                val bytesRead = body?.byteStream()?.readNBytes(512)?.size ?: 0
                if (isSuccess && (bytesRead > 0 || contentLength > 0)) {
                    Pair(true, "HTTP $code (read $bytesRead bytes)")
                } else {
                    Pair(false, "HTTP $code (bytes=$bytesRead, clen=$contentLength)")
                }
            }
        } catch (e: Exception) {
            Pair(false, "${e.javaClass.simpleName}: ${e.message?.take(100)}")
        }
    }

    @Test
    fun testAllProvidersOn10Songs() = runBlocking {
        println("\n====================================================================================================")
        println("                           SPOTUI AUDIO PROVIDERS STREAMABILITY TEST")
        println("====================================================================================================\n")

        val providerStats = mutableMapOf<String, Pair<Int, Int>>() // Provider -> (Resolved, Streamable)

        for ((index, song) in testSongs.withIndex()) {
            println("----------------------------------------------------------------------------------------------------")
            println("Song #${index + 1}: ${song.artist} - ${song.title} [ISRC: ${song.isrc} | ID: ${song.spotifyId}]")
            println("----------------------------------------------------------------------------------------------------")

            val results = mutableListOf<ProviderResult>()

            // 1. Amazon Music
            val amzStart = System.currentTimeMillis()
            try {
                val amzRes = runWithTimeout(6000L) {
                    AmazonAudioProvider.resolve(
                        dummyContext,
                        AmazonAudioProvider.Query(
                            mediaId = song.spotifyId,
                            title = song.title,
                            artists = listOf(song.artist),
                            album = song.album,
                            durationMs = song.durationMs
                        )
                    )
                }
                val latency = System.currentTimeMillis() - amzStart
                if (amzRes != null) {
                    val (streamable, note) = checkStreamUrl(amzRes.mediaUri)
                    results.add(ProviderResult("Amazon Music", true, streamable, "${amzRes.codecOrFormat()} (${amzRes.bitrate}kbps)", latency, note))
                } else {
                    results.add(ProviderResult("Amazon Music", false, false, "-", latency, "Timed out / Not found"))
                }
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - amzStart
                results.add(ProviderResult("Amazon Music", false, false, "-", latency, e.message?.take(100) ?: "Failed"))
            }

            // 2. Qobuz
            val qobStart = System.currentTimeMillis()
            try {
                val qobRes = runWithTimeout(6000L) {
                    QobuzAudioProvider.resolve(
                        QobuzAudioProvider.Query(
                            mediaId = song.spotifyId,
                            title = song.title,
                            artists = listOf(song.artist),
                            album = song.album,
                            isrc = song.isrc,
                            durationMs = song.durationMs
                        ),
                        preferHiRes = true
                    )
                }
                val latency = System.currentTimeMillis() - qobStart
                if (qobRes != null) {
                    val (streamable, note) = checkStreamUrl(qobRes.mediaUri)
                    results.add(ProviderResult("Qobuz", true, streamable, "FLAC ${qobRes.sampleRate ?: 44100}Hz", latency, note))
                } else {
                    results.add(ProviderResult("Qobuz", false, false, "-", latency, "Timed out (6s)"))
                }
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - qobStart
                results.add(ProviderResult("Qobuz", false, false, "-", latency, e.message?.take(100) ?: "Failed"))
            }

            // 3. TIDAL
            val tidalStart = System.currentTimeMillis()
            try {
                val tidalRes = runWithTimeout(6000L) {
                    TidalAudioProvider.resolve(
                        TidalAudioProvider.Query(
                            mediaId = song.spotifyId,
                            title = song.title,
                            artists = listOf(song.artist),
                            album = song.album,
                            isrc = song.isrc,
                            durationMs = song.durationMs
                        ),
                        audioQuality = TidalAudioQuality.FLAC
                    )
                }
                val latency = System.currentTimeMillis() - tidalStart
                if (tidalRes != null) {
                    val (streamable, note) = checkStreamUrl(tidalRes.mediaUri)
                    results.add(ProviderResult("TIDAL", true, streamable, "FLAC 16-bit", latency, note))
                } else {
                    results.add(ProviderResult("TIDAL", false, false, "-", latency, "Timed out (6s)"))
                }
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - tidalStart
                results.add(ProviderResult("TIDAL", false, false, "-", latency, e.message?.take(100) ?: "Failed"))
            }

            // 4. Deezer Mirror
            val dzStart = System.currentTimeMillis()
            try {
                val dzRes = runWithTimeout(6000L) {
                    DeezerAudioProvider.resolve(
                        DeezerAudioProvider.Query(
                            mediaId = song.spotifyId,
                            title = song.title,
                            artists = listOf(song.artist),
                            album = song.album,
                            isrc = song.isrc,
                            durationMs = song.durationMs
                        )
                    )
                }
                val latency = System.currentTimeMillis() - dzStart
                if (dzRes != null) {
                    val (streamable, note) = checkStreamUrl(dzRes.mediaUri)
                    results.add(ProviderResult("Deezer Mirror", true, streamable, "16-bit FLAC", latency, note))
                } else {
                    results.add(ProviderResult("Deezer Mirror", false, false, "-", latency, "Timed out (6s)"))
                }
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - dzStart
                results.add(ProviderResult("Deezer Mirror", false, false, "-", latency, e.message?.take(100) ?: "Failed"))
            }

            // 5. Deezer Direct (Requires user ARL login)
            val dzdStart = System.currentTimeMillis()
            try {
                val dzdRes = runWithTimeout(5000L) {
                    DeezerSource.resolveRaw(
                        dummyContext,
                        spotifyId = song.spotifyId,
                        isrc = song.isrc,
                        searchQuery = "${song.title} ${song.artist}"
                    )
                }
                val latency = System.currentTimeMillis() - dzdStart
                if (dzdRes != null) {
                    val (streamable, note) = checkStreamUrl(dzdRes.url)
                    results.add(ProviderResult("Deezer Direct", true, streamable, dzdRes.qualityLabel, latency, note))
                } else {
                    results.add(ProviderResult("Deezer Direct", false, false, "-", latency, "Not logged in (No ARL)"))
                }
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - dzdStart
                results.add(ProviderResult("Deezer Direct", false, false, "-", latency, e.message?.take(100) ?: "Failed"))
            }

            // 6. SpotiFLAC (Community Proxy)
            val spFlacStart = System.currentTimeMillis()
            try {
                val spRes = runWithTimeout(6000L) {
                    SpotiFlac.resolve(
                        spotifyTrackId = song.spotifyId,
                        isrc = song.isrc,
                        preferHiRes = true
                    )
                }
                val latency = System.currentTimeMillis() - spFlacStart
                when (spRes) {
                    is SpotiFlac.Result.Success -> {
                        val (streamable, note) = checkStreamUrl(spRes.track.url)
                        results.add(ProviderResult("SpotiFLAC (${spRes.track.provider})", true, streamable, "FLAC ${spRes.track.quality}-bit", latency, note))
                    }
                    is SpotiFlac.Result.Cooldown -> results.add(ProviderResult("SpotiFLAC", false, false, "-", latency, "Cooldown: ${spRes.message}"))
                    is SpotiFlac.Result.NotFound -> results.add(ProviderResult("SpotiFLAC", false, false, "-", latency, "No match"))
                    is SpotiFlac.Result.Error -> results.add(ProviderResult("SpotiFLAC", false, false, "-", latency, "Error: ${spRes.message}"))
                    null -> results.add(ProviderResult("SpotiFLAC", false, false, "-", latency, "Timed out (6s)"))
                }
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - spFlacStart
                results.add(ProviderResult("SpotiFLAC", false, false, "-", latency, e.message?.take(100) ?: "Failed"))
            }

            // 7. SoundCloud
            val scStart = System.currentTimeMillis()
            try {
                val scRes = runWithTimeout(6000L) {
                    SoundCloudAudioProvider.resolve(
                        SoundCloudAudioProvider.Query(
                            mediaId = song.spotifyId,
                            title = song.title,
                            artists = listOf(song.artist),
                            album = song.album,
                            isrc = song.isrc,
                            durationMs = song.durationMs
                        )
                    )
                }
                val latency = System.currentTimeMillis() - scStart
                if (scRes != null) {
                    val (streamable, note) = checkStreamUrl(scRes.mediaUri)
                    results.add(ProviderResult("SoundCloud", true, streamable, "HQ Audio", latency, note))
                } else {
                    results.add(ProviderResult("SoundCloud", false, false, "-", latency, "Timed out (6s)"))
                }
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - scStart
                results.add(ProviderResult("SoundCloud", false, false, "-", latency, e.message?.take(100) ?: "Failed"))
            }

            // 8. YouTube Music (Innertube + YTPlayerUtils)
            val ytStart = System.currentTimeMillis()
            try {
                val ytRes = runWithTimeout(8000L) {
                    val searchRes = YouTube.search("${song.title} ${song.artist}", YouTube.SearchFilter.FILTER_SONG).getOrNull()
                    val videoId = searchRes?.items?.firstOrNull()?.id
                    if (videoId != null) {
                        val pbData = YTPlayerUtils.playerResponseForPlayback(
                            videoId = videoId,
                            audioQuality = AudioQuality.HIGH,
                            connectivityManager = dummyContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager,
                            skipValidation = true
                        ).getOrNull()
                        val streamUrl = pbData?.streamUrl.orEmpty()
                        val mime = pbData?.format?.mimeType?.take(20) ?: "audio"
                        val kbps = (pbData?.format?.bitrate ?: 0) / 1000
                        Triple(videoId, streamUrl, "$mime (${kbps}kbps)")
                    } else null
                }
                val latency = System.currentTimeMillis() - ytStart
                if (ytRes != null) {
                    val (videoId, streamUrl, details) = ytRes
                    if (streamUrl.isNotBlank()) {
                        val (streamable, note) = checkStreamUrl(streamUrl)
                        results.add(ProviderResult("YouTube Music", true, streamable, details, latency, note))
                    } else {
                        results.add(ProviderResult("YouTube Music", true, false, "Protected", latency, "Video found ($videoId) but URL ciphered"))
                    }
                } else {
                    results.add(ProviderResult("YouTube Music", false, false, "-", latency, "No match or timed out"))
                }
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - ytStart
                results.add(ProviderResult("YouTube Music", false, false, "-", latency, e.message?.take(100) ?: "Failed"))
            }

            // Print table for this song
            println(String.format("%-25s | %-10s | %-12s | %-25s | %-8s | %s", "Provider", "Resolved", "Streamable", "Details", "Latency", "Note"))
            println("----------------------------------------------------------------------------------------------------")
            for (res in results) {
                val resStr = if (res.resolved) "✓ YES" else "✗ NO"
                val streamStr = if (res.streamable) "✓ PASS" else "✗ FAIL"
                println(String.format("%-25s | %-10s | %-12s | %-25s | %-8s | %s",
                    res.providerName, resStr, streamStr, res.qualityOrDetails, "${res.latencyMs}ms", res.note
                ))

                // Accumulate statistics
                val baseProv = res.providerName.split(" ").first()
                val current = providerStats.getOrDefault(baseProv, Pair(0, 0))
                val newRes = if (res.resolved) current.first + 1 else current.first
                val newStr = if (res.streamable) current.second + 1 else current.second
                providerStats[baseProv] = Pair(newRes, newStr)
            }
            println()
        }

        println("====================================================================================================")
        println("                                    PROVIDER HEALTH SUMMARY")
        println("====================================================================================================")
        println(String.format("%-20s | %-15s | %-15s | %-15s", "Provider", "Catch Rate", "Stream Rate", "Status"))
        println("----------------------------------------------------------------------------------------------------")
        for ((provider, counts) in providerStats) {
            val catchPct = (counts.first * 100) / testSongs.size
            val streamPct = (counts.second * 100) / testSongs.size
            val status = when {
                streamPct >= 80 -> "🟢 OPERATIONAL"
                streamPct >= 40 -> "🟡 DEGRADED"
                else -> "🔴 DOWN / OFF"
            }
            println(String.format("%-20s | %-15s | %-15s | %-15s",
                provider, "${counts.first}/${testSongs.size} ($catchPct%)", "${counts.second}/${testSongs.size} ($streamPct%)", status
            ))
        }
        println("====================================================================================================\n")
    }

    private fun AmazonAudioProvider.Resolved.codecOrFormat(): String {
        return if (codecs.isNotBlank()) codecs else mimeType
    }

    private class FakeContext : ContextWrapper(null) {
        private val connManager by lazy {
            try {
                val constructor = ConnectivityManager::class.java.getDeclaredConstructor()
                constructor.isAccessible = true
                constructor.newInstance()
            } catch (e: Exception) {
                java.lang.reflect.Proxy.newProxyInstance(
                    ConnectivityManager::class.java.classLoader,
                    arrayOf(ConnectivityManager::class.java)
                ) { _, _, _ -> false } as ConnectivityManager
            }
        }

        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
            return FakeSharedPreferences()
        }
        override fun getSystemService(name: String): Any? {
            if (name == Context.CONNECTIVITY_SERVICE) return connManager
            return null
        }
    }

    private class FakeSharedPreferences : SharedPreferences {
        override fun getAll(): MutableMap<String, *> = mutableMapOf<String, Any>()
        override fun getString(key: String?, defValue: String?): String? = defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = defValue
        override fun getLong(key: String?, defValue: Long): Long = defValue
        override fun getFloat(key: String?, defValue: Float): Float = defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue
        override fun contains(key: String?): Boolean = false
        override fun edit(): SharedPreferences.Editor = FakeEditor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    private class FakeEditor : SharedPreferences.Editor {
        override fun putString(key: String?, value: String?): SharedPreferences.Editor = this
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
        override fun remove(key: String?): SharedPreferences.Editor = this
        override fun clear(): SharedPreferences.Editor = this
        override fun apply() {}
        override fun commit(): Boolean = true
    }
}
