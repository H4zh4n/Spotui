package com.music.spotui.providers

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.min

object AmazonAudioProvider {
    const val DEFAULT_SEARCH_API_URL = "https://na.web.skill.music.a2z.com/api/showSearch"
    const val DEFAULT_RESOLVE_API_URL = "https://t2tunes.site/api/amazon-music/media-from-asin"
    internal const val SKILL_BASE_URL = "https://na.mesk.skill.music.a2z.com/api"
    internal const val MUSIC_BASE_URL = "https://music.amazon.com"
    private const val STREAM_CACHE_MS = 30 * 60 * 1000L
    private val ARTIST_SPLIT_REGEX = Regex("[,&]| and | feat\\.? | ft\\.? ")
    const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36"

    private var deviceId: String? = null
    private var sessionId: String? = null
    private var csrfToken: String = ""
    private var csrfTs: String = ""
    private var csrfRnd: String = ""
    private var appVersion: String = "1.0.10905.0"
    private var musicTerritory: String = "US"
    private var isInitialized = false
    private var sessionFetchedAtMs: Long = 0L
    private val initLock = Any()

    data class Query(
        val mediaId: String,
        val title: String,
        val artists: List<String>,
        val album: String?,
        val durationMs: Long?,
        val country: String = "US",
        val quality: String = "HI_RES",
        val explicit: Boolean? = null,
    )

    data class Resolved(
        val mediaUri: String,
        val trackId: String,
        val label: String,
        val mimeType: String,
        val codecs: String,
        val bitrate: Int,
        val sampleRate: Int?,
        val expiresAtMs: Long,
        val decryptionKey: String? = null,
    )

    data class CandidateMetadata(
        val trackId: String,
        val title: String,
        val artist: String,
        val album: String?,
        val durationMs: Long?,
    )

    class AmazonResolutionException(message: String, cause: Throwable? = null) : Exception(message, cause)

    private data class MatchedTrack(
        val asin: String,
        val title: String,
        val artists: List<String>,
        val album: String?,
        val durationMs: Long?,
    )

    private val cookieJar = object : CookieJar {
        private val storage = ConcurrentHashMap<String, List<Cookie>>()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            storage[url.host] = cookies
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return storage[url.host] ?: emptyList()
        }
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    private val streamCache = ConcurrentHashMap<String, Resolved>()

    fun String?.toAmazonAsinOrNull(): String? {
        val s = this?.trim() ?: return null
        if (s.length == 10 && s.all { it.isLetterOrDigit() }) return s
        if (s.startsWith("B0") && s.length == 10) return s
        return null
    }

    fun resolve(context: Context, query: Query): Resolved {
        val now = System.currentTimeMillis()
        val directAsin = query.mediaId.toAmazonAsinOrNull()
        val asin = if (directAsin != null) {
            directAsin
        } else {
            val matched = findCandidateTrack(context, query)
                ?: throw AmazonResolutionException("Amazon Music match not found for ${query.title}")
            matched.asin
        }

        val cacheKey = "$asin::${query.quality}::${query.country}"
        val cached = streamCache[cacheKey]?.takeIf { it.expiresAtMs > now + 20_000L }
        if (cached != null) return cached

        val resolved = resolveAsin(asin, query.mediaId, query.country, query.quality)
        streamCache[cacheKey] = resolved
        return resolved
    }

    private fun findCandidateTrack(context: Context, query: Query): MatchedTrack? {
        val cleanTitle = query.title.cleanSearchTitle()
        val primaryArtist = query.artists.firstOrNull()?.cleanSearchArtist() ?: ""
        val album = query.album?.cleanSearchTitle() ?: ""
        val explicitSuffix = if (query.explicit == true) " Explicit" else ""

        val searchTerms = buildList {
            if (album.isNotBlank() && primaryArtist.isNotBlank()) {
                add("$cleanTitle $primaryArtist $album$explicitSuffix".trim())
            }
            if (primaryArtist.isNotBlank()) add("$cleanTitle $primaryArtist$explicitSuffix".trim())
            add(cleanTitle)
        }.distinct()

        for (searchTerm in searchTerms) {
            val results = searchTracks(context, searchTerm)
            if (results != null && results.length() > 0) {
                selectBestTrack(results, query)?.let { return it }
            }
        }
        return null
    }

