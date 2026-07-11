package com.music.spotui.data.update

import android.content.Context
import android.util.Log
import com.music.spotui.BuildConfig
import com.music.spotui.data.preferences.getUpdateRepoUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val PREFS = "update_prefs"
    private const val KEY_SKIP = "skip_fingerprint"

    data class UpdateInfo(
        val version: String,
        val downloadUrl: String,
        val fingerprint: String,
        val releaseBody: String,
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun check(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val info = runCatching { fetchLatestRelease(context) }
            .onFailure { Log.d(TAG, "update check failed: ${it.message}") }
            .getOrNull() ?: return@withContext null
        if (!isNewer(info.version, BuildConfig.VERSION_NAME)) return@withContext null
        val skipped = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SKIP, null)
        if (skipped == info.fingerprint) return@withContext null
        info
    }

    fun skipRelease(context: Context, info: UpdateInfo) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SKIP, info.fingerprint).apply()
    }

    private fun fetchLatestRelease(context: Context): UpdateInfo? {
        val repoUrl = getUpdateRepoUrl(context).trimEnd('/')
        val repoPath = repoUrl.removePrefix("https://github.com/").removePrefix("http://github.com/")
        val apiLatest = "https://api.github.com/repos/$repoPath/releases/latest"
        val releasesPage = "$repoUrl/releases/latest"

        val request = Request.Builder()
            .url(apiLatest)
            .header("Accept", "application/vnd.github+json")
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return null
        val body = response.body?.string() ?: return null
        val json = JSONObject(body)
        val tag = json.optString("tag_name").trim()
        if (tag.isBlank()) return null
        val version = extractVersion(tag)
            ?: return null
        val releaseBody = json.optString("body", "")
        val htmlUrl = json.optString("html_url", releasesPage)
            .ifBlank { releasesPage }
        val assets = json.optJSONArray("assets")
        val apkUrl = (0 until (assets?.length() ?: 0))
            .asSequence()
            .mapNotNull { assets?.optJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk") }
            ?.optString("browser_download_url")
        return UpdateInfo(
            version = version,
            downloadUrl = apkUrl?.ifBlank { null } ?: htmlUrl,
            fingerprint = "${json.optLong("id")}:${json.optString("updated_at")}:$version",
            releaseBody = releaseBody,
        )
    }

    private fun extractVersion(text: String): String? =
        Regex("""\d+(?:\.\d+)+""").find(text)?.value

    private fun isNewer(remote: String, installed: String): Boolean {
        val r = remote.split('.').map { it.toIntOrNull() ?: 0 }
        val i = installed.split('-', '+').first().split('.').map { it.toIntOrNull() ?: 0 }
        for (n in 0 until maxOf(r.size, i.size)) {
            val a = r.getOrElse(n) { 0 }
            val b = i.getOrElse(n) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
