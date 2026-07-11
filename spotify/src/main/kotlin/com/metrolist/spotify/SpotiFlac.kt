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
import io.ktor.http.ContentType
import io.ktor.http.contentType
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

/**
 * Lossless (FLAC) resolver, ported from the open-source **SpotiFLAC** project.
 *
 * SpotiFLAC is *not* a Spotify ripper — it takes a Spotify track, finds the same
 * recording on a lossless service (Tidal / Qobuz / Amazon Music) by id/ISRC, and
 * returns a directly streamable/downloadable FLAC URL from that service via the
 * project's free "community" proxy servers.
 *
 * Pipeline:
 *  1. Resolve the Spotify track to provider ids via Odesli (song.link) — gives
 *     Tidal + Amazon ids directly. Qobuz is resolved from the track's ISRC via
 *     Qobuz's signed public search API.
 *  2. Ask the community proxy (`/api/dl`) for a FLAC URL, trying providers in
 *     order and skipping any that are on a rotating cooldown (HTTP 503).
 *
 * The proxy base URLs + API key are the same ones SpotiFLAC ships (obfuscated in
 * its binary); they are free community servers and are frequently rate-limited,
 * hence the multi-provider fallback and explicit [Result.Cooldown] state.
 */
object SpotiFlac {

    data class LosslessTrack(
        val url: String,
        val provider: String,   // "tidal" | "qobuz" | "amazon"
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
    private const val DL_PATH = "/api/dl"
    private const val UA = "SpotiFLAC"

    // ── Monochrome / squid.wtf TIDAL backends (public, no login required) ────
    // These proxy TIDAL and return a lossless FLAC URL for a TIDAL track id —
    // no user account or subscription needed. Instances are frequently up/down,
    // so we fail over across the list. (github.com/monochrome-music/monochrome)
    // Live JSON list of currently-up Hi-Fi API instances (fetched at runtime).
    private const val HIFI_UPTIME_URL = "https://tidal-uptime.geeked.wtf"

    // Static fallback Hi-Fi API instances (from monochrome INSTANCES.md + community).
    // The live uptime list is merged in front of this so the pool stays fresh/large.
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

    // Cache the live instance list for a few minutes so we don't refetch per track.
    @Volatile private var cachedInstances: List<String>? = null
    @Volatile private var cachedInstancesAt: Long = 0L

    /**
     * Current Hi-Fi API instances: the live "up" list from the uptime tracker merged
     * in front of the static fallback. Cached for 5 minutes. This is what lets the
     * resolver reach as many working providers as possible without a rebuild.
     */
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

    // ── Qobuz public API (for ISRC -> qobuz track id) ────────────────────────
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
                    connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                }
            }
            expectSuccess = false
        }
    }

    /**
     * Resolve a lossless FLAC URL for a Spotify track.
     * @param isrc the track's ISRC (used for Qobuz matching); may be null.
     * @param preferHiRes request 24-bit where available, else CD-quality 16-bit.
     */
    suspend fun resolve(
        spotifyTrackId: String,
        isrc: String?,
        preferHiRes: Boolean = true,
        // Downloads need one saveable file, so skip segmented DASH results (TIDAL
        // lossless) and let a single-file community proxy answer instead.
        singleFileOnly: Boolean = false,
    ): Result {
        val quality = if (preferHiRes) "24" else "16"
        val ids = runCatching { resolveProviderIds(spotifyTrackId, isrc) }
            .getOrElse {
                log("E", "id resolution failed: ${it.message}")
                ProviderIds()
            }

        var sawCooldown: String? = null
        var sawMatch = false

        // PRIMARY login-free path: resolve the TIDAL id (via Odesli) to a FLAC URL
        // through the monochrome / squid.wtf public backends. No account needed.
        ids.tidalId?.takeIf { it.isNotBlank() }?.let { tidalId ->
            sawMatch = true
            when (val r = resolveTidalMonochrome(tidalId, preferHiRes)) {
                // For downloads, a DASH result isn't a single saveable file — fall
                // through to a single-file community proxy instead of returning it.
                is Result.Success -> if (!(singleFileOnly && r.track.container == "dash")) return r
                is Result.Cooldown -> sawCooldown = r.message
                is Result.Error -> log("W", "tidal (monochrome) error: ${r.message}")
                is Result.NotFound -> Unit
            }
        }

        // Order: Tidal & Amazon need only Odesli; Qobuz needs an ISRC match.
        val attempts = listOf(
            Triple("tidal", TIDAL_BASE, ids.tidalId),
            Triple("qobuz", QOBUZ_BASE, ids.qobuzId),
            Triple("amazon", AMAZON_BASE, ids.amazonId),
        )
        for ((provider, base, id) in attempts) {
            if (id.isNullOrBlank()) continue
            sawMatch = true
            when (val r = communityDownload(provider, base, id, quality)) {
                is Result.Success -> return r
                is Result.Cooldown -> sawCooldown = r.message
                is Result.NotFound -> Unit
                is Result.Error -> log("W", "$provider error: ${r.message}")
            }
        }

        return when {
            sawCooldown != null -> Result.Cooldown(sawCooldown)
            !sawMatch -> Result.NotFound
            else -> Result.NotFound
        }
    }

    private data class ProviderIds(
        val tidalId: String? = null,
        val amazonId: String? = null,
        val qobuzId: String? = null,
    )

    private suspend fun resolveProviderIds(spotifyTrackId: String, isrc: String?): ProviderIds {
        var tidalId: String? = null
        var amazonId: String? = null

        // Odesli: spotify track -> all-platform links/ids.
        val odesli = runCatching {
            val resp = client.get("https://api.song.link/v1-alpha.1/links") {
                parameter("url", "spotify:track:$spotifyTrackId")
                header("User-Agent", "Mozilla/5.0")
            }
            json.parseToJsonElement(resp.bodyAsText()).jsonObject
        }.getOrNull()

        odesli?.get("linksByPlatform")?.jsonObject?.let { platforms ->
            tidalId = entityId(platforms, "tidal", "TIDAL_SONG::")
            amazonId = entityId(platforms, "amazonMusic", "AMAZON_SONG::")
        }

        // Qobuz: resolve via ISRC signed search.
        val qobuzId = isrc?.takeIf { it.isNotBlank() }?.let { qobuzIdForIsrc(it) }

        log("D", "ids tidal=$tidalId amazon=$amazonId qobuz=$qobuzId")
        return ProviderIds(tidalId = tidalId, amazonId = amazonId, qobuzId = qobuzId)
    }

    private fun entityId(platforms: JsonObject, platform: String, prefix: String): String? {
        val unique = platforms[platform]?.jsonObject?.get("entityUniqueId")
            ?.jsonPrimitive?.contentOrNull ?: return null
        return unique.substringAfter("::").takeIf { it.isNotBlank() && it != unique }
    }

    private suspend fun qobuzIdForIsrc(isrc: String): String? = runCatching {
        val params = sortedMapOf("query" to isrc.trim(), "limit" to "1")
        val ts = (System.currentTimeMillis() / 1000).toString()
        val sigPayload = buildString {
            append("tracksearch") // normalized "track/search" with slashes removed
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
            .get("tracks")?.jsonObject?.get("items")?.let { it as? kotlinx.serialization.json.JsonArray }
        items?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
    }.getOrElse { log("W", "qobuz isrc search failed: ${it.message}"); null }

    private suspend fun communityDownload(
        provider: String,
        base: String,
        id: String,
        quality: String,
    ): Result {
        // Retry transient errors (429/502/504) with a short backoff — the proxies
        // are flaky-but-alive far more often than genuinely down. 503 is a real
        // scheduled cooldown, so we surface it instead of hammering.
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
                log("W", "$provider on cooldown (503)")
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

        val url = extractStreamUrl(bodyText)
            ?: return Result.NotFound.also { log("W", "$provider: no url in response") }
        val q = if (quality == "24") "24-bit" else "16-bit"
        log("D", "$provider FLAC resolved ($q)")
        return Result.Success(LosslessTrack(url = url, provider = provider, quality = quality))
    }

    /**
     * Resolve a TIDAL track id to a direct FLAC URL via the monochrome Hi-Fi API,
     * failing over across instances. Two-step flow (matches monochrome's client):
     *   1. GET /trackManifests/?id=&quality=LOSSLESS&adaptive=false&formats=FLAC
     *      → JSON with `data.data.attributes.uri` = a signed manifest URL.
     *   2. GET that uri → a BTS manifest (`{"urls":[...]}`) whose first url is a
     *      single-file FLAC. (Hi-res returns a segmented DASH `<MPD>` we can't use
     *      as one URL, so we request LOSSLESS for a directly-playable stream.)
     */
    private suspend fun resolveTidalMonochrome(tidalId: String, @Suppress("UNUSED_PARAMETER") preferHiRes: Boolean): Result {
        var sawError = false
        for (base in monochromeInstances()) {
            // Instances run one of two API versions, so try both endpoint styles.
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

    /** New Hi-Fi API: /trackManifests → signed manifest uri → FLAC/DASH stream. */
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

    /** Older hifi-api: /track/?id=&quality=LOSSLESS → OriginalTrackUrl or inline manifest. */
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

    /**
     * Turn a fetched TIDAL manifest into a playable track. A `<MPD>` is a segmented
     * DASH stream — we hand the manifest URL itself to ExoPlayer (container "dash").
     * A BTS JSON manifest yields a single-file FLAC URL (container "flac").
     */
    private fun trackFromManifest(manifestText: String, manifestUri: String): LosslessTrack? {
        if (manifestText.isBlank()) return null
        if (manifestText.contains("<MPD")) {
            // Limited/no-subscription backends serve 30-second previews. Reject them
            // so a full-access backend (or full-length YouTube) is used instead.
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

    /** Parse a DASH `mediaPresentationDuration="PT#H#M#S"` into seconds. */
    private fun mpdDurationSeconds(mpd: String): Double? {
        val m = Regex("mediaPresentationDuration=\"PT(?:([0-9]+)H)?(?:([0-9]+)M)?([0-9.]+)S\"").find(mpd)
            ?: return null
        val h = m.groupValues[1].toDoubleOrNull() ?: 0.0
        val min = m.groupValues[2].toDoubleOrNull() ?: 0.0
        val s = m.groupValues[3].toDoubleOrNull() ?: 0.0
        return h * 3600 + min * 60 + s
    }

    /** Pull `attributes.uri` (the signed manifest URL) out of a /trackManifests response. */
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

    /**
     * Extract a single FLAC URL from a fetched TIDAL manifest. LOSSLESS uses a BTS
     * JSON manifest `{"mimeType":"audio/flac","urls":[...]}`; segmented DASH (`<MPD>`)
     * can't be a single URL so we skip it. Some instances base64-wrap the JSON.
     */
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

    /** Prefer FLAC / lossless URLs when a manifest offers several. */
    private fun pickBestLosslessUrl(urls: List<String>): String {
        val keywords = listOf("flac", "lossless", "hi-res", "high")
        return urls.minByOrNull { url ->
            val low = url.lowercase()
            keywords.indexOfFirst { low.contains(it) }.let { if (it == -1) 999 else it }
        } ?: urls.first()
    }

    /** Pull a streamable URL out of the varied community-response shapes. */
    private fun extractStreamUrl(body: String): String? {
        if (body.isBlank()) return null
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        fun JsonObject.url(key: String) = this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.startsWith("http") }
        obj.url("url")?.let { return it }
        obj.url("download_url")?.let { return it }
        obj["data"]?.let { it as? JsonObject }?.let { data ->
            data.url("url")?.let { return it }
            data.url("download_url")?.let { return it }
        }
        return null
    }

    private fun md5Hex(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
