package com.music.spotui.data.preferences

import android.content.Context

private const val PREF_STREAM_CACHE = "StreamUrlCache"
private const val SUFFIX_URL = "_url"
private const val SUFFIX_SOURCE = "_src"
private const val SUFFIX_QUALITY = "_q"
private const val SUFFIX_TIER = "_tier"
private const val SUFFIX_EXPIRES = "_exp"

/**
 * Returns a cached (url, source, quality) triple if the entry exists, has not
 * expired, and matches [expectedTier] (if specified). Expiry is checked against
 * the wallclock time recorded when the stream was resolved.
 */
fun getCachedStream(
    context: Context,
    query: String,
    expectedTier: String? = null,
): Triple<String, String, String>? {
    val prefs = context.getSharedPreferences(PREF_STREAM_CACHE, Context.MODE_PRIVATE)
    val url = prefs.getString(query + SUFFIX_URL, null) ?: return null
    val cachedTier = prefs.getString(query + SUFFIX_TIER, null)
    if (expectedTier != null && cachedTier != null && cachedTier != expectedTier) {
        return null
    }
    val expiresAt = prefs.getLong(query + SUFFIX_EXPIRES, 0L)
    // Treat as expired 60s early to avoid edge-of-expiry failures.
    if (System.currentTimeMillis() >= expiresAt - 60_000L) {
        // Lazy cleanup — the entry is stale.
        prefs.edit()
            .remove(query + SUFFIX_URL)
            .remove(query + SUFFIX_SOURCE)
            .remove(query + SUFFIX_QUALITY)
            .remove(query + SUFFIX_TIER)
            .remove(query + SUFFIX_EXPIRES)
            .apply()
        return null
    }
    val source = prefs.getString(query + SUFFIX_SOURCE, "YouTube") ?: "YouTube"
    val quality = prefs.getString(query + SUFFIX_QUALITY, "") ?: ""
    return Triple(url, source, quality)
}

/**
 * Persist a resolved stream URL so the next play of the same query can skip the
 * full resolution pipeline. [expiresInSeconds] is the value from the YouTube
 * player response (typically ~21600 = 6h).
 */
fun setCachedStream(
    context: Context,
    query: String,
    url: String,
    source: String,
    quality: String,
    expiresInSeconds: Int,
    qualityTier: String = "",
) {
    if (url.isBlank() || expiresInSeconds <= 0) return
    val expiresAt = System.currentTimeMillis() + expiresInSeconds * 1000L
    context.getSharedPreferences(PREF_STREAM_CACHE, Context.MODE_PRIVATE)
        .edit()
        .putString(query + SUFFIX_URL, url)
        .putString(query + SUFFIX_SOURCE, source)
        .putString(query + SUFFIX_QUALITY, quality)
        .putString(query + SUFFIX_TIER, qualityTier)
        .putLong(query + SUFFIX_EXPIRES, expiresAt)
        .apply()
}

/** Remove a cached stream entry (used when the user invalidates / refreshes). */
fun clearCachedStream(context: Context, query: String) {
    context.getSharedPreferences(PREF_STREAM_CACHE, Context.MODE_PRIVATE)
        .edit()
        .remove(query + SUFFIX_URL)
        .remove(query + SUFFIX_SOURCE)
        .remove(query + SUFFIX_QUALITY)
        .remove(query + SUFFIX_TIER)
        .remove(query + SUFFIX_EXPIRES)
        .apply()
}

/** Clear all cached streams completely. */
fun clearAllCachedStreams(context: Context) {
    context.getSharedPreferences(PREF_STREAM_CACHE, Context.MODE_PRIVATE)
        .edit()
        .clear()
        .apply()
}