    fun searchCandidates(context: Context, term: String, country: String = "US", limit: Int = 10): List<CandidateMetadata> {
        val results = searchTracks(context, term, country) ?: return emptyList()
        val candidates = mutableListOf<CandidateMetadata>()
        for (i in 0 until min(results.length(), limit)) {
            val obj = results.optJSONObject(i) ?: continue
            val asin = obj.optString("asin").takeIf { it.isNotEmpty() } ?: continue
            val title = obj.optString("title").takeIf { it.isNotEmpty() } ?: ""
            val artistsArr = obj.optJSONArray("artists")
            val artistName = artistsArr?.optJSONObject(0)?.optString("name")?.takeIf { it.isNotEmpty() }
                ?: obj.optString("artist").takeIf { it.isNotEmpty() }
                ?: ""
            val albumTitle = obj.optJSONObject("album")?.optString("title")?.takeIf { it.isNotEmpty() }
                ?: obj.optString("album").takeIf { it.isNotEmpty() }
                ?: ""
            val durationMs = if (obj.has("durationMs")) obj.optLong("durationMs") else 0L

            candidates += CandidateMetadata(
                trackId = asin,
                title = title,
                artist = artistName,
                album = albumTitle,
                durationMs = durationMs
            )
        }
        return candidates
    }

    private fun searchTracks(context: Context, term: String, country: String = "US"): JSONArray? {
        ensureSession()

        val pageUrl = "$MUSIC_BASE_URL/search/${java.net.URLEncoder.encode(term, "UTF-8")}"
        val searchUrl = DEFAULT_SEARCH_API_URL

        val amznHeaders = buildHeaders(pageUrl)
        val bodyObj = JSONObject().apply {
            put("filter", JSONObject().apply { put("IsLibrary", JSONArray().apply { put("false") }) }.toString())
            put("keyword", JSONObject().apply {
                put("interface", "Web.TemplatesInterface.v1_0.Touch.SearchTemplateInterface.SearchKeywordClientInformation")
                put("keyword", term)
            }.toString())
            put("suggestedKeyword", term)
            put("userHash", JSONObject().apply { put("level", "LIBRARY_MEMBER") }.toString())
            put("headers", JSONObject(amznHeaders).toString())
        }

        val req = Request.Builder()
            .url(searchUrl)
            .post(bodyObj.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Referer", pageUrl)
            .header("x-amz-target", "com.amazon.music.search.v1.MusicSearchService.showSearch")
            .build()

        return runCatching {
            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val bodyStr = response.body.string()
                parseSearchResults(bodyStr)
            }
        }.getOrNull()
    }

    private fun parseSearchResults(bodyStr: String): JSONArray {
        val root = runCatching { JSONObject(bodyStr) }.getOrNull() ?: return JSONArray()
        val results = JSONArray()

        val methods = root.optJSONArray("methods") ?: JSONArray()
        for (i in 0 until methods.length()) {
            val method = methods.optJSONObject(i) ?: continue
            val template = method.optJSONObject("template") ?: continue
            val widgets = template.optJSONArray("widgets") ?: continue
            for (j in 0 until widgets.length()) {
                val widget = widgets.optJSONObject(j) ?: continue
                val items = widget.optJSONArray("items") ?: continue
                for (k in 0 until items.length()) {
                    val item = items.optJSONObject(k) ?: continue
                    val asin = item.optString("asin").takeIf { it.isNotBlank() } ?: continue
                    val title = textValue(item.opt("primaryText"))
                    if (title.isBlank()) continue
                    val artistStr = textValue(item.opt("secondaryText1"))
                    val artistsArr = JSONArray()
                    if (artistStr.isNotBlank()) {
                        artistStr.split(ARTIST_SPLIT_REGEX).forEach {
                            val t = it.trim()
                            if (t.isNotEmpty()) artistsArr.put(JSONObject().put("name", t))
                        }
                    }
                    results.put(JSONObject().apply {
                        put("asin", asin)
                        put("title", title)
                        put("artists", artistsArr)
                        val dur = parseDurationMMSS(textValue(item.opt("secondaryText3")))
                        if (dur > 0) put("durationMs", dur * 1000L)
                    })
                }
            }
        }
        return results
    }

    private fun selectBestTrack(results: JSONArray, query: Query): MatchedTrack? {
        var best: MatchedTrack? = null
        var bestScore = -1.0
        val targetTitle = query.title.cleanSearchTitle()
        val targetArtist = query.artists.firstOrNull()?.cleanSearchArtist() ?: ""

        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val asin = item.optString("asin").takeIf { it.isNotBlank() } ?: continue
            val title = item.optString("title").cleanSearchTitle()
            val artistsArr = item.optJSONArray("artists")
            val artistName = artistsArr?.optJSONObject(0)?.optString("name")?.cleanSearchArtist() ?: ""
            val durationMs = item.optLong("durationMs", 0L)

            val titleScore = similarityScore(title, targetTitle)
            val artistScore = similarityScore(artistName, targetArtist)
            var score = titleScore * 0.6 + artistScore * 0.4

            if (query.durationMs != null && query.durationMs > 0 && durationMs > 0) {
                val diffSec = abs((durationMs - query.durationMs) / 1000L)
                if (diffSec <= 15) score += 0.2 else score -= 0.3
            }

            if (score > bestScore && score >= 0.5) {
                bestScore = score
                val artistList = if (artistName.isNotBlank()) listOf(artistName) else emptyList()
                best = MatchedTrack(asin, title, artistList, null, durationMs)
            }
        }

