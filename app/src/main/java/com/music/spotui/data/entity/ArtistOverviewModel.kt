package com.music.spotui.data.entity

/**
 * Rich artist page data (Spotify-style): header art, monthly listeners, bio,
 * popular tracks with play counts, discography and related artists. Backed by
 * Spotify's queryArtistOverview GQL endpoint.
 */
data class ArtistOverviewModel(
    val id: String = "",
    val name: String = "",
    val verified: Boolean = false,
    val monthlyListeners: Long? = null,
    val biography: String? = null,
    val headerImage: String = "",
    val avatarImage: String = "",
    val topTracks: List<ArtistTrackUi> = emptyList(),
    val popularReleases: List<AlbumsModel> = emptyList(),
    val appearsOn: List<AlbumsModel> = emptyList(),
    val relatedArtists: List<ArtistsModel> = emptyList(),
    val featuringPlaylist: LibraryEntry? = null,
)

data class ArtistTrackUi(
    val song: SongsModel,
    val playcount: Long? = null,
)
