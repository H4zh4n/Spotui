package com.music.spotui.data.entity

data class SongsModel(
    val id : Int,
    val title : String,
    val album : String,
    val singer : String,
    val coverUri : String,
    val url : String,
    // Real Spotify track id (e.g. "3n3Ppam7vgaVa1iaRUc9Lp"), kept so playback can
    // seed Spotify's recommendations endpoint for autoplay radio. Empty when unknown.
    val spotifyTrackId : String = "",
    // Whether the Spotify track is the explicit version, so the YouTube fallback
    // can pick the matching (explicit vs clean) edit.
    val explicit : Boolean = false,
    // Track length in ms (from Spotify). Used to disambiguate the YouTube match:
    // a same-title-different-artist song almost always has a different duration.
    val durationMs : Int = 0,
    // Comma-separated artist IDs matching the order of names in [singer].
    // Populated from Spotify's API so tapping a specific artist in the player
    // navigates to the correct artist page.
    val artistIds : String = ""
){
    constructor() : this(-1 ,"" ,"" ,"" ,"" ,"", "")
}
