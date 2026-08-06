package com.music.spotui.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object LrcLibLyricsProvider {
    private const val API_BASE = "https://lrclib.net/api"
    private const val USER_AGENT = "Spotui-Android/1.0"

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    data class LyricsResult(
        val plainLyrics: String?,
        val syncedLyrics: String?,
        val isInstrumental: Boolean = false,
    )

    suspend fun getLyrics(
        title: String,
        artist: String,
        durationSeconds: Int = -1,
        album: String? = null,
    ): LyricsResult? = withContext(Dispatchers.IO) {
        val cleanedTitle = cleanTitle(title)
        val cleanedArtist = cleanArtist(artist)

        val directResult = queryDirect(cleanedTitle, cleanedArtist, album, durationSeconds)
        if (directResult != null) return@withContext directResult

        querySearch(cleanedTitle, cleanedArtist, durationSeconds)
    }

    private fun queryDirect(
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Int,
    ): LyricsResult? {
        val builder = "$API_BASE/get"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)

        if (!album.isNullOrBlank()) {
            builder.addQueryParameter("album_name", album)
        }
        if (durationSeconds > 0) {
            builder.addQueryParameter("duration", durationSeconds.toString())
        }

        val request = Request.Builder()
            .url(builder.build())
            .get()
            .header("User-Agent", USER_AGENT)
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string()?.takeIf { it.isNotBlank() } ?: return@use null
                val json = JSONObject(body)
                parseTrackJson(json)
            }
        }.getOrNull()
    }

    private fun querySearch(
        title: String,
        artist: String,
        durationSeconds: Int,
    ): LyricsResult? {
        val url = "$API_BASE/search"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", USER_AGENT)
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string()?.takeIf { it.isNotBlank() } ?: return@use null
                val array = JSONArray(body)
                if (array.length() == 0) return@use null

                var bestMatch: JSONObject? = null
                var minDiff = Int.MAX_VALUE

                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val dur = item.optInt("duration", -1)
                    if (durationSeconds <= 0 || dur <= 0) {
                        bestMatch = item
                        break
                    }
                    val diff = abs(dur - durationSeconds)
                    if (diff < minDiff && diff <= 15) {
                        minDiff = diff
                        bestMatch = item
                    }
                }

                bestMatch?.let { parseTrackJson(it) }
            }
        }.getOrNull()
    }

    private fun parseTrackJson(json: JSONObject): LyricsResult? {
        val synced = json.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
        val plain = json.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
        val instrumental = json.optBoolean("instrumental", false)

        if (synced == null && plain == null && !instrumental) return null
        return LyricsResult(
            plainLyrics = plain,
            syncedLyrics = synced,
            isInstrumental = instrumental,
        )
    }

    private fun cleanTitle(title: String): String =
        title.replace(Regex("""\s*[\[(]\s*(official|video|audio|lyrics|lyric|remastered|remix|hd|hq)\b.*?[\])]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*-\s*(official|video|audio|lyrics).*$""", RegexOption.IGNORE_CASE), "")
            .trim()

    private fun cleanArtist(artist: String): String =
        artist.split(',', '&').firstOrNull()?.trim() ?: artist.trim()
}
