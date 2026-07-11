package com.music.spotui.data.preferences

import android.content.Context

private const val PREF_RESOLVED_VIDEOS = "ResolvedVideosCache"

fun getCachedVideoIds(context: Context, cacheKey: String): List<String>? {
    val raw = context.getSharedPreferences(PREF_RESOLVED_VIDEOS, Context.MODE_PRIVATE)
        .getString(cacheKey, null) ?: return null
    return raw.split(",").filter { it.isNotBlank() }
}

fun setCachedVideoIds(context: Context, cacheKey: String, videoIds: List<String>) {
    if (videoIds.isEmpty()) return
    val raw = videoIds.joinToString(",")
    context.getSharedPreferences(PREF_RESOLVED_VIDEOS, Context.MODE_PRIVATE)
        .edit()
        .putString(cacheKey, raw)
        .apply()
}

fun clearCachedVideoId(context: Context, songQuery: String) {
    val prefs = context.getSharedPreferences(PREF_RESOLVED_VIDEOS, Context.MODE_PRIVATE)
    // Clear both song and video search cache keys for this query
    prefs.edit()
        .remove("$songQuery|FILTER_SONG")
        .remove("$songQuery|FILTER_VIDEO")
        .apply()
}
