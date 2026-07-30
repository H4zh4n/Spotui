package com.metrolist.spotify

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Lossless (FLAC) resolver, ported from the open-source **SpotiFLAC** project.
 *
 * SpotiFLAC is *not* a Spotify ripper — it takes a Spotify track, finds the same
 * recording on a lossless service (Tidal / Qobuz / Amazon Music) by id/ISRC, and
 * returns a directly streamable/downloadable FLAC URL from that service via the
 * project's free "community" proxy servers.
 */
object SpotiFlac {

    data class LosslessTrack(
        val url: String,
        val provider: String,   // "tidal" | "qobuz" | "amazon" | "deezer"
        val quality: String,    // "24" (hi-res) | "16" (CD)
        val container: String = "flac",
    )

    sealed interface Result {
        data class Success(val track: LosslessTrack) : Result
        /** Every candidate provider is currently throttled — try again later. */
        data class Cooldown(val message: String) : Result
        /** No lossless match exists for this track on any provider. */
        data object NotFound : Result
        data class Error(val message: String) : Result
    }

    // ── Community proxy config (from SpotiFLAC) ──────────────────────────────
    private const val API_KEY = "explore-obscure-chivalry-travesty-blinks"
    private const val TIDAL_BASE = "https://tdl-foss.spotbye.qzz.io"
    private const val QOBUZ_BASE = "https://qbz-foss.spotbye.qzz.io"
    private const val AMAZON_BASE = "https://amz-foss.spotbye.qzz.io"
    private const val DEEZER_BASE = "https://dz-foss.spotbye.qzz.io"
    private const val DL_PATH = "/api/dl"
    private const val UA = "SpotiFLAC"

    // Multi-tier caching durations
    private const val TRACK_CACHE_MS = 60 * 60 * 1000L      // 60 min
    private const val STREAM_CACHE_MS = 45 * 60 * 1000L     // 45 min
    private const val FAILURE_CACHE_MS = 15 * 60 * 1000L    // 15 min

    private const val HIFI_UPTIME_URL = "https://tidal-uptime.geeked.wtf"

    private val TIDAL_MONOCHROME_INSTANCES = listOf(
        "https://api.monochrome.tf",
        "https://monochrome-api.samidy.com",
        "https://hifi.geeked.wtf",
        "https://wolf.qqdl.site",
        "https://maus.qqdl.site",
        "https://vogel.qqdl.site",
        "https://katze.qqdl.site",
        "https://hund.qqdl.site",
        "https://tidal.kinoplus.online",
        "https://eu-central.monochrome.tf",
        "https://us-west.monochrome.tf",
        "https://arran.monochrome.tf",
        "https://triton.squid.wtf",
    )

    @Volatile private var cachedInstances: List<String>? = null
    @Volatile private var cachedInstancesAt: Long = 0L

    private suspend fun monochromeInstances(): List<String> {
        cachedInstances?.let {
            if (System.currentTimeMillis() - cachedInstancesAt < 5 * 60_000L) return it
        }
        val live = runCatching {
            val resp = client.get(HIFI_UPTIME_URL) { header("User-Agent", UA) }
            if (resp.status.value !in 200..299) return@runCatching emptyList<String>()
            val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
            val arr = (root["api"] as? JsonArray) ?: (root["apis"] as? JsonArray)
                ?: (root["instances"] as? JsonArray) ?: JsonArray(emptyList())
            arr.mapNotNull { el ->
                when (el) {
                    is JsonObject -> {
                        val up = el["up"]?.jsonPrimitive?.booleanOrNull
                            ?: el["online"]?.jsonPrimitive?.booleanOrNull ?: true
                        if (up) el["url"]?.jsonPrimitive?.contentOrNull else null
                    }
                    else -> el.jsonPrimitive.contentOrNull
                }
            }.mapNotNull { it.takeIf { u -> u.startsWith("http") }?.trimEnd('/') }
        }.getOrNull().orEmpty()
        return (live + TIDAL_MONOCHROME_INSTANCES).distinct().also {
            cachedInstances = it
            cachedInstancesAt = System.currentTimeMillis()
        }
    }

