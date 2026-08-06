package com.music.spotui.providers

import android.util.Log
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

object SoundCloudAudioProvider {
    private const val SOUNDCLOUD_API_BASE = "https://api-v2.soundcloud.com"
    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36"
    private const val DEFAULT_CLIENT_ID = "iZ8g4fkmVfrgwsRotA4tP8hAYzBZu0pE"

    private const val STREAM_CACHE_MS = 45 * 60 * 1000L
    private const val SEARCH_CACHE_MS = 10 * 60 * 1000L
    private const val STREAM_FAILURE_CACHE_MS = 15 * 60 * 1000L
    private const val SEARCH_LIMIT = 8
    private const val MAX_STREAM_CANDIDATES = 3
    private const val MIN_MATCH_SCORE = 85
    private const val REJECT_SCORE = -1_000_000

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
        val isHls: Boolean = false,
    )

    data class CandidateMetadata(
        val trackId: String,
        val title: String,
        val artist: String,
        val album: String?,
        val isrc: String?,
        val durationMs: Long?,
    )

    open class SoundCloudAudioResolutionException(message: String, cause: Throwable? = null) : Exception(message, cause)

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
        val progressiveUrl: String?,
        val hlsUrl: String?,
    )

    private data class ScoredTrack(
        val track: MatchedTrack,
        val score: Int,
    )

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()

    private val searchCache = ConcurrentHashMap<String, CachedSearch>()
    private val streamCache = ConcurrentHashMap<String, Resolved>()
    private val streamFailureCache = ConcurrentHashMap<String, CachedFailure>()

    @Volatile
    private var cachedClientId: String? = null

    fun resolve(
        query: Query,
        clientId: String? = null,
    ): Resolved {
        val now = System.currentTimeMillis()
        val activeClientId = clientId ?: getOrFetchClientId()
        val directTrackId = query.mediaId.toSoundCloudTrackIdOrNull()
        val tracks = if (directTrackId != null) {
            val directTrack = resolveTrackById(directTrackId, activeClientId) ?: query.toDirectMatchedTrack(directTrackId)
            buildList {
                add(directTrack)
                findCandidateTracks(query, activeClientId)
                    .asSequence()
                    .filterNot { it.trackId == directTrack.trackId }
                    .take(MAX_STREAM_CANDIDATES - 1)
                    .forEach { add(it) }
            }.distinctBy { it.trackId }
        } else {
            findCandidateTracks(query, activeClientId)
        }

        if (tracks.isEmpty()) {
            throw SoundCloudAudioResolutionException("SoundCloud match not found for ${query.title}")
        }

        val errors = mutableListOf<String>()
        for (track in tracks.take(MAX_STREAM_CANDIDATES)) {
            val streamCacheKey = "${query.mediaId}::${track.trackId}"
            val cachedStream = streamCache[streamCacheKey]
                ?.takeIf { it.expiresAtMs > now + 20_000L }
            if (cachedStream != null) return cachedStream

            val cachedFailure = streamFailureCache[streamCacheKey]?.takeIf { it.expiresAtMs > now }
            if (cachedFailure != null) {
                errors += "${track.trackId}: cached failure: ${cachedFailure.message}"
                continue
            }

            val streamAttempt = runCatching {
                requestStreamUrl(track, activeClientId, now)
            }.onFailure { error ->
                Log.w("SoundCloudAudio", "SoundCloud stream failed for ${track.trackId}", error)
                errors += "${track.trackId}: ${error.message ?: error.javaClass.simpleName}"
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

        throw SoundCloudAudioResolutionException(
            "SoundCloud stream not found for ${query.title}: ${errors.joinToString("; ").take(720)}",
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

    fun isSoundCloudTrackId(value: String): Boolean = value.toSoundCloudTrackIdOrNull() != null

    fun searchCandidates(
        query: Query,
        limit: Int = 8,
        clientId: String? = null,
    ): List<CandidateMetadata> =
        findCandidateTracks(query, clientId ?: getOrFetchClientId())
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

    private fun getOrFetchClientId(): String {
        cachedClientId?.let { return it }
        val fetched = fetchClientIdFromWeb() ?: DEFAULT_CLIENT_ID
        cachedClientId = fetched
        return fetched
    }

    private fun fetchClientIdFromWeb(): String? {
        val homeRequest = Request.Builder()
            .url("https://soundcloud.com")
            .get()
            .header("User-Agent", BROWSER_USER_AGENT)
            .build()

        return runCatching {
            val html = client.newCall(homeRequest).execute().use { it.body.string() }
            val jsUrls = Regex("""https://a-v2\.sndcdn\.com/assets/[^"]+\.js""")
                .findAll(html)
                .map { it.value }
                .toList()

            for (jsUrl in jsUrls.takeLast(5)) {
                val jsRequest = Request.Builder().url(jsUrl).get().header("User-Agent", BROWSER_USER_AGENT).build()
                val jsContent = client.newCall(jsRequest).execute().use { it.body.string() }
                val match = Regex("""client_id[:=]\s*"([A-Za-z0-9]{32})"""").find(jsContent)
                if (match != null) return@runCatching match.groupValues[1]
            }
            null
        }.getOrNull()
    }

    private fun findCandidateTracks(
        query: Query,
        clientId: String,
    ): List<MatchedTrack> {
        val candidates = mutableListOf<ScoredTrack>()
        val wantedTitle = query.title.titleMatchNormalized()
        val wantedArtists = query.artists.map { it.normalized() }.filter { it.isNotBlank() }
        val wantedAlbum = query.album.normalized()
        val wantedIsrc = query.isrc?.uppercase(Locale.US)
        val wantedDurationMs = query.durationMs?.takeIf { it > 0L }

        for (term in searchTerms(query)) {
            val results = searchTracks(term, clientId) ?: continue
            candidates += selectCandidateTracks(results, query)
        }

        return candidates
            .groupBy { it.track.trackId }
            .mapNotNull { (_, matches) -> matches.maxByOrNull { it.score } }
            .sortedByDescending { it.score }
            .map { it.track }
    }

    private fun searchTracks(
        term: String,
        clientId: String,
    ): JSONArray? {
        if (term.isBlank()) return null
        val cacheKey = "$clientId:${term.lowercase(Locale.US)}"
        val now = System.currentTimeMillis()
        searchCache[cacheKey]?.takeIf { it.expiresAtMs > now }?.let { return it.results }

        val url = SOUNDCLOUD_API_BASE.toHttpUrl()
            .newBuilder()
            .addPathSegment("search")
            .addPathSegment("tracks")
            .addQueryParameter("q", term.trim())
            .addQueryParameter("client_id", clientId)
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
                val root = JSONObject(body)
                val results = root.optJSONArray("collection")
                if (results != null) searchCache[cacheKey] = CachedSearch(results, now + SEARCH_CACHE_MS)
                results
            }
        }.getOrNull()
    }

    private fun resolveTrackById(trackId: String, clientId: String): MatchedTrack? {
        val url = SOUNDCLOUD_API_BASE.toHttpUrl()
            .newBuilder()
            .addPathSegment("tracks")
            .addPathSegment(trackId)
            .addQueryParameter("client_id", clientId)
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
    ): List<ScoredTrack> {
        val wantedTitle = query.title.titleMatchNormalized()
        val wantedArtists = query.artists.map { it.normalized() }.filter { it.isNotBlank() }
        val wantedAlbum = query.album.normalized()
        val wantedIsrc = query.isrc?.uppercase(Locale.US)
        val wantedDurationMs = query.durationMs?.takeIf { it > 0L }
        val candidates = mutableListOf<ScoredTrack>()

        for (index in 0 until results.length()) {
            val obj = results.optJSONObject(index) ?: continue
            val track = obj.toMatchedTrack() ?: continue
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

        if (wantedTitle.isBlank() || candidateTitle.isBlank()) return REJECT_SCORE
        val wantedTitleTokens = significantTokens(wantedTitle)
        val candidateTitleTokens = significantTokens(candidateTitle)
        var score = 0

        when {
            candidateTitle == wantedTitle -> score += 110
            candidateTitle.contains(wantedTitle) || wantedTitle.contains(candidateTitle) -> score += 62
            else -> {
                val titleOverlap = tokenOverlap(wantedTitleTokens, candidateTitleTokens)
                if (titleOverlap < 0.45) return REJECT_SCORE
                score += (titleOverlap * 48).roundToInt()
            }
        }

        if (wantedArtists.isNotEmpty() && candidateArtists.isNotEmpty()) {
            val artistHit = wantedArtists.any { wanted ->
                candidateArtists.any { candidate ->
                    candidate == wanted || candidate.contains(wanted) || wanted.contains(candidate)
                }
            }
            if (artistHit) score += 38 else score -= 20
        }

        val candidateDurationMs = track.durationMs
        if (wantedDurationMs != null && candidateDurationMs != null) {
            val diffSeconds = abs(wantedDurationMs - candidateDurationMs) / 1000L
            if (diffSeconds > 60) return REJECT_SCORE
            score += when {
                diffSeconds <= 3 -> 36
                diffSeconds <= 8 -> 18
                diffSeconds <= 20 -> 4
                else -> -50
            }
        }

        return score
    }

    private fun requestStreamUrl(
        track: MatchedTrack,
        clientId: String,
        now: Long,
    ): Resolved {
        val targetTranscodingUrl = track.progressiveUrl ?: track.hlsUrl
            ?: throw SoundCloudAudioResolutionException("SoundCloud track ${track.trackId} has no playable transcodings")

        val isHls = track.progressiveUrl == null && track.hlsUrl != null

        val url = targetTranscodingUrl.toHttpUrl()
            .newBuilder()
            .addQueryParameter("client_id", clientId)
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .header("User-Agent", BROWSER_USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw SoundCloudAudioResolutionException("SoundCloud transcoding fetch HTTP ${response.code}")
            }
            val body = response.body.string()
            val root = JSONObject(body)
            val streamUrl = root.stringOrNull("url")
                ?: throw SoundCloudAudioResolutionException("SoundCloud transcoding returned no stream URL")

            return Resolved(
                mediaUri = streamUrl,
                trackId = track.trackId,
                label = if (isHls) "SoundCloud HLS" else "SoundCloud MP3 128",
                mimeType = if (isHls) "application/x-mpegURL" else "audio/mpeg",
                codecs = if (isHls) "aac" else "mp3",
                bitrate = 128_000,
                sampleRate = 44_100,
                contentLength = null,
                expiresAtMs = now + STREAM_CACHE_MS,
                isHls = isHls,
            )
        }
    }

    private fun Query.toDirectMatchedTrack(trackId: String): MatchedTrack =
        MatchedTrack(
            trackId = trackId,
            title = title,
            artistNames = artists,
            album = album,
            isrc = isrc,
            durationMs = durationMs,
            progressiveUrl = null,
            hlsUrl = null,
        )

    private fun JSONObject.toMatchedTrack(): MatchedTrack? {
        val trackId = optLong("id").takeIf { it > 0L }?.toString()
            ?: stringOrNull("id") ?: return null
        val title = stringOrNull("title") ?: return null
        val artist = optJSONObject("user")?.stringOrNull("username")
            ?: optJSONObject("user")?.stringOrNull("full_name")
        val durationMs = optLong("duration").takeIf { it > 0L }
            ?: optLong("full_duration").takeIf { it > 0L }

        var progressiveUrl: String? = null
        var hlsUrl: String? = null

        optJSONObject("media")?.optJSONArray("transcodings")?.let { trans ->
            for (i in 0 until trans.length()) {
                val item = trans.optJSONObject(i) ?: continue
                val format = item.optJSONObject("format")?.stringOrNull("protocol")
                val url = item.stringOrNull("url") ?: continue

                if (format == "progressive" && progressiveUrl == null) {
                    progressiveUrl = url
                } else if (format == "hls" && hlsUrl == null) {
                    hlsUrl = url
                }
            }
        }

        return MatchedTrack(
            trackId = trackId,
            title = title,
            artistNames = listOfNotNull(artist),
            album = null,
            isrc = stringOrNull("publisher_metadata")?.let { null },
            durationMs = durationMs,
            progressiveUrl = progressiveUrl,
            hlsUrl = hlsUrl,
        )
    }

    private fun String.toSoundCloudTrackIdOrNull(): String? {
        val trimmed = trim()
        if (trimmed.matches(Regex("\\d+"))) return trimmed
        Regex("""soundcloud\.com/[^/]+/([a-z0-9-]+)""", RegexOption.IGNORE_CASE)
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

    private fun JSONObject.stringOrNull(key: String): String? =
        optString(key).takeIf { it.isNotBlank() && it != "null" }

    private val STOP_WORDS = setOf("the", "and", "feat", "ft", "with", "remaster", "remastered", "version", "explicit", "clean", "audio", "official")
}
