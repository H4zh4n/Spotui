package com.music.spotui.data.entity

/**
 * A single row in "Your Library": either a saved album or a saved/followed
 * playlist. [spotifyId] is the Spotify string id used to open the detail screen
 * (playlistRoute for playlists, name-based albumRoute for albums).
 */
data class LibraryEntry(
    val spotifyId: String,
    val name: String,
    val subtitle: String,
    val coverUri: String,
    val isPlaylist: Boolean,
    val artists: String = "",
    val isLocal: Boolean = false,
)
