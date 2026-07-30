package com.music.spotui.providers

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.EOFException
import java.io.IOException
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

enum class DeezerAudioQuality(
    val format: String,
    val isLossless: Boolean,
    val maxBitrateKbps: Int,
) {
    MP3_128(format = "MP3_128", isLossless = false, maxBitrateKbps = 128),
    MP3_320(format = "MP3_320", isLossless = false, maxBitrateKbps = 320),
    FLAC(format = "FLAC", isLossless = true, maxBitrateKbps = 1411);

    companion object {
        fun fromFormat(format: String?): DeezerAudioQuality? =
            values().firstOrNull { it.format.equals(format, ignoreCase = true) }
    }
}

object DeezerAudioProvider {
    private const val DEEZER_API_BASE = "https://api.deezer.com"
    private const val SONG_LINK_API_URL = "https://api.song.link/v1-alpha.1/links"
    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36"
    private const val DOWNLOAD_USER_AGENT = "Spotui-Android"

    private const val STREAM_CACHE_MS = 45 * 60 * 1000L
    private const val SEARCH_CACHE_MS = 10 * 60 * 1000L
    private const val STREAM_FAILURE_CACHE_MS = 15 * 60 * 1000L
    private const val SEARCH_LIMIT = 8
    private const val MAX_STREAM_CANDIDATES = 3
    private const val MIN_MATCH_SCORE = 90
    private const val REJECT_SCORE = -1_000_000

    const val DEFAULT_RESOLVER_BASE_URL = "https://deezer-api.binimum.org"

    data class Query(
        val mediaId: String,
        val title: String,
        val artists: List<String>,
        val album: String?,
        val isrc: String?,
        val durationMs: Long?,
    )

    data class Resolved(
        val mediaUri: String,
        val trackId: String,
        val label: String,
        val mimeType: String,
        val codecs: String,
        val bitrate: Int,
        val sampleRate: Int?,
        val contentLength: Long?,
        val expiresAtMs: Long,
        val blowfishKeyHex: String? = null,
    )

    data class CandidateMetadata(
        val trackId: String,
        val title: String,
        val artist: String,
        val album: String?,
        val isrc: String?,
        val durationMs: Long?,
    )

    open class DeezerAudioResolutionException(message: String, cause: Throwable? = null) : Exception(message, cause)

    private data class CachedFailure(
        val message: String,
        val expiresAtMs: Long,
    )

    private data class CachedSearch(
        val results: JSONArray,
        val expiresAtMs: Long,
    )

    private data class MatchedTrack(
        val trackId: String,
        val title: String,
        val artistNames: List<String>,
        val album: String?,
        val isrc: String?,
        val durationMs: Long?,
        val md5Origin: String?,
        val mediaVersion: String?,
    )

    private data class ScoredTrack(
        val track: MatchedTrack,
        val score: Int,
    )

    private class DeezerResolverEndpoint(
        val name: String,
        baseUrl: String,
    ) {
        val baseUrl: String = baseUrl.trimEnd('/')
    }

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()

    private val searchCache = ConcurrentHashMap<String, CachedSearch>()
    private val streamCache = ConcurrentHashMap<String, Resolved>()
    private val streamFailureCache = ConcurrentHashMap<String, CachedFailure>()

    fun resolverEndpointBases(customResolverEndpoints: String? = null): List<String> =
        customResolverEndpoints.normalizedResolverEndpoints()
            .distinctBy { it.lowercase(Locale.US) }

    fun resolverEndpointDisplayName(
        baseUrl: String,
        customIndex: Int,
    ): String = "Custom Deezer #$customIndex"

    fun normalizeResolverEndpointsInput(value: String): String =
        value.normalizedResolverEndpoints().joinToString("\n")

    fun isResolverEndpointsInputValid(value: String): Boolean {
        val entries = value.resolverEndpointTokens()
        return entries.all { token -> token.normalizedResolverEndpointOrNull() != null }
    }

