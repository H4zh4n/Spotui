package com.music.spotui.data.preferences

import android.content.Context

fun getCachedArtistImage(context: Context, artistId: String): String? {
    if (artistId.isBlank()) return null
    return context.getSharedPreferences("ArtistImageCache", Context.MODE_PRIVATE)
        .getString(artistId, null)
}

fun cacheArtistImage(context: Context, artistId: String, imageUrl: String) {
    if (artistId.isBlank() || imageUrl.isBlank()) return
    context.getSharedPreferences("ArtistImageCache", Context.MODE_PRIVATE)
        .edit().putString(artistId, imageUrl).apply()
}
