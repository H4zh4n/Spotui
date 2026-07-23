package com.music.spotui.util

import org.junit.Assert.*
import org.junit.Test

class SpotifyDeepLinkTest {

    @Test
    fun testParseTrackWebUrl() {
        val link = SpotifyDeepLink.parseUrlString("https://open.spotify.com/track/6rqhFgToBYV0Wzjp2ySBxV?si=abc123xyz")
        assertTrue(link is SpotifyDeepLink.Track)
        assertEquals("6rqhFgToBYV0Wzjp2ySBxV", (link as SpotifyDeepLink.Track).id)
    }

    @Test
    fun testParseTrackWwwUrl() {
        val link = SpotifyDeepLink.parseUrlString("https://www.spotify.com/track/6rqhFgToBYV0Wzjp2ySBxV")
        assertTrue(link is SpotifyDeepLink.Track)
        assertEquals("6rqhFgToBYV0Wzjp2ySBxV", (link as SpotifyDeepLink.Track).id)
    }

    @Test
    fun testParseTrackSpotifyComUrl() {
        val link = SpotifyDeepLink.parseUrlString("https://spotify.com/track/6rqhFgToBYV0Wzjp2ySBxV")
        assertTrue(link is SpotifyDeepLink.Track)
        assertEquals("6rqhFgToBYV0Wzjp2ySBxV", (link as SpotifyDeepLink.Track).id)
    }

    @Test
    fun testParseTrackCustomUri() {
        val link = SpotifyDeepLink.parseUrlString("spotify:track:6rqhFgToBYV0Wzjp2ySBxV?si=test")
        assertTrue(link is SpotifyDeepLink.Track)
        assertEquals("6rqhFgToBYV0Wzjp2ySBxV", (link as SpotifyDeepLink.Track).id)
    }

    @Test
    fun testParsePlaylistWebUrl() {
        val link = SpotifyDeepLink.parseUrlString("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M")
        assertTrue(link is SpotifyDeepLink.Playlist)
        assertEquals("37i9dQZF1DXcBWIGoYBM5M", (link as SpotifyDeepLink.Playlist).id)
    }

    @Test
    fun testParseUserPlaylistWebUrl() {
        val link = SpotifyDeepLink.parseUrlString("https://open.spotify.com/user/spotify/playlist/37i9dQZF1DXcBWIGoYBM5M")
        assertTrue(link is SpotifyDeepLink.Playlist)
        assertEquals("37i9dQZF1DXcBWIGoYBM5M", (link as SpotifyDeepLink.Playlist).id)
    }

    @Test
    fun testParseUserPlaylistCustomUri() {
        val link = SpotifyDeepLink.parseUrlString("spotify:user:spotify:playlist:37i9dQZF1DXcBWIGoYBM5M")
        assertTrue(link is SpotifyDeepLink.Playlist)
        assertEquals("37i9dQZF1DXcBWIGoYBM5M", (link as SpotifyDeepLink.Playlist).id)
    }

    @Test
    fun testParseAlbumWebUrl() {
        val link = SpotifyDeepLink.parseUrlString("https://open.spotify.com/album/4aawyAB9vmqN3uQ7FjRGTy")
        assertTrue(link is SpotifyDeepLink.Album)
        assertEquals("4aawyAB9vmqN3uQ7FjRGTy", (link as SpotifyDeepLink.Album).id)
    }

    @Test
    fun testParseLocalizedAlbumWebUrl() {
        val link = SpotifyDeepLink.parseUrlString("https://open.spotify.com/intl-es/album/4aawyAB9vmqN3uQ7FjRGTy?si=123")
        assertTrue(link is SpotifyDeepLink.Album)
        assertEquals("4aawyAB9vmqN3uQ7FjRGTy", (link as SpotifyDeepLink.Album).id)
    }

    @Test
    fun testParseArtistWebUrl() {
        val link = SpotifyDeepLink.parseUrlString("https://open.spotify.com/artist/06HL4z0CvFAxyW27GXpf02")
        assertTrue(link is SpotifyDeepLink.Artist)
        assertEquals("06HL4z0CvFAxyW27GXpf02", (link as SpotifyDeepLink.Artist).id)
    }

    @Test
    fun testParseShowWebUrl() {
        val link = SpotifyDeepLink.parseUrlString("https://open.spotify.com/show/5CfCWKIveZ2AcgB1pMCe3B")
        assertTrue(link is SpotifyDeepLink.Show)
        assertEquals("5CfCWKIveZ2AcgB1pMCe3B", (link as SpotifyDeepLink.Show).id)
    }

    @Test
    fun testParseEpisodeWebUrl() {
        val link = SpotifyDeepLink.parseUrlString("https://open.spotify.com/episode/7macc2E7mfdW2vY2wGv29Z")
        assertTrue(link is SpotifyDeepLink.Episode)
        assertEquals("7macc2E7mfdW2vY2wGv29Z", (link as SpotifyDeepLink.Episode).id)
    }

    @Test
    fun testParseShortLink() {
        val link = SpotifyDeepLink.parseUrlString("https://spotify.link/AbCdEfGh")
        // Short links need redirect resolution, so raw string returns null until resolved or if segments < 2
        assertNull(link)
    }

    @Test
    fun testParseInvalidUrl() {
        assertNull(SpotifyDeepLink.parseUrlString("https://google.com/search"))
        assertNull(SpotifyDeepLink.parseUrlString(""))
        assertNull(SpotifyDeepLink.parseUrlString(null))
    }
}
