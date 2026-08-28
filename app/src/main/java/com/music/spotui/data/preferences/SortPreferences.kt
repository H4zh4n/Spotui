package com.music.spotui.data.preferences

import android.content.Context

private const val PREF_SORT = "app_sort_preferences"

// ── Album Sort ──
enum class AlbumSortOption(val label: String) {
    DEFAULT("Track order"),
    TITLE("Title"),
    ARTIST("Artist"),
    DURATION("Duration");

    fun getDescriptiveLabel(isDescending: Boolean): String = when (this) {
        DEFAULT -> if (isDescending) "Track order (reverse)" else "Track order (default)"
        TITLE -> if (isDescending) "Title (Z to A)" else "Title (A to Z)"
        ARTIST -> if (isDescending) "Artist (Z to A)" else "Artist (A to Z)"
        DURATION -> if (isDescending) "Duration (longest first)" else "Duration (shortest first)"
    }
}

fun getAlbumSortOption(context: Context, albumId: String): AlbumSortOption {
    val prefs = context.getSharedPreferences(PREF_SORT, Context.MODE_PRIVATE)
    val saved = prefs.getString("album_sort_$albumId", AlbumSortOption.DEFAULT.name)
    return runCatching { AlbumSortOption.valueOf(saved!!) }.getOrDefault(AlbumSortOption.DEFAULT)
}

fun isAlbumSortDescending(context: Context, albumId: String): Boolean {
    return context.getSharedPreferences(PREF_SORT, Context.MODE_PRIVATE)
        .getBoolean("album_desc_$albumId", false)
}

fun setAlbumSortOption(context: Context, albumId: String, option: AlbumSortOption, desc: Boolean) {
    context.getSharedPreferences(PREF_SORT, Context.MODE_PRIVATE).edit()
        .putString("album_sort_$albumId", option.name)
        .putBoolean("album_desc_$albumId", desc)
        .apply()
}

// ── Show / Podcast Sort ──
enum class ShowSortOption(val label: String) {
    DATE("Date published"),
    TITLE("Title"),
    DURATION("Duration");

    fun getDescriptiveLabel(isDescending: Boolean): String = when (this) {
        DATE -> if (isDescending) "Date (newest to oldest)" else "Date (oldest to newest)"
        TITLE -> if (isDescending) "Title (Z to A)" else "Title (A to Z)"
        DURATION -> if (isDescending) "Duration (longest first)" else "Duration (shortest first)"
    }
}

fun getShowSortOption(context: Context, showId: String): ShowSortOption {
    val prefs = context.getSharedPreferences(PREF_SORT, Context.MODE_PRIVATE)
    val saved = prefs.getString("show_sort_$showId", ShowSortOption.DATE.name)
    return runCatching { ShowSortOption.valueOf(saved!!) }.getOrDefault(ShowSortOption.DATE)
}

fun isShowSortDescending(context: Context, showId: String): Boolean {
    return context.getSharedPreferences(PREF_SORT, Context.MODE_PRIVATE)
        .getBoolean("show_desc_$showId", true)
}

fun setShowSortOption(context: Context, showId: String, option: ShowSortOption, desc: Boolean) {
    context.getSharedPreferences(PREF_SORT, Context.MODE_PRIVATE).edit()
        .putString("show_sort_$showId", option.name)
        .putBoolean("show_desc_$showId", desc)
        .apply()
}

// ── Liked Songs Sort ──
enum class LikedSongsSortOption(val label: String) {
    DATE("Date added"),
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album");

    fun getDescriptiveLabel(isDescending: Boolean): String = when (this) {
        DATE -> if (isDescending) "Date added (newest to oldest)" else "Date added (oldest to newest)"
        TITLE -> if (isDescending) "Title (Z to A)" else "Title (A to Z)"
        ARTIST -> if (isDescending) "Artist (Z to A)" else "Artist (A to Z)"
        ALBUM -> if (isDescending) "Album (Z to A)" else "Album (A to Z)"
    }
}

fun getLikedSongsSortOption(context: Context): LikedSongsSortOption {
    val prefs = context.getSharedPreferences(PREF_SORT, Context.MODE_PRIVATE)
    val saved = prefs.getString("liked_songs_sort", LikedSongsSortOption.DATE.name)
    return runCatching { LikedSongsSortOption.valueOf(saved!!) }.getOrDefault(LikedSongsSortOption.DATE)
}

fun isLikedSongsSortDescending(context: Context): Boolean {
    return context.getSharedPreferences(PREF_SORT, Context.MODE_PRIVATE)
        .getBoolean("liked_songs_desc", true)
}

fun setLikedSongsSortOption(context: Context, option: LikedSongsSortOption, desc: Boolean) {
    context.getSharedPreferences(PREF_SORT, Context.MODE_PRIVATE).edit()
        .putString("liked_songs_sort", option.name)
        .putBoolean("liked_songs_desc", desc)
        .apply()
}