        return best
    }

    private fun resolveAsin(asin: String, mediaId: String, country: String, quality: String): Resolved {
        val codec = "flac"
        val url = DEFAULT_RESOLVE_API_URL.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("asin", asin)
            ?.addQueryParameter("country", country)
            ?.addQueryParameter("codec", codec)
            ?.build() ?: throw AmazonResolutionException("Could not build resolution URL")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Origin", "https://t2tunes.site")
            .header("Referer", "https://t2tunes.site/")
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw AmazonResolutionException("Resolution failed with code ${response.code}")
            val body = response.body.string()
            val jsonArr = runCatching { JSONArray(body) }.getOrElse {
                val obj = runCatching { JSONObject(body) }.getOrNull()
                when {
                    obj == null -> JSONArray()
                    obj.has("data") && obj.opt("data") is JSONArray -> obj.optJSONArray("data") ?: JSONArray()
                    else -> JSONArray().apply { put(obj) }
                }
            }

            val first = jsonArr.optJSONObject(0) ?: throw AmazonResolutionException("Empty resolution response")
            val dataObj = first.optJSONObject("data")
            val streamInfo = first.optJSONObject("streamInfo")
            val streamUrl = first.optString("streamUrl").takeIf { it.isNotEmpty() }
                ?: first.optString("url").takeIf { it.isNotEmpty() }
                ?: streamInfo?.optString("streamUrl")?.takeIf { it.isNotEmpty() }
                ?: streamInfo?.optString("url")?.takeIf { it.isNotEmpty() }
                ?: dataObj?.optString("streamUrl")?.takeIf { it.isNotEmpty() }
                ?: dataObj?.optString("url")?.takeIf { it.isNotEmpty() }
                ?: throw AmazonResolutionException("No stream URL returned")

            val decryptionKey = (first.optString("decryptionKey").takeIf { it.isNotEmpty() }
                ?: first.optString("key").takeIf { it.isNotEmpty() }
                ?: streamInfo?.optString("decryptionKey")?.takeIf { it.isNotEmpty() }
                ?: streamInfo?.optString("key")?.takeIf { it.isNotEmpty() }
                ?: dataObj?.optString("decryptionKey")?.takeIf { it.isNotEmpty() }
                ?: dataObj?.optString("key")?.takeIf { it.isNotEmpty() })

            val sampleRate = streamInfo?.optInt("sampleRate")?.takeIf { it > 0 } ?: 44100
            val expiresAtMs = System.currentTimeMillis() + STREAM_CACHE_MS

            Resolved(
                mediaUri = streamUrl,
                trackId = asin,
                label = "Amazon Music",
                mimeType = "audio/flac",
                codecs = "flac",
                bitrate = if (sampleRate > 44100) 2400 else 1411,
                sampleRate = sampleRate,
                expiresAtMs = expiresAtMs,
                decryptionKey = decryptionKey,
            )
        }
    }

    private fun ensureSession() {
        val now = System.currentTimeMillis()
        if (isInitialized && (now - sessionFetchedAtMs < 60 * 60 * 1000L)) return
        synchronized(initLock) {
            if (isInitialized && (now - sessionFetchedAtMs < 60 * 60 * 1000L)) return
            deviceId = "spotui_" + java.util.UUID.randomUUID().toString().replace("-", "").take(16)
            sessionId = java.util.UUID.randomUUID().toString()
            isInitialized = true
            sessionFetchedAtMs = now
        }
    }

    private fun buildHeaders(referer: String): Map<String, String> {
        val ts = System.currentTimeMillis().toString()
        return mapOf(
            "User-Agent" to BROWSER_USER_AGENT,
            "Referer" to referer,
            "x-amzn-device-id" to (deviceId ?: ""),
            "x-amzn-session-id" to (sessionId ?: ""),
            "x-amzn-timestamp" to ts,
            "x-amzn-territory" to musicTerritory,
            "x-amzn-app-version" to appVersion,
        )
    }

    private fun textValue(obj: Any?): String = when (obj) {
        is String -> obj.trim()
        is JSONObject -> obj.optString("text").trim()
        else -> ""
    }

    private fun parseDurationMMSS(str: String): Int {
        if (str.isBlank()) return 0
        val parts = str.split(':').mapNotNull { it.trim().toIntOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0
        }
    }

    private fun String.cleanSearchTitle(): String =
        Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .replace(Regex("""[^\w\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .lowercase(Locale.US)

    private fun String.cleanSearchArtist(): String = cleanSearchTitle()

    private fun similarityScore(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.contains(s2) || s2.contains(s1)) return 0.8
        val words1 = s1.split(" ").filter { it.length > 2 }.toSet()
        val words2 = s2.split(" ").filter { it.length > 2 }.toSet()
        if (words1.isEmpty() || words2.isEmpty()) return 0.0
        val inter = words1.intersect(words2).size
        return inter.toDouble() / maxOf(words1.size, words2.size)
    }
}
