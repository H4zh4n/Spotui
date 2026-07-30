package com.music.spotui.providers

import com.music.spotui.data.preferences.AudioProviderOrderItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

object ProviderMatchSearch {
    data class Query(
        val title: String,
        val artist: String,
        val album: String?,
        val isrc: String?,
        val durationMs: Long?,
    )

    suspend fun searchAll(
        query: Query,
        providers: List<AudioProviderOrderItem> = listOf(
            AudioProviderOrderItem.QOBUZ,
            AudioProviderOrderItem.TIDAL,
            AudioProviderOrderItem.DEEZER,
            AudioProviderOrderItem.SOUNDCLOUD,
        ),
        perProviderLimit: Int = 4,
    ): List<ProviderMatchCandidate> = withContext(Dispatchers.IO) {
        coroutineScope {
            providers.map { provider ->
                async { searchProvider(query, provider, perProviderLimit) }
            }.awaitAll().flatten()
        }
    }

    suspend fun searchProvider(
        query: Query,
        provider: AudioProviderOrderItem,
        limit: Int = 4,
    ): List<ProviderMatchCandidate> = withContext(Dispatchers.IO) {
        val providerQuery = QobuzAudioProvider.Query(
            mediaId = "",
            title = query.title,
            artists = listOfNotNull(query.artist.takeIf { it.isNotBlank() }),
            album = query.album,
            isrc = query.isrc,
            durationMs = query.durationMs,
        )

        runCatching {
            when (provider) {
                AudioProviderOrderItem.QOBUZ -> {
                    QobuzAudioProvider.searchCandidates(providerQuery, limit).map {
                        ProviderMatchCandidate(
                            provider = provider,
                            providerTrackId = it.trackId,
                            title = it.title,
                            artist = it.artist,
                            album = it.album,
                            durationMs = it.durationMs,
                        )
                    }
                }
                AudioProviderOrderItem.TIDAL -> {
                    val tidalQuery = TidalAudioProvider.Query(
                        mediaId = "",
                        title = query.title,
                        artists = listOfNotNull(query.artist.takeIf { it.isNotBlank() }),
                        album = query.album,
                        isrc = query.isrc,
                        durationMs = query.durationMs,
                    )
                    TidalAudioProvider.searchCandidates(tidalQuery, limit).map {
                        ProviderMatchCandidate(
                            provider = provider,
                            providerTrackId = it.trackId,
                            title = it.title,
                            artist = it.artist,
                            album = it.album,
                            durationMs = it.durationMs,
                        )
                    }
                }
                AudioProviderOrderItem.DEEZER -> {
                    val deezerQuery = DeezerAudioProvider.Query(
                        mediaId = "",
                        title = query.title,
                        artists = listOfNotNull(query.artist.takeIf { it.isNotBlank() }),
                        album = query.album,
                        isrc = query.isrc,
                        durationMs = query.durationMs,
                    )
                    DeezerAudioProvider.searchCandidates(deezerQuery, limit).map {
                        ProviderMatchCandidate(
                            provider = provider,
                            providerTrackId = it.trackId,
                            title = it.title,
                            artist = it.artist,
                            album = it.album,
                            durationMs = it.durationMs,
                        )
                    }
                }
                AudioProviderOrderItem.SOUNDCLOUD -> {
                    val scQuery = SoundCloudAudioProvider.Query(
                        mediaId = "",
                        title = query.title,
                        artists = listOfNotNull(query.artist.takeIf { it.isNotBlank() }),
                        album = query.album,
                        isrc = query.isrc,
                        durationMs = query.durationMs,
                    )
                    SoundCloudAudioProvider.searchCandidates(scQuery, limit).map {
                        ProviderMatchCandidate(
                            provider = provider,
                            providerTrackId = it.trackId,
                            title = it.title,
                            artist = it.artist,
                            album = it.album,
                            durationMs = it.durationMs,
                        )
                    }
                }
                else -> emptyList()
            }
        }.getOrDefault(emptyList())
    }
}