// ── History Sort ──
enum class HistorySortOption(val label: String) {
    DATE("Recently played"),
    TITLE("Title"),
    ARTIST("Artist");

    fun getDescriptiveLabel(isDescending: Boolean): String = when (this) {
        DATE -> if (isDescending) "Recently played (newest to oldest)" else "Recently played (oldest to newest)"
        TITLE -> if (isDescending) "Title (Z to A)" else "Title (A to Z)"
        ARTIST -> if (isDescending) "Artist (Z to A)" else "Artist (A to Z)"
    }
}

fun getHistorySortOption(context: Context): HistorySortOption {
    val prefs = context.getSharedPreferences(PREF_SORT, Context.MODE_PRIVATE)
    val saved = prefs.getString("history_sort", HistorySortOption.DATE.name)
    return runCatching { HistorySortOption.valueOf(saved!!) }.getOrDefault(HistorySortOption.DATE)
}

fun isHistorySortDescending(context: Context): Boolean {
    return context.getSharedPreferences(PREF_SORT, Context.MODE_PRIVATE)
        .getBoolean("history_desc", true)
}

fun setHistorySortOption(context: Context, option: HistorySortOption, desc: Boolean) {
    context.getSharedPreferences(PREF_SORT, Context.MODE_PRIVATE).edit()
        .putString("history_sort", option.name)
        .putBoolean("history_desc", desc)
        .apply()
}

// ── Library Sort ──
enum class LibrarySortOption(val label: String) {
    RECENTS("Recently added"),
    TITLE("Title"),
    CREATOR("Artist / Creator");

    fun getDescriptiveLabel(isDescending: Boolean): String = when (this) {
        RECENTS -> if (isDescending) "Recently added (newest first)" else "Recently added (oldest first)"
        TITLE -> if (isDescending) "Title (Z to A)" else "Title (A to Z)"
        CREATOR -> if (isDescending) "Artist / Creator (Z to A)" else "Artist / Creator (A to Z)"
    }
}

fun getLibrarySortOption(context: Context): LibrarySortOption {
    val prefs = context.getSharedPreferences(PREF_SORT, Context.MODE_PRIVATE)
    val saved = prefs.getString("library_sort", LibrarySortOption.RECENTS.name)
    return runCatching { LibrarySortOption.valueOf(saved!!) }.getOrDefault(LibrarySortOption.RECENTS)
}

fun isLibrarySortDescending(context: Context): Boolean {
    return context.getSharedPreferences(PREF_SORT, Context.MODE_PRIVATE)
        .getBoolean("library_desc", true)
}

fun setLibrarySortOption(context: Context, option: LibrarySortOption, desc: Boolean) {
    context.getSharedPreferences(PREF_SORT, Context.MODE_PRIVATE).edit()
        .putString("library_sort", option.name)
        .putBoolean("library_desc", desc)
        .apply()
}

// ── Playlist Sort ──
private const val PREF_PLAYLIST_SORTS = "PlaylistSorts"

enum class PlaylistSortOption(val label: String) {
    DATE("Date added"),
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album");

    fun getDescriptiveLabel(isDescending: Boolean): String = when (this) {
        DATE -> if (isDescending) "Date added (newest to oldest)" else "Date added (oldest to newest)"
        TITLE -> if (isDescending) "Title (Z to A)" else "Title (A to Z)"
        ARTIST -> if (isDescending) "Artist (Z to A)" else "Artist (A to Z)"
        ALBUM -> if (isDescending) "Album (Z to A)" else "Album (A to Z)"
    }
}

fun getPlaylistSortOption(context: Context, playlistId: String): PlaylistSortOption {
    val prefs = context.getSharedPreferences(PREF_PLAYLIST_SORTS, Context.MODE_PRIVATE)
    val saved = prefs.getString("sort_option_$playlistId", PlaylistSortOption.DATE.name)
    return runCatching { PlaylistSortOption.valueOf(saved!!) }.getOrDefault(PlaylistSortOption.DATE)
}

fun isPlaylistSortDescending(context: Context, playlistId: String): Boolean {
    val prefs = context.getSharedPreferences(PREF_PLAYLIST_SORTS, Context.MODE_PRIVATE)
    if (!prefs.contains("sort_descending_$playlistId")) {
        val opt = getPlaylistSortOption(context, playlistId)
        return opt == PlaylistSortOption.DATE
    }
    return prefs.getBoolean("sort_descending_$playlistId", true)
}

fun setPlaylistSort(context: Context, playlistId: String, option: PlaylistSortOption, descending: Boolean) {
    context.getSharedPreferences(PREF_PLAYLIST_SORTS, Context.MODE_PRIVATE).edit()
        .putString("sort_option_$playlistId", option.name)
        .putBoolean("sort_descending_$playlistId", descending)
        .apply()
}