    private const val QOBUZ_APP_ID = "712109809"
    private const val QOBUZ_APP_SECRET = "589be88e4538daea11f509d29e4a23b1"
    private const val QOBUZ_API_BASE = "https://www.qobuz.com/api.json/0.2"

    private val json = Json { isLenient = true; ignoreUnknownKeys = true; coerceInputValues = true }

    @Volatile
    var logger: ((level: String, message: String) -> Unit)? = null
    private fun log(level: String, msg: String) = logger?.invoke(level, "SpotiFlac: $msg")

    private val client by lazy {
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(5, TimeUnit.SECONDS)
                    readTimeout(10, TimeUnit.SECONDS)
                    writeTimeout(5, TimeUnit.SECONDS)
                }
            }
            expectSuccess = false
        }
    }

    private const val STATUS_URL = "https://spotbye.qzz.io/api/status"
    private val allProviders = setOf("tidal", "qobuz", "amazon", "deezer")
    private val providerCooldowns = ConcurrentHashMap<String, Long>()
    @Volatile private var upProvidersCache: Set<String>? = null
    @Volatile private var upProvidersAt = 0L

    // Multi-tier caches
    private data class CachedTrack(
        val track: LosslessTrack,
        val expiresAtMs: Long,
    )
    private data class CachedFailure(
        val message: String,
        val expiresAtMs: Long,
    )
    private data class CachedProviderIds(
        val ids: ProviderIds,
        val expiresAtMs: Long,
    )

    private val trackIdCache = ConcurrentHashMap<String, CachedProviderIds>()
    private val streamCache = ConcurrentHashMap<String, CachedTrack>()
    private val streamFailureCache = ConcurrentHashMap<String, CachedFailure>()

    suspend fun upLosslessProviders(): Set<String> {
        upProvidersCache?.let { if (System.currentTimeMillis() - upProvidersAt < 60_000L) return it }
        val up = runCatching {
            val r = client.get(STATUS_URL) { header("User-Agent", UA) }
            if (r.status.value !in 200..299) return@runCatching null
            val obj = json.parseToJsonElement(r.bodyAsText()).jsonObject

            val spotiflacUp = obj["spotiflac"]
                ?.jsonObject?.get("status")?.jsonObject
                ?.filterValues { it.jsonPrimitive.contentOrNull.equals("up", true) }
                ?.keys?.toSet().orEmpty()

            val nextUp = obj["next"]
                ?.jsonObject?.get("status")?.jsonObject
                ?.filterValues { it.jsonPrimitive.contentOrNull.equals("up", true) }
                ?.keys?.map { key -> key.substringBefore("_") }?.toSet().orEmpty()

            val combined = spotiflacUp + nextUp
            if (combined.isEmpty()) null else combined
        }.getOrNull()

        val result = up ?: allProviders
        upProvidersCache = result
        upProvidersAt = System.currentTimeMillis()
        return result
    }

    data class ProviderStatus(
        val id: String,
        val name: String,
        val isUp: Boolean,
        val isCooldown: Boolean,
        val cooldownRemainingSec: Int = 0,
        val detail: String = "",
    )

    fun clearStatusCache() {
        upProvidersCache = null
        upProvidersAt = 0L
    }

    suspend fun getProviderStatuses(): List<ProviderStatus> {
        val up = upLosslessProviders()
        val now = System.currentTimeMillis()

        val providers = listOf(
            Triple("tidal", "Tidal (Community)", "High-res & CD quality FLAC"),
            Triple("qobuz", "Qobuz (Community)", "24-bit Hi-Res & CD quality FLAC via ISRC"),
            Triple("amazon", "Amazon Music (Community)", "24-bit Ultra HD FLAC"),
            Triple("deezer", "Deezer (Community)", "16-bit CD quality FLAC via ISRC"),
        )

        val list = mutableListOf<ProviderStatus>()

        for ((id, name, desc) in providers) {
            val isUp = id in up
            val cooldownUntil = providerCooldowns[id] ?: 0L
            val isCooldown = cooldownUntil > now
            val cdSec = if (isCooldown) ((cooldownUntil - now) / 1000).toInt() else 0
            list.add(
                ProviderStatus(
                    id = id,
                    name = name,
                    isUp = isUp,
                    isCooldown = isCooldown,
                    cooldownRemainingSec = cdSec,
                    detail = desc,
                )
            )
        }

        val monoInstances = runCatching { monochromeInstances() }.getOrDefault(emptyList())
        list.add(
            ProviderStatus(
                id = "monochrome",
                name = "Tidal (Monochrome)",
                isUp = monoInstances.isNotEmpty(),
                isCooldown = false,
                detail = "${monoInstances.size} live public mirror instances",
            )
        )

        return list
    }

    suspend fun anyLosslessServerUp(): Boolean = upLosslessProviders().isNotEmpty()

    /**
     * Resolve a lossless FLAC URL for a Spotify track.
     * Checks multi-tier streamCache, trackIdCache, and streamFailureCache.
     */
    suspend fun resolve(
        spotifyTrackId: String,
        isrc: String?,
        preferHiRes: Boolean = true,
    ): Result = coroutineScope {
        val now = System.currentTimeMillis()
        val quality = if (preferHiRes) "24" else "16"
        val streamCacheKey = "$spotifyTrackId::$quality"

        // 1. Check 45-min streamCache
        streamCache[streamCacheKey]?.takeIf { it.expiresAtMs > now }?.let { cached ->
            log("D", "streamCache hit for $spotifyTrackId ($quality)")
            return@coroutineScope Result.Success(cached.track)
        }

        val upProvidersDeferred = async { upLosslessProviders() }
        val idsDeferred = async {
            // 2. Check 60-min trackIdCache
            val cachedIds = trackIdCache[spotifyTrackId]?.takeIf { it.expiresAtMs > now }?.ids
            if (cachedIds != null) {
                cachedIds
            } else {
                val resolved = runCatching { resolveProviderIds(spotifyTrackId, isrc) }.getOrDefault(ProviderIds())
                trackIdCache[spotifyTrackId] = CachedProviderIds(resolved, now + TRACK_CACHE_MS)
                resolved
            }
        }

        val upProviders = upProvidersDeferred.await()
        if (upProviders.isEmpty()) {
            log("D", "all lossless servers down — skipping lossless")
            return@coroutineScope Result.NotFound
        }

        val ids = idsDeferred.await()
        val candidates = mutableListOf<suspend () -> Result>()

        // 3. Skip candidates if streamFailureCache or providerCooldowns are active
        val tidalFailKey = "tidal::${ids.tidalId}"
        if ((providerCooldowns["tidal"] ?: 0L) <= now && (streamFailureCache[tidalFailKey]?.expiresAtMs ?: 0L) <= now && !ids.tidalId.isNullOrBlank()) {
            candidates.add { resolveTidalMonochrome(ids.tidalId, preferHiRes) }
            if ("tidal" in upProviders) {
                candidates.add { communityDownload("tidal", TIDAL_BASE, ids.tidalId, quality) }
            }
        }

        val qobuzFailKey = "qobuz::${ids.qobuzId}"
        if ((providerCooldowns["qobuz"] ?: 0L) <= now && (streamFailureCache[qobuzFailKey]?.expiresAtMs ?: 0L) <= now && !ids.qobuzId.isNullOrBlank() && "qobuz" in upProviders) {
            candidates.add { communityDownload("qobuz", QOBUZ_BASE, ids.qobuzId, quality) }
        }

        val amazonFailKey = "amazon::${ids.amazonId}"
        if ((providerCooldowns["amazon"] ?: 0L) <= now && (streamFailureCache[amazonFailKey]?.expiresAtMs ?: 0L) <= now && !ids.amazonId.isNullOrBlank() && "amazon" in upProviders) {
            candidates.add { communityDownload("amazon", AMAZON_BASE, ids.amazonId, quality) }
        }

        val deezerFailKey = "deezer::${ids.deezerId}"
        if ((providerCooldowns["deezer"] ?: 0L) <= now && (streamFailureCache[deezerFailKey]?.expiresAtMs ?: 0L) <= now && !ids.deezerId.isNullOrBlank() && "deezer" in upProviders) {
            candidates.add { communityDownload("deezer", DEEZER_BASE, ids.deezerId, quality) }
        }

        if (candidates.isEmpty()) {
            return@coroutineScope Result.NotFound
        }

        val channel = Channel<Result>(candidates.size)
        val jobs = candidates.map { task ->
            launch {
                val r = withTimeoutOrNull(2500L) { task() } ?: Result.NotFound
                channel.send(r)
            }
        }

        var winner: Result.Success? = null
        var lastCooldown: Result.Cooldown? = null
        var receivedCount = 0

        for (r in channel) {
            receivedCount++
            when (r) {
                is Result.Success -> {
                    winner = r
                    jobs.forEach { it.cancel() }
                    break
                }
                is Result.Cooldown -> {
                    lastCooldown = r
                }
                else -> Unit
            }
            if (receivedCount >= candidates.size) break
        }

        val finalResult = winner ?: lastCooldown ?: Result.NotFound

        if (finalResult is Result.Success) {
            streamCache[streamCacheKey] = CachedTrack(finalResult.track, now + STREAM_CACHE_MS)
        } else if (finalResult is Result.Error || finalResult is Result.Cooldown) {
            streamFailureCache[streamCacheKey] = CachedFailure(
                message = (finalResult as? Result.Error)?.message ?: "cooldown",
                expiresAtMs = now + FAILURE_CACHE_MS,
            )
        }

        finalResult
    }

    private data class ProviderIds(
        val tidalId: String? = null,
        val amazonId: String? = null,
        val qobuzId: String? = null,
        val deezerId: String? = null,
    )

    private suspend fun resolveProviderIds(spotifyTrackId: String, isrc: String?): ProviderIds = coroutineScope {
        val odesliDeferred = async {
            runCatching {
                val resp = client.get("https://api.song.link/v1-alpha.1/links") {
                    parameter("url", "spotify:track:$spotifyTrackId")
                    header("User-Agent", "Mozilla/5.0")
                }
                if (resp.status.value in 200..299) {
                    json.parseToJsonElement(resp.bodyAsText()).jsonObject
                } else null
            }.getOrNull()
        }

        val qobuzDeferred = async {
            isrc?.takeIf { it.isNotBlank() }?.let { qobuzIdForIsrc(it) }
        }

        val deezerIsrcDeferred = async {
            isrc?.takeIf { it.isNotBlank() }?.let { deezerIdForIsrc(it) }
        }

        val odesli = odesliDeferred.await()
        val qobuzId = qobuzDeferred.await()
        var deezerId: String? = null
        var tidalId: String? = null
        var amazonId: String? = null

        odesli?.get("linksByPlatform")?.jsonObject?.let { platforms ->
            tidalId = entityId(platforms, "tidal", "TIDAL_SONG::")
            amazonId = entityId(platforms, "amazonMusic", "AMAZON_SONG::")
            deezerId = entityId(platforms, "deezer", "DEEZER_SONG::")
        }

        if (deezerId == null) {
            deezerId = deezerIsrcDeferred.await()
        }

        log("D", "ids tidal=$tidalId amazon=$amazonId qobuz=$qobuzId deezer=$deezerId")
        ProviderIds(tidalId = tidalId, amazonId = amazonId, qobuzId = qobuzId, deezerId = deezerId)
    }

    private suspend fun deezerIdForIsrc(isrc: String): String? = runCatching {
        val resp = client.get("https://api.deezer.com/2.0/track/isrc:${isrc.trim()}") {
            header("User-Agent", "Mozilla/5.0")
            header("Accept", "application/json")
        }
        if (resp.status.value !in 200..299) return@runCatching null
        val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
        root["id"]?.jsonPrimitive?.contentOrNull
    }.getOrElse { log("W", "deezer isrc search failed: ${it.message}"); null }

    private fun entityId(platforms: JsonObject, platform: String, prefix: String): String? {
        val unique = platforms[platform]?.jsonObject?.get("entityUniqueId")
            ?.jsonPrimitive?.contentOrNull ?: return null
        return unique.substringAfter("::").takeIf { it.isNotBlank() && it != unique }
    }

    private suspend fun qobuzIdForIsrc(isrc: String): String? = runCatching {
        val params = sortedMapOf("query" to isrc.trim(), "limit" to "1")
        val ts = (System.currentTimeMillis() / 1000).toString()
        val sigPayload = buildString {
            append("tracksearch")
            params.forEach { (k, v) -> append(k); append(v) }
            append(ts)
            append(QOBUZ_APP_SECRET)
        }
        val sig = md5Hex(sigPayload)
        val resp = client.get("$QOBUZ_API_BASE/track/search") {
            parameter("query", isrc.trim())
            parameter("limit", "1")
            parameter("app_id", QOBUZ_APP_ID)
            parameter("request_ts", ts)
            parameter("request_sig", sig)
            header("User-Agent", "Mozilla/5.0")
            header("X-App-Id", QOBUZ_APP_ID)
            header("Accept", "application/json")
        }
        val items = json.parseToJsonElement(resp.bodyAsText()).jsonObject
            .get("tracks")?.jsonObject?.get("items")?.let { it as? JsonArray }
        items?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
    }.getOrElse { log("W", "qobuz isrc search failed: ${it.message}"); null }

    private suspend fun communityDownload(
        provider: String,
        base: String,
        id: String,
        quality: String,
    ): Result {
        var resp: HttpResponse? = null
        val maxAttempts = 3
        for (attempt in 1..maxAttempts) {
            val r = runCatching {
                client.post("$base$DL_PATH") {
                    header("x-api-key", API_KEY)
                    header("User-Agent", UA)
                    header("Accept", "application/json")
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(JsonObject.serializer(), buildJsonObject {
                        put("id", id)
                        put("quality", quality)
                    }))
                }
            }.getOrElse { return Result.Error(it.message ?: "network error") }

            if (r.status.value == 503) {
                val msg = runCatching {
                    json.parseToJsonElement(runCatching { r.bodyAsText() }.getOrDefault(""))
                        .jsonObject["detail"]?.jsonPrimitive?.contentOrNull
                }.getOrNull() ?: "Lossless servers are busy. Try again shortly."
                providerCooldowns[provider] = System.currentTimeMillis() + 60_000L
                log("W", "$provider on cooldown (503) — backing off $provider for 60s")
                return Result.Cooldown(msg)
            }
            if (r.status.value in intArrayOf(429, 502, 504) && attempt < maxAttempts) {
                val retryAfter = r.headers["Retry-After"]?.toIntOrNull()?.takeIf { it in 1..3 }
                val waitMs = (retryAfter ?: attempt) * 1000L
                log("W", "$provider transient ${r.status.value}, retrying in ${waitMs}ms ($attempt/$maxAttempts)")
                kotlinx.coroutines.delay(waitMs)
                continue
            }
            resp = r
            break
        }
        val response = resp ?: return Result.Error("$provider unavailable")

        val bodyText = runCatching { response.bodyAsText() }.getOrDefault("")
        if (response.status.value !in 200..299) {
            return Result.Error("$provider HTTP ${response.status.value}")
        }

        val track = communityTrackFrom(bodyText, provider, quality)
            ?: return Result.NotFound.also { log("W", "$provider: no usable url in response") }
        providerCooldowns.remove(provider)
        val q = if (quality == "24") "24-bit" else "16-bit"
        log("D", "$provider FLAC resolved ($q, ${track.container})")
        return Result.Success(track)
    }

    private suspend fun resolveTidalMonochrome(tidalId: String, @Suppress("UNUSED_PARAMETER") preferHiRes: Boolean): Result {
        var sawError = false
        for (base in monochromeInstances()) {
            flacViaTrackManifests(base, tidalId)?.let {
                log("D", "tidal FLAC via $base/trackManifests (${it.container})")
                return Result.Success(it)
            }
            flacViaTrack(base, tidalId)?.let {
                log("D", "tidal FLAC via $base/track (${it.container})")
                return Result.Success(it)
            }
            sawError = true
        }
        return if (sawError) Result.Error("Tidal backends unavailable") else Result.NotFound
    }

    private suspend fun flacViaTrackManifests(base: String, tidalId: String): LosslessTrack? {
        val lookup = runCatching {
            client.get("$base/trackManifests/") {
                parameter("id", tidalId)
                parameter("quality", "LOSSLESS")
                parameter("adaptive", "false")
                parameter("formats", "FLAC")
                header("User-Agent", UA)
                header("Accept", "application/json")
            }
        }.getOrNull() ?: return null
        if (lookup.status.value !in 200..299) return null
        val manifestUri = extractManifestUri(runCatching { lookup.bodyAsText() }.getOrDefault("")) ?: return null
        val manifestResp = runCatching { client.get(manifestUri) { header("User-Agent", UA) } }.getOrNull() ?: return null
        if (manifestResp.status.value !in 200..299) return null
        return trackFromManifest(runCatching { manifestResp.bodyAsText() }.getOrDefault(""), manifestUri)
    }

    private suspend fun flacViaTrack(base: String, tidalId: String): LosslessTrack? {
        val resp = runCatching {
            client.get("$base/track/") {
                parameter("id", tidalId)
                parameter("quality", "LOSSLESS")
                parameter("country", "US")
                header("User-Agent", UA)
                header("Accept", "application/json")
            }
        }.getOrNull() ?: return null
        if (resp.status.value !in 200..299) return null
        val body = runCatching { resp.bodyAsText() }.getOrDefault("")
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val data = (root["data"] as? JsonObject) ?: root
        fun JsonObject.httpUrl(key: String) =
            this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.startsWith("http") }
        (data.httpUrl("OriginalTrackUrl") ?: data.httpUrl("originalTrackUrl") ?: data.httpUrl("url"))?.let {
            return LosslessTrack(url = it, provider = "tidal", quality = "16", container = "flac")
        }
        val manifest = (data["manifest"] ?: root["manifest"])?.jsonPrimitive?.contentOrNull ?: return null
        val decoded = runCatching { String(java.util.Base64.getMimeDecoder().decode(manifest)) }.getOrElse { manifest }
        return flacUrlFromManifest(decoded)?.let {
            LosslessTrack(url = it, provider = "tidal", quality = "16", container = "flac")
        }
    }

    private fun trackFromManifest(manifestText: String, manifestUri: String): LosslessTrack? {
        if (manifestText.isBlank()) return null
        if (manifestText.contains("<MPD")) {
            val durSec = mpdDurationSeconds(manifestText)
            if (durSec != null && durSec < 45.0) {
                log("W", "tidal manifest is a ${durSec.toInt()}s preview — skipping")
                return null
            }
            return LosslessTrack(url = manifestUri, provider = "tidal", quality = "16", container = "dash")
        }
        return flacUrlFromManifest(manifestText)?.let {
            LosslessTrack(url = it, provider = "tidal", quality = "16", container = "flac")
        }
    }

    private fun mpdDurationSeconds(mpd: String): Double? {
        val m = Regex("mediaPresentationDuration=\"PT(?:([0-9]+)H)?(?:([0-9]+)M)?([0-9.]+)S\"").find(mpd)
            ?: return null
        val h = m.groupValues[1].toDoubleOrNull() ?: 0.0
        val min = m.groupValues[2].toDoubleOrNull() ?: 0.0
        val s = m.groupValues[3].toDoubleOrNull() ?: 0.0
        return h * 3600 + min * 60 + s
    }

    private fun extractManifestUri(body: String): String? {
        if (body.isBlank()) return null
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val candidates = listOf(
            root["data"]?.let { it as? JsonObject }?.get("data")?.let { it as? JsonObject },
            root["data"]?.let { it as? JsonObject },
            root,
        )
        for (node in candidates) {
            val uri = (node?.get("attributes") as? JsonObject)
                ?.get("uri")?.jsonPrimitive?.contentOrNull
            if (!uri.isNullOrBlank() && uri.startsWith("http")) return uri
        }
        return null
    }

    private fun flacUrlFromManifest(manifestText: String): String? {
        if (manifestText.isBlank() || manifestText.contains("<MPD")) return null
        fun urlsFrom(text: String): List<String> = runCatching {
            (json.parseToJsonElement(text).jsonObject["urls"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
        }.getOrNull().orEmpty()

        urlsFrom(manifestText).takeIf { it.isNotEmpty() }?.let { return pickBestLosslessUrl(it) }
        val decoded = runCatching {
            String(java.util.Base64.getMimeDecoder().decode(manifestText.trim()))
        }.getOrNull()
        if (decoded != null && !decoded.contains("<MPD")) {
            urlsFrom(decoded).takeIf { it.isNotEmpty() }?.let { return pickBestLosslessUrl(it) }
        }
        return Regex("https?://[^\"\\s]+").find(manifestText)?.value
    }

    private fun pickBestLosslessUrl(urls: List<String>): String {
        val keywords = listOf("flac", "lossless", "hi-res", "high")
        return urls.minByOrNull { url ->
            val low = url.lowercase()
            keywords.indexOfFirst { low.contains(it) }.let { if (it == -1) 999 else it }
        } ?: urls.first()
    }

    private fun communityTrackFrom(body: String, provider: String, quality: String): LosslessTrack? {
        if (body.isBlank()) return null
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        fun JsonObject.raw(key: String) = this[key]?.jsonPrimitive?.contentOrNull
        val rawUrl = obj.raw("url") ?: obj.raw("download_url")
            ?: (obj["data"] as? JsonObject)?.let { it.raw("url") ?: it.raw("download_url") }
            ?: return null

        if (rawUrl.startsWith("http")) {
            return LosslessTrack(url = rawUrl, provider = provider, quality = quality, container = "flac")
        }
        if (rawUrl.startsWith("MANIFEST:")) {
            val b64 = rawUrl.substring("MANIFEST:".length).trim()
            val decoded = runCatching {
                String(java.util.Base64.getMimeDecoder().decode(b64))
            }.getOrNull() ?: return null
            if (decoded.contains("<MPD")) {
                val durSec = mpdDurationSeconds(decoded)
                if (durSec != null && durSec < 45.0) {
                    log("W", "$provider manifest is a ${durSec.toInt()}s preview — skipping")
                    return null
                }
                return LosslessTrack(
                    url = "data:application/dash+xml;base64,$b64",
                    provider = provider, quality = quality, container = "dash",
                )
            }
            return flacUrlFromManifest(decoded)?.let {
                LosslessTrack(url = it, provider = provider, quality = quality, container = "flac")
            }
        }
        return null
    }

    suspend fun downloadDashFlacToFile(streamUrl: String, outFile: java.io.File): Boolean = runCatching {
        val mpd = dashMpdFromUrl(streamUrl) ?: return false
        val initUrl = htmlUnescape(Regex("initialization=\"([^\"]+)\"").find(mpd)?.groupValues?.get(1) ?: return false)
        val mediaTmpl = htmlUnescape(Regex("media=\"([^\"]+)\"").find(mpd)?.groupValues?.get(1) ?: return false)
        val startNumber = Regex("startNumber=\"(\\d+)\"").find(mpd)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val segCount = dashSegmentCount(mpd)
        if (segCount <= 0) return false

        val initBytes = httpBytes(initUrl) ?: return false
        val streamInfo = flacStreamInfoFromInit(initBytes) ?: return false

        java.io.BufferedOutputStream(java.io.FileOutputStream(outFile)).use { out ->
            out.write(byteArrayOf(0x66, 0x4c, 0x61, 0x43))
            out.write(0x80)
            out.write((streamInfo.size ushr 16) and 0xFF)
            out.write((streamInfo.size ushr 8) and 0xFF)
            out.write(streamInfo.size and 0xFF)
            out.write(streamInfo)
            for (n in startNumber until startNumber + segCount) {
                val seg = httpBytes(mediaTmpl.replace("\$Number\$", n.toString())) ?: return false
                writeMdatPayloads(seg, out)
            }
        }
        log("D", "tidal DASH remuxed to FLAC (${segCount} segments)")
        true
    }.getOrElse { log("W", "DASH remux failed: ${it.message}"); runCatching { outFile.delete() }; false }

    private fun dashMpdFromUrl(url: String): String? = when {
        url.startsWith("data:application/dash+xml;base64,") ->
            runCatching { String(java.util.Base64.getMimeDecoder().decode(url.substringAfter("base64,"))) }.getOrNull()
        url.contains("<MPD") -> url
        else -> null
    }

    private fun dashSegmentCount(mpd: String): Int {
        var total = 0
        for (m in Regex("<S\\b[^>]*/?>").findAll(mpd)) {
            total += 1 + (Regex("\\br=\"(\\d+)\"").find(m.value)?.groupValues?.get(1)?.toIntOrNull() ?: 0)
        }
        return total
    }

    private fun flacStreamInfoFromInit(init: ByteArray): ByteArray? {
        val tag = byteArrayOf(0x64, 0x66, 0x4c, 0x61)
        val k = indexOf(init, tag)
        if (k < 0 || k + 12 > init.size) return null
        val len = ((init[k + 9].toInt() and 0xFF) shl 16) or
            ((init[k + 10].toInt() and 0xFF) shl 8) or (init[k + 11].toInt() and 0xFF)
        if (len <= 0 || k + 12 + len > init.size) return null
        return init.copyOfRange(k + 12, k + 12 + len)
    }

    private fun writeMdatPayloads(seg: ByteArray, out: java.io.OutputStream) {
        var i = 0
        while (i + 8 <= seg.size) {
            var size = ((seg[i].toLong() and 0xFF) shl 24) or ((seg[i + 1].toLong() and 0xFF) shl 16) or
                ((seg[i + 2].toLong() and 0xFF) shl 8) or (seg[i + 3].toLong() and 0xFF)
            var hdr = 8
            if (size == 1L) {
                if (i + 16 > seg.size) break
                size = (0..7).fold(0L) { acc, b -> (acc shl 8) or (seg[i + 8 + b].toLong() and 0xFF) }
                hdr = 16
            } else if (size == 0L) {
                size = (seg.size - i).toLong()
            }
            if (size < hdr) break
            if (seg[i + 4] == 0x6D.toByte() && seg[i + 5] == 0x64.toByte() &&
                seg[i + 6] == 0x61.toByte() && seg[i + 7] == 0x74.toByte()
            ) {
                out.write(seg, i + hdr, (size - hdr).toInt())
            }
            i += size.toInt()
        }
    }

    private suspend fun httpBytes(url: String): ByteArray? = runCatching {
        val r = client.get(url) { header("User-Agent", UA) }
        if (r.status.value !in 200..299) null else r.readBytes()
    }.getOrNull()

    private fun htmlUnescape(s: String): String =
        s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    private fun md5Hex(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
