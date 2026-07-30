package com.music.spotui.providers

import android.net.Uri
import android.util.Log
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.Normalizer
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

enum class QobuzAudioQuality(
    val formatId: Int,
    val isLossless: Boolean,
    val isHiRes: Boolean,
    val maxBitDepth: Int,
    val maxSampleRate: Int,
) {
    MP3_320(formatId = 5, isLossless = false, isHiRes = false, maxBitDepth = 16, maxSampleRate = 44_100),
    FLAC_16_44(formatId = 6, isLossless = true, isHiRes = false, maxBitDepth = 16, maxSampleRate = 44_100),
    FLAC_24_96(formatId = 7, isLossless = true, isHiRes = true, maxBitDepth = 24, maxSampleRate = 96_000),
    FLAC_24_192(formatId = 27, isLossless = true, isHiRes = true, maxBitDepth = 24, maxSampleRate = 192_000);

    companion object {
        fun fromFormatId(id: Int?): QobuzAudioQuality? =
            values().firstOrNull { it.formatId == id }
    }
}

object QobuzAudioProvider {
    private const val QOBUZ_APP_ID = "712109809"
    private const val QOBUZ_APP_SECRET = "589be88e4538daea11f509d29e4a23b1"
    private const val QOBUZ_API_BASE = "https://www.qobuz.com/api.json/0.2"

    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36"
    private const val DOWNLOAD_USER_AGENT = "Spotui-Android"
    private const val SONG_LINK_API_URL = "https://api.song.link/v1-alpha.1/links"
    private const val STREAM_CACHE_MS = 45 * 60 * 1000L
    private const val SEARCH_CACHE_MS = 10 * 60 * 1000L
    private const val STREAM_FAILURE_CACHE_MS = 15 * 60 * 1000L
    private const val SEARCH_LIMIT = 8
    private const val MAX_STREAM_CANDIDATES = 3
    private const val MIN_MATCH_SCORE = 90
    private const val REJECT_SCORE = -1_000_000
    private val AMAZON_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.US)

    const val DEFAULT_KENNY_BASE_URL = "https://qobuz-api.binimum.org"

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
    )

    data class CandidateMetadata(
        val trackId: String,
        val title: String,
        val artist: String,
        val album: String?,
        val isrc: String?,
        val durationMs: Long?,
    )

    open class QobuzAudioResolutionException(message: String, cause: Throwable? = null) : Exception(message, cause)

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
        val maxBitDepth: Int?,
        val maxSampleRate: Int?,
        val hiresAvailable: Boolean,
    )

    private data class ScoredTrack(
        val track: MatchedTrack,
        val score: Int,
    )

    private class KennyEndpoint(
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
    ): String = "Custom Qobuz #$customIndex"

    fun normalizeResolverEndpointsInput(value: String): String =
        value.normalizedResolverEndpoints().joinToString("\n")

    fun isResolverEndpointsInputValid(value: String): Boolean {
        val entries = value.resolverEndpointTokens()
        return entries.all { token -> token.normalizedResolverEndpointOrNull() != null }
    }

    private fun kennyEndpoints(customResolverEndpoints: String?): List<KennyEndpoint> {
        var customIndex = 0
        val bases = resolverEndpointBases(customResolverEndpoints)
            .ifEmpty { listOf(DEFAULT_KENNY_BASE_URL) }
        return bases
            .map { baseUrl ->
                customIndex += 1
                KennyEndpoint(
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
        preferHiRes: Boolean = true,
        resolverEndpoints: String? = null,
    ): Resolved {
        val now = System.currentTimeMillis()
        val endpoints = kennyEndpoints(resolverEndpoints)
        val endpointCacheKey = endpoints.joinToString(",") { it.baseUrl }.hashCode()
        val directTrackId = query.mediaId.toQobuzTrackIdOrNull()
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
            throw QobuzAudioResolutionException("Qobuz match not found for ${query.title}")
        }

        val qualities = qualityCandidates(preferHiRes)
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
                    requestDirectFlac(
                        endpoints = endpoints,
                        track = track,
                        quality = quality,
                        durationMs = query.durationMs ?: track.durationMs,
                        now = now,
                    )
                }.onFailure { error ->
                    Log.w("QobuzAudio", "Qobuz ${quality.name} stream failed for ${track.trackId}", error)
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

        throw QobuzAudioResolutionException(
            "Qobuz FLAC stream not found for ${query.title}: ${errors.joinToString("; ").take(720)}",
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

    fun isQobuzTrackId(value: String): Boolean = value.toQobuzTrackIdOrNull() != null

    fun searchCandidates(
        query: Query,
        limit: Int = 8,
        resolverEndpoints: String? = null,
    ): List<CandidateMetadata> =
        kennyEndpoints(resolverEndpoints)
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

    private fun findCandidateTracks(
        query: Query,
        downloadEndpoints: List<KennyEndpoint>,
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

        resolveSongLinkQobuzTrackId(query)?.let { qobuzId ->
            val track = resolveTrackById(qobuzId) ?: query.toDirectMatchedTrack(qobuzId)
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
        downloadEndpoints: List<KennyEndpoint>,
        endpointCacheKey: Int,
    ): JSONArray? {
        if (term.isBlank()) return null
        searchTracksFromKennyApi(term, exactIsrc, downloadEndpoints, endpointCacheKey)
            ?.takeIf { it.length() > 0 }
            ?.let { return it }
        return searchTracksFromQobuzPublicApi(term)
    }

    private fun searchTracksFromKennyApi(
        term: String,
        exactIsrc: Boolean,
        downloadEndpoints: List<KennyEndpoint>,
        endpointCacheKey: Int,
    ): JSONArray? {
        val cacheKey = "kenny:$endpointCacheKey:${if (exactIsrc) "isrc" else "query"}:${term.lowercase(Locale.US)}"
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
                    root.optJSONObject("tracks")?.optJSONArray("items")
                        ?: root.optJSONArray("items")
                        ?: root.optJSONArray("tracks")
                }
            }.getOrNull()?.let { results ->
                searchCache[cacheKey] = CachedSearch(results, now + SEARCH_CACHE_MS)
                return results
            }
        }
        return null
    }

    private fun searchTracksFromQobuzPublicApi(term: String): JSONArray? {
        val params = sortedMapOf("query" to term.trim(), "limit" to SEARCH_LIMIT.toString())
        val ts = (System.currentTimeMillis() / 1000).toString()
        val sigPayload = buildString {
            append("tracksearch")
            params.forEach { (k, v) -> append(k); append(v) }
            append(ts)
            append(QOBUZ_APP_SECRET)
        }
        val sig = md5Hex(sigPayload)
        val url = QOBUZ_API_BASE.toHttpUrl()
            .newBuilder()
            .addPathSegment("track")
            .addPathSegment("search")
            .addQueryParameter("query", term.trim())
            .addQueryParameter("limit", SEARCH_LIMIT.toString())
            .addQueryParameter("app_id", QOBUZ_APP_ID)
            .addQueryParameter("request_ts", ts)
            .addQueryParameter("request_sig", sig)
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("X-App-Id", QOBUZ_APP_ID)
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body.string().takeIf { it.isNotBlank() } ?: return@use null
                JSONObject(body).optJSONObject("tracks")?.optJSONArray("items")
            }
        }.getOrNull()
    }

    private fun resolveTrackById(trackId: String): MatchedTrack? {
        val url = QOBUZ_API_BASE.toHttpUrl()
            .newBuilder()
            .addPathSegment("track")
            .addPathSegment("get")
            .addQueryParameter("track_id", trackId)
            .addQueryParameter("app_id", QOBUZ_APP_ID)
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("X-App-Id", QOBUZ_APP_ID)
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

        if (track.hiresAvailable) score += 25

        return score
    }

    private fun requestDirectFlac(
        endpoints: List<KennyEndpoint>,
        track: MatchedTrack,
        quality: QobuzAudioQuality,
        durationMs: Long?,
        now: Long,
    ): Resolved {
        val errors = mutableListOf<String>()
        for (endpoint in endpoints) {
            val result = runCatching {
                requestDirectFlacFromEndpoint(endpoint, track, quality, durationMs, now)
            }
            result.getOrNull()?.let { return it }
            val error = result.exceptionOrNull() ?: continue
            errors += "${endpoint.name}: ${error.message ?: error.javaClass.simpleName}"
        }
        throw QobuzAudioResolutionException(
            "Qobuz resolver failed on all mirrors for ${track.title}: ${errors.joinToString(" | ").take(720)}",
        )
    }

    private fun requestDirectFlacFromEndpoint(
        endpoint: KennyEndpoint,
        track: MatchedTrack,
        quality: QobuzAudioQuality,
        durationMs: Long?,
        now: Long,
    ): Resolved {
        val url = endpoint.baseUrl
            .toHttpUrl()
            .newBuilder()
            .addPathSegment("track")
            .addQueryParameter("id", track.trackId)
            .addQueryParameter("quality", quality.formatId.toString())
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
                throw QobuzAudioResolutionException("Qobuz ${endpoint.name} HTTP ${response.code}: ${responseBody.take(180)}")
            }
            val root = JSONObject(responseBody)
            val streamUrl = root.stringOrNull("url")
                ?: root.optJSONObject("data")?.stringOrNull("url")
                ?: throw QobuzAudioResolutionException("Qobuz resolver returned no stream URL")

            val effectiveDurationMs = durationMs ?: track.durationMs
            val streamMetadata = fetchStreamMetadata(streamUrl)
            val bitrate = estimateBitrate(streamMetadata.contentLength, effectiveDurationMs)
                ?: if (quality.isLossless) 1_411_000 else 320_000

            val label = when {
                quality.isHiRes -> "Qobuz 24-bit Hi-Res"
                quality.isLossless -> "Qobuz 16-bit CD"
                else -> "Qobuz MP3 320"
            }

            return Resolved(
                mediaUri = streamUrl,
                trackId = track.trackId,
                label = label,
                mimeType = if (quality.isLossless) "audio/flac" else "audio/mpeg",
                codecs = if (quality.isLossless) "flac" else "mp3",
                bitrate = bitrate,
                sampleRate = quality.maxSampleRate,
                contentLength = streamMetadata.contentLength,
                expiresAtMs = extractExpiryMs(streamUrl, now),
            )
        }
    }

    private fun qualityCandidates(preferHiRes: Boolean): List<QobuzAudioQuality> =
        if (preferHiRes) {
            listOf(
                QobuzAudioQuality.FLAC_24_192,
                QobuzAudioQuality.FLAC_24_96,
                QobuzAudioQuality.FLAC_16_44,
                QobuzAudioQuality.MP3_320,
            )
        } else {
            listOf(
                QobuzAudioQuality.FLAC_16_44,
                QobuzAudioQuality.MP3_320,
            )
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

    private fun extractExpiryMs(url: String, now: Long): Long {
        val httpUrl = url.toHttpUrlOrNull() ?: return now + STREAM_CACHE_MS
        val expiresSeconds = httpUrl.queryParameterIgnoreCase("X-Amz-Expires")?.toLongOrNull()
        val date = httpUrl.queryParameterIgnoreCase("X-Amz-Date")
        if (expiresSeconds != null && date != null) {
            runCatching {
                val issuedAt = LocalDateTime.parse(date, AMAZON_DATE).toInstant(ZoneOffset.UTC).toEpochMilli()
                return (issuedAt + expiresSeconds * 1000L - 30_000L).coerceAtLeast(now + 60_000L)
            }
        }
        return now + STREAM_CACHE_MS
    }

    private fun resolveSongLinkQobuzTrackId(query: Query): String? {
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
                        ?.optJSONObject("qobuz")
                        ?.stringOrNull("url")
                        ?.toQobuzTrackIdOrNull()
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
            maxBitDepth = null,
            maxSampleRate = null,
            hiresAvailable = false,
        )

    private fun JSONObject.toMatchedTrack(): MatchedTrack? {
        val trackId = stringOrNull("id") ?: return null
        val title = stringOrNull("title") ?: stringOrNull("name") ?: return null
        val artists = collectArtistNames()
        val album = optJSONObject("album")?.stringOrNull("title")
        val hires = optBoolean("hires", false) || optBoolean("hires_streamable", false)

        return MatchedTrack(
            trackId = trackId,
            title = title,
            artistNames = artists,
            album = album,
            isrc = stringOrNull("isrc"),
            durationMs = longOrNull("duration")?.takeIf { it > 0L }?.times(1000L),
            maxBitDepth = optInt("maximum_bit_depth").takeIf { it > 0 },
            maxSampleRate = normalizeSampleRate(optDouble("maximum_sampling_rate").takeIf { it > 0.0 }),
            hiresAvailable = hires,
        )
    }

    private fun JSONObject.collectArtistNames(): List<String> {
        val names = mutableListOf<String>()
        optJSONObject("performer")?.stringOrNull("name")?.takeIf { it.isNotBlank() }?.let(names::add)
        optJSONObject("artist")?.stringOrNull("name")?.takeIf { it.isNotBlank() }?.let(names::add)
        return names.distinct()
    }

    private fun String.toQobuzTrackIdOrNull(): String? {
        val trimmed = trim()
        if (trimmed.matches(Regex("\\d+"))) return trimmed
        Regex("""qobuz\.com/(?:[a-z]{2}-[a-z]{2}/)?track/(\d+)""", RegexOption.IGNORE_CASE)
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

    private fun HttpUrl.queryParameterIgnoreCase(name: String): String? {
        val key = queryParameterNames.firstOrNull { it.equals(name, ignoreCase = true) } ?: return null
        return queryParameter(key)
    }

    private fun estimateBitrate(contentLength: Long?, durationMs: Long?): Int? {
        val length = contentLength?.takeIf { it > 0L } ?: return null
        val duration = durationMs?.takeIf { it > 0L } ?: return null
        return ((length * 8L * 1000L) / duration).toInt()
    }

    private fun normalizeSampleRate(value: Double?): Int? {
        val sampleRate = value?.takeIf { it > 0.0 } ?: return null
        return when {
            sampleRate < 1000.0 -> (sampleRate * 1000.0).roundToInt()
            else -> sampleRate.roundToInt()
        }
    }

    private fun md5Hex(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

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
        trim().substringBefore(',').replace(Regex("\\s+"), " ").trim()

    private data class StreamMetadata(
        val mimeType: String?,
        val contentLength: Long?,
    )

    private val STOP_WORDS = setOf("the", "and", "feat", "ft", "with", "remaster", "remastered", "version", "explicit", "clean", "audio", "official")
    private val VERSION_TOKENS = setOf("remix", "mix", "edit", "acoustic", "sped", "slowed", "nightcore", "karaoke")
}
