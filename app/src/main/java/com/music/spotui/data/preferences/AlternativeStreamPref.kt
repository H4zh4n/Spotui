package com.music.spotui.data.preferences

import android.content.Context
import android.net.Uri
import com.music.spotui.data.entity.SongsModel
import org.json.JSONObject

data class AlternativeStream(
    val type: String,
    val value: String,
    val label: String = "",
) {
    val isYouTube: Boolean get() = type == TYPE_YOUTUBE
    val isLocal: Boolean get() = type == TYPE_LOCAL

    companion object {
        const val TYPE_YOUTUBE = "youtube"
        const val TYPE_LOCAL = "local"
    }
}

private const val PREF = "AlternativeStreams"

fun alternativeStreamKey(song: SongsModel): String =
    song.spotifyTrackId.takeIf { it.isNotBlank() }?.let { "spotify:$it" } ?: "song:${song.id}"

fun alternativeStreamKeyForSpotifyId(spotifyTrackId: String): String =
    "spotify:$spotifyTrackId"

private fun AlternativeStream.toJson(): String = JSONObject().apply {
    put("type", type)
    put("value", value)
    put("label", label)
}.toString()

private fun parseAlternativeStream(raw: String): AlternativeStream? = runCatching {
    val obj = JSONObject(raw)
    AlternativeStream(
        type = obj.optString("type"),
        value = obj.optString("value"),
        label = obj.optString("label"),
    ).takeIf { it.value.isNotBlank() && (it.isYouTube || it.isLocal) }
}.getOrNull()

fun getAlternativeStream(context: Context, key: String): AlternativeStream? =
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .getString(key, null)
        ?.let(::parseAlternativeStream)

fun setYouTubeAlternativeStream(context: Context, key: String, videoId: String) {
    val stream = AlternativeStream(
        type = AlternativeStream.TYPE_YOUTUBE,
        value = videoId,
        label = "YouTube video",
    )
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .edit()
        .putString(key, stream.toJson())
        .apply()
}

fun setLocalAlternativeStream(context: Context, key: String, uri: Uri, label: String = "") {
    val stream = AlternativeStream(
        type = AlternativeStream.TYPE_LOCAL,
        value = uri.toString(),
        label = label.ifBlank { uri.lastPathSegment.orEmpty() },
    )
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .edit()
        .putString(key, stream.toJson())
        .apply()
}

fun clearAlternativeStream(context: Context, key: String) {
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .edit()
        .remove(key)
        .apply()
}

fun clearAllAlternativeStreams(context: Context) {
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .edit()
        .clear()
        .apply()
}