    private fun resolverEndpointsList(customResolverEndpoints: String?): List<DeezerResolverEndpoint> {
        var customIndex = 0
        val bases = resolverEndpointBases(customResolverEndpoints)
            .ifEmpty { listOf(DEFAULT_RESOLVER_BASE_URL) }
        return bases
            .map { baseUrl ->
                customIndex += 1
                DeezerResolverEndpoint(
                    name = resolverEndpointDisplayName(baseUrl, customIndex),
                    baseUrl = baseUrl,
                )
            }
    }

    private fun String?.normalizedResolverEndpoints(): List<String> =
        orEmpty()
            .resolverEndpointTokens()
            .mapNotNull { token -> token.normalizedResolverEndpointOrNull() }
            .distinctBy { it.lowercase(Locale.US) }

    private fun String.resolverEndpointTokens(): List<String> =
        split('\n', '\r', ',', ';', '\t', ' ')
            .map { it.trim() }
            .filter { it.isNotBlank() }

    private fun String.normalizedResolverEndpointOrNull(): String? {
        val candidate = trim()
            .removeSuffix("/")
            .let { value ->
                if (value.contains("://")) value else "https://$value"
            }
        return candidate.toHttpUrlOrNull()?.toString()?.trimEnd('/')
    }

    fun resolve(
        query: Query,
        preferFlac: Boolean = true,
        resolverEndpoints: String? = null,
    ): Resolved {
        val now = System.currentTimeMillis()
        val endpoints = resolverEndpointsList(resolverEndpoints)
        val endpointCacheKey = endpoints.joinToString(",") { it.baseUrl }.hashCode()
        val directTrackId = query.mediaId.toDeezerTrackIdOrNull()
        val tracks = if (directTrackId != null) {
            val directTrack = resolveTrackById(directTrackId) ?: query.toDirectMatchedTrack(directTrackId)
            buildList {
                add(directTrack)
                findCandidateTracks(query, endpoints, endpointCacheKey)
                    .asSequence()
                    .filterNot { it.trackId == directTrack.trackId }
                    .take(MAX_STREAM_CANDIDATES - 1)
                    .forEach { add(it) }
            }.distinctBy { it.trackId }
        } else {
            findCandidateTracks(query, endpoints, endpointCacheKey)
        }

        if (tracks.isEmpty()) {
            throw DeezerAudioResolutionException("Deezer match not found for ${query.title}")
        }

        val qualities = if (preferFlac) listOf(DeezerAudioQuality.FLAC, DeezerAudioQuality.MP3_320) else listOf(DeezerAudioQuality.MP3_320, DeezerAudioQuality.FLAC)
        val errors = mutableListOf<String>()

        for (track in tracks.take(MAX_STREAM_CANDIDATES)) {
            for (quality in qualities) {
                val streamCacheKey = "${query.mediaId}::${track.trackId}::${quality.name}"
                val cachedStream = streamCache[streamCacheKey]
                    ?.takeIf { it.expiresAtMs > now + 20_000L }
                if (cachedStream != null) return cachedStream

                val cachedFailure = streamFailureCache[streamCacheKey]?.takeIf { it.expiresAtMs > now }
                if (cachedFailure != null) {
                    errors += "${track.trackId}/${quality.name}: cached failure: ${cachedFailure.message}"
                    continue
                }

                val streamAttempt = runCatching {
                    requestStream(
                        endpoints = endpoints,
                        track = track,
                        quality = quality,
                        durationMs = query.durationMs ?: track.durationMs,
                        now = now,
                    )
                }.onFailure { error ->
                    Log.w("DeezerAudio", "Deezer ${quality.name} stream failed for ${track.trackId}", error)
                    errors += "${track.trackId}/${quality.name}: ${error.message ?: error.javaClass.simpleName}"
                    streamFailureCache[streamCacheKey] = CachedFailure(
                        message = error.message ?: error.javaClass.simpleName,
                        expiresAtMs = now + STREAM_FAILURE_CACHE_MS,
                    )
                }

                streamAttempt.getOrNull()?.let { resolved ->
                    streamCache[streamCacheKey] = resolved
                    return resolved
                }
            }
        }

        throw DeezerAudioResolutionException(
            "Deezer audio stream not found for ${query.title}: ${errors.joinToString("; ").take(720)}",
        )
    }

