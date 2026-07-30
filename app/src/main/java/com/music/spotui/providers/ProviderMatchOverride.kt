package com.music.spotui.providers

import com.music.spotui.data.preferences.AudioProviderOrderItem
import org.json.JSONObject

data class ProviderMatchOverride(
    val provider: AudioProviderOrderItem,
    val providerTrackId: String,
    val label: String,
) {
    fun providerMediaId(): String =
        when (provider) {
            AudioProviderOrderItem.AMAZON -> "amazon:track:$providerTrackId"
            AudioProviderOrderItem.SOUNDCLOUD -> providerTrackId
            AudioProviderOrderItem.TIDAL -> "tidal:track:$providerTrackId"
            AudioProviderOrderItem.DEEZER -> "deezer:track:$providerTrackId"
            AudioProviderOrderItem.QOBUZ -> "qobuz:track:$providerTrackId"
            AudioProviderOrderItem.YOUTUBE_MUSIC -> providerTrackId
            AudioProviderOrderItem.SPOTIFLAC -> providerTrackId
        }
}

data class ProviderMatchCandidate(
    val provider: AudioProviderOrderItem,
    val providerTrackId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long?,
    val source: String = provider.displayName,
) {
    val label: String
        get() = listOf(title, artist.takeIf { it.isNotBlank() })
            .filterNotNull()
            .joinToString(" - ")
}

object ProviderMatchOverrides {
    fun decode(value: String?): MutableMap<String, ProviderMatchOverride> {
        if (value.isNullOrBlank()) return mutableMapOf()
        return runCatching {
            val root = JSONObject(value)
            mutableMapOf<String, ProviderMatchOverride>().apply {
                root.keys().forEach { mediaId ->
                    val obj = root.optJSONObject(mediaId) ?: return@forEach
                    val providerName = obj.optString("provider").takeIf { it.isNotBlank() } ?: return@forEach
                    val provider = AudioProviderOrderItem.values().firstOrNull { it.name == providerName || it.id == providerName } ?: return@forEach
                    val providerTrackId = obj.optString("trackId").takeIf { it.isNotBlank() } ?: return@forEach
                    put(
                        mediaId,
                        ProviderMatchOverride(
                            provider = provider,
                            providerTrackId = providerTrackId,
                            label = obj.optString("label").takeIf { it.isNotBlank() } ?: providerTrackId,
                        ),
                    )
                }
            }
        }.getOrDefault(mutableMapOf())
    }

    fun encode(overrides: Map<String, ProviderMatchOverride>): String {
        val root = JSONObject()
        overrides.forEach { (mediaId, override) ->
            root.put(
                mediaId,
                JSONObject()
                    .put("provider", override.provider.name)
                    .put("trackId", override.providerTrackId)
                    .put("label", override.label),
            )
        }
        return root.toString()
    }
}
