package com.music.spotui.lossless

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Endpoint registry for the community FLAC backends (Tidal etc.). SpotiFLAC keeps
 * the current backend hostnames in an AES-GCM-encrypted JSON blob on a public
 * gist and rotates them as servers come and go; this fetches + decrypts that blob
 * so the source list stays current, and falls back to a known-good default if the
 * fetch fails.
 *
 * These are public community APIs (a plain `GET .../track/?id=…` with a shared,
 * published API key) — no login, no CAPTCHA, no per-user credentials.
 */
internal object LosslessRegistry {

    private const val TAG = "LosslessRegistry"

    // Community backend API key (shared/published constant in SpotiFLAC's sources).
    const val API_KEY = "ak_8e3f1a7c2b5d9e4f0a6c3b8d1e5f2a9c7b4d0e6f"

    // Signed-session base for the *gated* backends (experimental path 1a).
    const val SESSION_BASE = "https://api.zarz.moe/v2"

    /** Community (gated) download endpoint for a provider, e.g. tidal → .../api/dl. */
    fun communityDlUrl(provider: String): String? =
        registry()?.optJSONObject(provider)?.optString("community")?.takeIf { it.isNotBlank() }

    private const val GIST_URL =
        "https://gist.githubusercontent.com/BartolomeoRusso9/ef9fdbbc894818aea89d25a8d99f8c77/raw"

    // AES-GCM key material (matches SpotiFLAC's community-url scheme).
    private val SEED_PARTS = listOf("spotif", "lac:co", "mmunity:url:v1")
    private val AAD = "spotiflac|community|url|v1".toByteArray()

    // Known-good defaults (used until/if the registry refresh succeeds).
    private val DEFAULT_TIDAL = listOf("https://tidal.anandserver.cfd")
    private val DEFAULT_AMAZON = listOf("https://amazon.anandserver.cfd")
    private val DEFAULT_QOBUZ = listOf("https://qobuz.anandserver.cfd")

    private const val CACHE_TTL_MS = 30 * 60 * 1000L
    @Volatile private var cached: JSONObject? = null
    @Volatile private var cachedAt = 0L

    /** Current Tidal community endpoints, freshest first, default appended. */
    fun tidalEndpoints(): List<String> = endpoints("tidal", "stream", DEFAULT_TIDAL)

    /** Current Amazon community endpoints (self-resolve by Spotify id). */
    fun amazonEndpoints(): List<String> {
        val fromReg = registry()?.optJSONObject("amazon")
            ?.let { listOfNotNull(it.optString("antra").takeIf(String::isNotBlank)) }
            .orEmpty()
        return (fromReg + DEFAULT_AMAZON).map { it.trimEnd('/') }.distinct()
    }

    /** Current Qobuz community endpoints (need a numeric Qobuz id). */
    fun qobuzEndpoints(): List<String> = endpoints("qobuz", "stream", DEFAULT_QOBUZ)

    private fun endpoints(provider: String, category: String, default: List<String>): List<String> {
        val fromReg = registry()?.optJSONObject(provider)?.optJSONArray(category)?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
        }.orEmpty()
        return (fromReg + default).map { it.trimEnd('/') }.distinct()
    }

    private fun registry(): JSONObject? {
        val now = nowMs()
        cached?.let { if (now - cachedAt < CACHE_TTL_MS) return it }
        return runCatching {
            val text = URL("$GIST_URL?t=$now").let { url ->
                (url.openConnection() as HttpURLConnection).run {
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("User-Agent", "SpotiFLAC-Agent")
                    inputStream.bufferedReader().use { it.readText() }.also { disconnect() }
                }
            }
            val json = JSONObject(decrypt(text))
            cached = json
            cachedAt = now
            json
        }.onFailure { Log.d(TAG, "registry refresh failed, using defaults: ${it.message}") }
            .getOrNull() ?: cached
    }

    private fun decrypt(b64Input: String): String {
        var b = b64Input.filterNot { it.isWhitespace() }.replace('-', '+').replace('_', '/')
        while (b.length % 4 != 0) b += "="
        val raw = android.util.Base64.decode(b, android.util.Base64.DEFAULT)
        val nonce = raw.copyOfRange(0, 12)
        val body = raw.copyOfRange(12, raw.size)
        val md = MessageDigest.getInstance("SHA-256")
        SEED_PARTS.forEach { md.update(it.toByteArray()) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(md.digest(), "AES"), GCMParameterSpec(128, nonce))
            updateAAD(AAD)
        }
        return String(cipher.doFinal(body), Charsets.UTF_8)
    }

    // Avoids Date.now()-style calls that some sandboxes block; System.currentTimeMillis is fine on-device.
    private fun nowMs(): Long = System.currentTimeMillis()
}