    fun invalidate(mediaId: String) {
        val prefix = "$mediaId::"
        for (key in streamCache.keys) {
            if (key.startsWith(prefix)) streamCache.remove(key)
        }
        for (key in streamFailureCache.keys) {
            if (key.startsWith(prefix)) streamFailureCache.remove(key)
        }
    }

    fun isDeezerTrackId(value: String): Boolean = value.toDeezerTrackIdOrNull() != null

    fun searchCandidates(
        query: Query,
        limit: Int = 8,
        resolverEndpoints: String? = null,
    ): List<CandidateMetadata> =
        resolverEndpointsList(resolverEndpoints)
            .let { endpoints ->
                findCandidateTracks(
                    query = query,
                    downloadEndpoints = endpoints,
                    endpointCacheKey = endpoints.joinToString(",") { it.baseUrl }.hashCode(),
                )
            }
            .take(limit.coerceAtLeast(1))
            .map { track ->
                CandidateMetadata(
                    trackId = track.trackId,
                    title = track.title,
                    artist = track.artistNames.joinToString(", "),
                    album = track.album,
                    isrc = track.isrc,
                    durationMs = track.durationMs,
                )
            }

    fun normalizeIsrc(value: String?): String? {
        val compact = value
            ?.uppercase(Locale.US)
            ?.replace(Regex("[^A-Z0-9]"), "")
            ?: return null
        return Regex("[A-Z]{2}[A-Z0-9]{3}[0-9]{7}")
            .find(compact)
            ?.value
    }

    fun computeBlowfishKey(trackId: String): ByteArray {
        val md5TrackId = md5Hex(trackId.trim())
        val key = ByteArray(16)
        for (i in 0 until 16) {
            val c1 = md5TrackId[i].code
            val c2 = md5TrackId[i + 16].code
            val c3 = gKey[i].code
            key[i] = (c1 xor c2 xor c3).toByte()
        }
        return key
    }

    private val gKey = "g42f50a0" + "c670a93f" + "e6f3f890" + "61021948"

    private fun findCandidateTracks(
        query: Query,
        downloadEndpoints: List<DeezerResolverEndpoint>,
        endpointCacheKey: Int,
    ): List<MatchedTrack> {
        val candidates = mutableListOf<ScoredTrack>()
        val wantedTitle = query.title.titleMatchNormalized()
        val wantedArtists = query.artists.map { it.normalized() }.filter { it.isNotBlank() }
        val wantedAlbum = query.album.normalized()
        val wantedIsrc = normalizeIsrc(query.isrc)
        val wantedDurationMs = query.durationMs?.takeIf { it > 0L }

        wantedIsrc?.let { isrc ->
            searchTracks(
                term = isrc,
                exactIsrc = true,
                downloadEndpoints = downloadEndpoints,
                endpointCacheKey = endpointCacheKey,
            )
                ?.let { selectCandidateTracks(it, query, exactIsrcOnly = true) }
                ?.firstOrNull()
                ?.takeIf { it.score >= MIN_MATCH_SCORE }
                ?.let { return listOf(it.track) }
        }

        for (term in searchTerms(query)) {
            val results = searchTracks(
                term = term,
                downloadEndpoints = downloadEndpoints,
                endpointCacheKey = endpointCacheKey,
            ) ?: continue
            candidates += selectCandidateTracks(results, query)
        }

        resolveSongLinkDeezerTrackId(query)?.let { deezerId ->
            val track = resolveTrackById(deezerId) ?: query.toDirectMatchedTrack(deezerId)
            val score = scoreTrack(track, wantedTitle, wantedArtists, wantedAlbum, wantedIsrc, wantedDurationMs)
            if (score >= MIN_MATCH_SCORE) {
                candidates += ScoredTrack(track, score)
            }
        }

        return candidates
            .groupBy { it.track.trackId }
            .mapNotNull { (_, matches) -> matches.maxByOrNull { it.score } }
            .sortedByDescending { it.score }
            .map { it.track }
    }

