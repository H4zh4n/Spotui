package com.music.spotui.lyrics

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object KuGouLyricsProvider {
    private const val SEARCH_API = "https://krcs.kugou.com/search"
    private const val DOWNLOAD_API = "https://krcs.kugou.com/download"
    private const val USER_AGENT = "Spotui-Android/1.0"

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    data class KuGouCandidate(
        val id: String,
        val accessKey: String,
        val duration: Int,
        val score: Int,
    )

    suspend fun getLyrics(
        title: String,
        artist: String,
        durationSeconds: Int = -1,
    ): String? = withContext(Dispatchers.IO) {
        val keyword = "$title $artist".trim()
        val candidate = searchCandidate(keyword, durationSeconds) ?: return@withContext null
        downloadLyrics(candidate.id, candidate.accessKey)
    }

    private fun searchCandidate(keyword: String, durationSeconds: Int): KuGouCandidate? {
        val url = SEARCH_API.toHttpUrl()
            .newBuilder()
            .addQueryParameter("ver", "1")
            .addQueryParameter("man", "yes")
            .addQueryParameter("client", "pc")
            .addQueryParameter("keyword", keyword)
            .addQueryParameter("duration", if (durationSeconds > 0) (durationSeconds * 1000).toString() else "0")
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", USER_AGENT)
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body.string().takeIf { it.isNotBlank() } ?: return@use null
                val root = JSONObject(body)
                val candidates = root.optJSONArray("candidates") ?: return@use null
                if (candidates.length() == 0) return@use null

                var bestCandidate: KuGouCandidate? = null
                var bestScore = -1

                for (i in 0 until candidates.length()) {
                    val item = candidates.optJSONObject(i) ?: continue
                    val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val accessKey = item.optString("accesskey").takeIf { it.isNotBlank() } ?: continue
                    val durMs = item.optInt("duration", 0)
                    val score = item.optInt("score", 0)

                    if (durationSeconds > 0 && durMs > 0) {
                        val diffSec = abs((durMs / 1000) - durationSeconds)
                        if (diffSec > 15) continue
                    }

                    if (score > bestScore) {
                        bestScore = score
                        bestCandidate = KuGouCandidate(id = id, accessKey = accessKey, duration = durMs, score = score)
                    }
                }

                bestCandidate
            }
        }.getOrNull()
    }

    private fun downloadLyrics(id: String, accessKey: String): String? {
        val url = DOWNLOAD_API.toHttpUrl()
            .newBuilder()
            .addQueryParameter("ver", "1")
            .addQueryParameter("client", "pc")
            .addQueryParameter("id", id)
            .addQueryParameter("accesskey", accessKey)
            .addQueryParameter("fmt", "lrc")
            .addQueryParameter("charset", "utf8")
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", USER_AGENT)
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body.string().takeIf { it.isNotBlank() } ?: return@use null
                val root = JSONObject(body)
                val contentB64 = root.optString("content").takeIf { it.isNotBlank() } ?: return@use null
                val bytes = Base64.decode(contentB64, Base64.DEFAULT)
                String(bytes, Charsets.UTF_8)
            }
        }.getOrNull()
    }
}