    private fun searchTracks(
        term: String,
        exactIsrc: Boolean = false,
        downloadEndpoints: List<DeezerResolverEndpoint>,
        endpointCacheKey: Int,
    ): JSONArray? {
        if (term.isBlank()) return null
        searchTracksFromResolverApi(term, exactIsrc, downloadEndpoints, endpointCacheKey)
            ?.takeIf { it.length() > 0 }
            ?.let { return it }
        return searchTracksFromDeezerPublicApi(term)
    }

    private fun searchTracksFromResolverApi(
        term: String,
        exactIsrc: Boolean,
        downloadEndpoints: List<DeezerResolverEndpoint>,
        endpointCacheKey: Int,
    ): JSONArray? {
        val cacheKey = "deezer:$endpointCacheKey:${if (exactIsrc) "isrc" else "query"}:${term.lowercase(Locale.US)}"
        val now = System.currentTimeMillis()
        searchCache[cacheKey]?.takeIf { it.expiresAtMs > now }?.let { return it.results }

        for (endpoint in downloadEndpoints) {
            val url = endpoint.baseUrl
                .toHttpUrl()
                .newBuilder()
                .addPathSegment("search")
                .addQueryParameter("q", term)
                .addQueryParameter("limit", SEARCH_LIMIT.toString())
                .addQueryParameter("offset", "0")
                .build()
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .header("User-Agent", DOWNLOAD_USER_AGENT)
                .build()

            runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body.string().takeIf { it.isNotBlank() } ?: return@use null
                    val root = JSONObject(body)
                    root.optJSONObject("data")?.optJSONArray("items")
                        ?: root.optJSONArray("data")
                        ?: root.optJSONArray("items")
                }
            }.getOrNull()?.let { results ->
                searchCache[cacheKey] = CachedSearch(results, now + SEARCH_CACHE_MS)
                return results
            }
        }
        return null
    }

    private fun searchTracksFromDeezerPublicApi(term: String): JSONArray? {
        val url = DEEZER_API_BASE.toHttpUrl()
            .newBuilder()
            .addPathSegment("search")
            .addQueryParameter("q", term.trim())
            .addQueryParameter("limit", SEARCH_LIMIT.toString())
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .header("User-Agent", BROWSER_USER_AGENT)
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body.string().takeIf { it.isNotBlank() } ?: return@use null
                JSONObject(body).optJSONArray("data")
            }
        }.getOrNull()
    }

    private fun resolveTrackById(trackId: String): MatchedTrack? {
        val url = DEEZER_API_BASE.toHttpUrl()
            .newBuilder()
            .addPathSegment("track")
            .addPathSegment(trackId)
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .header("User-Agent", BROWSER_USER_AGENT)
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body.string().takeIf { it.isNotBlank() } ?: return@use null
                JSONObject(body).toMatchedTrack()
            }
        }.getOrNull()
    }

    private fun selectCandidateTracks(
        results: JSONArray,
        query: Query,
        exactIsrcOnly: Boolean = false,
    ): List<ScoredTrack> {
        val wantedTitle = query.title.titleMatchNormalized()
        val wantedArtists = query.artists.map { it.normalized() }.filter { it.isNotBlank() }
        val wantedAlbum = query.album.normalized()
        val wantedIsrc = normalizeIsrc(query.isrc)
        val wantedDurationMs = query.durationMs?.takeIf { it > 0L }
        val candidates = mutableListOf<ScoredTrack>()

        for (index in 0 until results.length()) {
            val obj = results.optJSONObject(index) ?: continue
            val track = obj.toMatchedTrack() ?: continue
            if (exactIsrcOnly && normalizeIsrc(track.isrc) != wantedIsrc) continue
            val score = scoreTrack(track, wantedTitle, wantedArtists, wantedAlbum, wantedIsrc, wantedDurationMs)
            if (score >= MIN_MATCH_SCORE) {
                candidates += ScoredTrack(track, score)
            }
        }
        return candidates
    }

    private fun scoreTrack(
        track: MatchedTrack,
        wantedTitle: String,
        wantedArtists: List<String>,
        wantedAlbum: String,
        wantedIsrc: String?,
        wantedDurationMs: Long?,
    ): Int {
        val candidateTitle = track.title.titleMatchNormalized()
        val candidateArtists = track.artistNames.map { it.normalized() }.filter { it.isNotBlank() }
        val candidateAlbum = track.album.normalized()
        val candidateIsrc = normalizeIsrc(track.isrc)

        if (wantedTitle.isBlank() || candidateTitle.isBlank()) return REJECT_SCORE
        if (wantedIsrc != null && candidateIsrc == wantedIsrc) {
            return if (durationMatches(wantedDurationMs, track.durationMs)) 220 else REJECT_SCORE
        }
        if (hasVersionMismatch(wantedTitle, candidateTitle)) return REJECT_SCORE

        val wantedTitleTokens = significantTokens(wantedTitle)
        val candidateTitleTokens = significantTokens(candidateTitle)
        var score = 0

        when {
            candidateTitle == wantedTitle -> score += 110
            candidateTitle.contains(wantedTitle) || wantedTitle.contains(candidateTitle) -> score += 62
            else -> {
                val titleOverlap = tokenOverlap(wantedTitleTokens, candidateTitleTokens)
                if (titleOverlap < 0.50) return REJECT_SCORE
                score += (titleOverlap * 48).roundToInt()
            }
        }

        if (wantedArtists.isNotEmpty() && candidateArtists.isNotEmpty()) {
            val artistHit = wantedArtists.any { wanted ->
                candidateArtists.any { candidate ->
                    candidate == wanted || candidate.contains(wanted) || wanted.contains(candidate)
                }
            }
            if (artistHit) {
                score += 38
            } else {
                return REJECT_SCORE
            }
        }

        if (wantedAlbum.isNotBlank() && candidateAlbum.isNotBlank()) {
            score += when {
                candidateAlbum == wantedAlbum -> 18
                candidateAlbum.contains(wantedAlbum) || wantedAlbum.contains(candidateAlbum) -> 10
                else -> 0
            }
        }

        val candidateDurationMs = track.durationMs
        if (wantedDurationMs != null && candidateDurationMs != null) {
            val diffSeconds = abs(wantedDurationMs - candidateDurationMs) / 1000L
            if (diffSeconds > 45) return REJECT_SCORE
            score += when {
                diffSeconds <= 3 -> 36
                diffSeconds <= 8 -> 18
                diffSeconds <= 20 -> 4
                else -> -50
            }
        }

        return score
    }

    private fun requestStream(
        endpoints: List<DeezerResolverEndpoint>,
        track: MatchedTrack,
        quality: DeezerAudioQuality,
        durationMs: Long?,
        now: Long,
    ): Resolved {
        val errors = mutableListOf<String>()
        for (endpoint in endpoints) {
            val result = runCatching {
                requestStreamFromEndpoint(endpoint, track, quality, durationMs, now)
            }
            result.getOrNull()?.let { return it }
            val error = result.exceptionOrNull() ?: continue
            errors += "${endpoint.name}: ${error.message ?: error.javaClass.simpleName}"
        }
        throw DeezerAudioResolutionException(
            "Deezer resolver failed on all mirrors for ${track.title}: ${errors.joinToString(" | ").take(720)}",
        )
    }

    private fun requestStreamFromEndpoint(
        endpoint: DeezerResolverEndpoint,
        track: MatchedTrack,
        quality: DeezerAudioQuality,
        durationMs: Long?,
        now: Long,
    ): Resolved {
        val url = endpoint.baseUrl
            .toHttpUrl()
            .newBuilder()
            .addPathSegment("track")
            .addQueryParameter("id", track.trackId)
            .addQueryParameter("format", quality.format)
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .header("User-Agent", DOWNLOAD_USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body.string()
            if (!response.isSuccessful) {
                throw DeezerAudioResolutionException("Deezer ${endpoint.name} HTTP ${response.code}: ${responseBody.take(180)}")
            }
            val root = JSONObject(responseBody)
            val streamUrl = root.stringOrNull("url")
                ?: root.optJSONObject("data")?.stringOrNull("url")
                ?: throw DeezerAudioResolutionException("Deezer resolver returned no stream URL")

            val bfKeyHex = root.stringOrNull("blowfishKey")
                ?: computeBlowfishKey(track.trackId).joinToString("") { "%02x".format(it) }

            val effectiveDurationMs = durationMs ?: track.durationMs
            val streamMetadata = fetchStreamMetadata(streamUrl)
            val bitrate = estimateBitrate(streamMetadata.contentLength, effectiveDurationMs)
                ?: (quality.maxBitrateKbps * 1000)

            val label = when (quality) {
                DeezerAudioQuality.FLAC -> "Deezer FLAC"
                DeezerAudioQuality.MP3_320 -> "Deezer MP3 320"
                DeezerAudioQuality.MP3_128 -> "Deezer MP3 128"
            }

            return Resolved(
                mediaUri = streamUrl,
                trackId = track.trackId,
                label = label,
                mimeType = if (quality.isLossless) "audio/flac" else "audio/mpeg",
                codecs = if (quality.isLossless) "flac" else "mp3",
                bitrate = bitrate,
                sampleRate = 44_100,
                contentLength = streamMetadata.contentLength,
                expiresAtMs = now + STREAM_CACHE_MS,
                blowfishKeyHex = bfKeyHex,
            )
        }
    }

    private fun fetchStreamMetadata(url: String): StreamMetadata {
        val httpUrl = url.toHttpUrlOrNull() ?: return StreamMetadata("audio/flac", null)
        val builder = Request.Builder()
            .url(httpUrl)
            .header("Accept", "audio/flac,audio/mpeg,audio/*,*/*;q=0.8")
            .header("Accept-Encoding", "identity")
            .header("User-Agent", BROWSER_USER_AGENT)

        return runCatching {
            client.newCall(builder.head().build()).execute().use { response ->
                if (response.isSuccessful) {
                    StreamMetadata(
                        mimeType = response.header("Content-Type")?.substringBefore(';'),
                        contentLength = response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0L },
                    )
                } else null
            }
        }.getOrNull() ?: StreamMetadata("audio/flac", null)
    }

    private fun resolveSongLinkDeezerTrackId(query: Query): String? {
        for (sourceUrl in songLinkSourceUrls(query.mediaId)) {
            val url = SONG_LINK_API_URL.toHttpUrl()
                .newBuilder()
                .addQueryParameter("url", sourceUrl)
                .build()
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .header("User-Agent", BROWSER_USER_AGENT)
                .build()

            runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body.string().takeIf { it.isNotBlank() } ?: return@use null
                    val root = JSONObject(body)
                    root.optJSONObject("linksByPlatform")
                        ?.optJSONObject("deezer")
                        ?.stringOrNull("url")
                        ?.toDeezerTrackIdOrNull()
                }
            }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun songLinkSourceUrls(mediaId: String): List<String> {
        val trimmed = mediaId.trim()
        if (trimmed.isBlank()) return emptyList()
        return buildList {
            if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                add(trimmed)
            }
            Regex("""spotify[:/](?:track[:/])?([A-Za-z0-9]{22})""", RegexOption.IGNORE_CASE)
                .find(trimmed)
                ?.groupValues
                ?.getOrNull(1)
                ?.let { add("https://open.spotify.com/track/$it") }
            if (trimmed.matches(Regex("[A-Za-z0-9]{22}"))) {
                add("https://open.spotify.com/track/$trimmed")
            }
        }.distinct()
    }

    private fun Query.toDirectMatchedTrack(trackId: String): MatchedTrack =
        MatchedTrack(
            trackId = trackId,
            title = title,
            artistNames = artists,
            album = album,
            isrc = isrc,
            durationMs = durationMs,
            md5Origin = null,
            mediaVersion = null,
        )

    private fun JSONObject.toMatchedTrack(): MatchedTrack? {
        val trackId = stringOrNull("id") ?: return null
        val title = stringOrNull("title") ?: stringOrNull("name") ?: return null
        val artists = collectArtistNames()
        val album = optJSONObject("album")?.stringOrNull("title")

        return MatchedTrack(
            trackId = trackId,
            title = title,
            artistNames = artists,
            album = album,
            isrc = stringOrNull("isrc"),
            durationMs = longOrNull("duration")?.takeIf { it > 0L }?.times(1000L),
            md5Origin = stringOrNull("md5_origin"),
            mediaVersion = stringOrNull("media_version"),
        )
    }

    private fun JSONObject.collectArtistNames(): List<String> {
        val names = mutableListOf<String>()
        optJSONObject("artist")?.stringOrNull("name")?.takeIf { it.isNotBlank() }?.let(names::add)
        optJSONArray("contributors")?.let { array ->
            for (i in 0 until array.length()) {
                array.optJSONObject(i)?.stringOrNull("name")?.takeIf { it.isNotBlank() }?.let(names::add)
            }
        }
        return names.distinct()
    }

    private fun String.toDeezerTrackIdOrNull(): String? {
        val trimmed = trim()
        if (trimmed.matches(Regex("\\d+"))) return trimmed
        Regex("""deezer\.com/(?:[a-z]{2}/)?track/(\d+)""", RegexOption.IGNORE_CASE)
            .find(trimmed)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { return it }
        return null
    }

    private fun String?.normalized(): String =
        this
            ?.lowercase(Locale.US)
            ?.let { Normalizer.normalize(it, Normalizer.Form.NFD) }
            ?.replace(Regex("\\p{Mn}+"), "")
            ?.replace(Regex("[^a-z0-9]+"), " ")
            ?.trim()
            .orEmpty()

    private fun String.titleMatchNormalized(): String =
        normalized()
            .replace(Regex("""\b(feat|ft|featuring)\b.*$"""), "")
            .replace(Regex("""\b(explicit|clean|remaster|remastered|version|audio|official)\b"""), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun String.searchQueryTitle(): String =
        trim()
            .replace(Regex("""\s*[\[(]\s*(feat\.?|ft\.?|featuring)\b.*?[\])]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*-\s*(explicit|clean|remaster(?:ed)?|audio|official)\b.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun searchTerms(query: Query): List<String> =
        buildList {
            val title = query.title.searchQueryTitle()
            val primaryArtist = query.artists
                .firstOrNull { it.isNotBlank() }
                ?.searchQueryArtist()
                .orEmpty()
            val titleAndArtist = listOf(title, primaryArtist)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            add(titleAndArtist)
        }.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

    private fun String.searchQueryArtist(): String =
        trim()
            .substringBefore(',')
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun significantTokens(value: String): Set<String> =
        value.split(' ')
            .map { it.trim() }
            .filter { it.length >= 2 && it !in STOP_WORDS }
            .toSet()

    private fun tokenOverlap(wanted: Set<String>, candidate: Set<String>): Double {
        if (wanted.isEmpty() || candidate.isEmpty()) return 0.0
        val shared = wanted.intersect(candidate).size
        return shared.toDouble() / wanted.size.coerceAtLeast(candidate.size).toDouble()
    }

    private fun durationMatches(wantedMs: Long?, candidateMs: Long?): Boolean {
        if (wantedMs == null || candidateMs == null) return true
        return abs(wantedMs - candidateMs) <= 45_000L
    }

    private fun hasVersionMismatch(wanted: String, candidate: String): Boolean {
        val wantedLive = wanted.contains(" live ")
        val candidateLive = candidate.contains(" live ")
        if (wantedLive != candidateLive) return true
        return VERSION_TOKENS.any { token ->
            wanted.hasToken(token) != candidate.hasToken(token)
        }
    }

    private fun String.hasToken(token: String): Boolean = split(' ').any { it == token }

    private fun JSONObject.stringOrNull(key: String): String? =
        optString(key).takeIf { it.isNotBlank() && it != "null" }

    private fun JSONObject.longOrNull(key: String): Long? =
        if (has(key)) optLong(key).takeIf { it > 0L } else null

    private fun estimateBitrate(contentLength: Long?, durationMs: Long?): Int? {
        val length = contentLength?.takeIf { it > 0L } ?: return null
        val duration = durationMs?.takeIf { it > 0L } ?: return null
        return ((length * 8L * 1000L) / duration).toInt()
    }

    private fun md5Hex(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private val STOP_WORDS = setOf("the", "and", "feat", "ft", "with", "remaster", "remastered", "version", "explicit", "clean", "audio", "official")
    private val VERSION_TOKENS = setOf("remix", "mix", "edit", "acoustic", "sped", "slowed", "nightcore", "karaoke")

    private data class StreamMetadata(
        val mimeType: String?,
        val contentLength: Long?,
    )
}

@UnstableApi
class DeezerAudioDataSource private constructor(
    private val upstream: DataSource,
    private val blowfishKey: ByteArray,
) : BaseDataSource(true) {

    private var bytesReadTotal: Long = 0
    private var dataSpec: DataSpec? = null

    class Factory(
        private val upstreamFactory: DataSource.Factory,
        private val blowfishKeyHex: String,
    ) : DataSource.Factory {

        override fun createDataSource(): DataSource {
            val upstream = upstreamFactory.createDataSource()
            val keyBytes = blowfishKeyHex.chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
            return DeezerAudioDataSource(upstream, keyBytes)
        }
    }

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        bytesReadTotal = dataSpec.position
        transferInitializing(dataSpec)

        val upstreamBytes = upstream.open(dataSpec)
        transferStarted(dataSpec)
        return upstreamBytes
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0

        val read = upstream.read(buffer, offset, length)
        if (read == C.RESULT_END_OF_INPUT) return C.RESULT_END_OF_INPUT

        val chunkStart = bytesReadTotal
        val chunkEnd = bytesReadTotal + read

        if (chunkStart < 6000) {
            val decryptEnd = min(chunkEnd, 6000L)
            var currentPos = chunkStart

            while (currentPos < decryptEnd) {
                val blockIndex = (currentPos / 2048).toInt()
                val blockOffset = (currentPos % 2048).toInt()

                if (blockIndex % 3 == 0) {
                    val bufOffset = offset + (currentPos - chunkStart).toInt()
                    val blockStartInBuf = bufOffset - blockOffset
                    val blockEndInBuf = blockStartInBuf + 2048

                    val decStart = maxOf(bufOffset, blockStartInBuf)
                    val decEnd = minOf(offset + read, blockEndInBuf)

                    if (decStart < decEnd) {
                        decryptBlowfishBlock(buffer, decStart, decEnd)
                    }
                }
                currentPos = (currentPos / 2048 + 1) * 2048
            }
        }

        bytesReadTotal += read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = upstream.uri

    override fun close() {
        dataSpec?.let { transferEnded() }
        dataSpec = null
        upstream.close()
    }

    private fun decryptBlowfishBlock(buffer: ByteArray, start: Int, end: Int) {
        val len = end - start
        if (len <= 0) return

        runCatching {
            val cipher = Cipher.getInstance("Blowfish/CBC/NoPadding")
            val iv = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7)
            val secretKey = SecretKeySpec(blowfishKey, "Blowfish")
            val ivSpec = IvParameterSpec(iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)

            val decrypted = cipher.doFinal(buffer, start, len - (len % 8))
            System.arraycopy(decrypted, 0, buffer, start, decrypted.size)
        }.onFailure { e ->
            Log.w("DeezerAudioDS", "Blowfish decryption error: ${e.message}")
        }
    }
}
