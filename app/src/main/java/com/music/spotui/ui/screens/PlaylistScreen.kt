package com.music.spotui.ui.screens

import android.content.Context
import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.music.spotui.R
import com.music.spotui.data.api.Response
import com.music.spotui.data.entity.AlbumsModel
import com.music.spotui.di.Palette
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.components.Loader
import com.music.spotui.ui.components.Snackbar
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.AppPalette
import com.music.spotui.ui.viewmodel.PlaylistViewModel
import com.music.spotui.ui.viewmodel.PlayerViewModel
import com.music.spotui.ui.components.SwipeToPlayNextWrapper

enum class PlaylistSortOption(val label: String) {
    DATE("Date added"),
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album")
}

fun PlaylistSortOption.getDescriptiveLabel(isDescending: Boolean): String {
    return when (this) {
        PlaylistSortOption.DATE -> if (isDescending) "Date added (newest to oldest)" else "Date added (oldest to newest)"
        PlaylistSortOption.TITLE -> if (isDescending) "Title (Z to A)" else "Title (A to Z)"
        PlaylistSortOption.ARTIST -> if (isDescending) "Artist (Z to A)" else "Artist (A to Z)"
        PlaylistSortOption.ALBUM -> if (isDescending) "Album (Z to A)" else "Album (A to Z)"
    }
}

private const val PREF_PLAYLIST_SORTS = "PlaylistSorts"

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

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistScreen(navController: NavController, playlistId: String, playlistName: String = "") {

    val playlistViewModel: PlaylistViewModel = hiltViewModel()
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val songsResp by playlistViewModel.songs.collectAsState()
    val playlistResp by playlistViewModel.playlist.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(playlistId) {
        playlistViewModel.loadPlaylist(playlistId)
    }

    val songs = (songsResp as? Response.Success)?.data.orEmpty()
    val playlist = (playlistResp as? Response.Success)?.data
        ?: AlbumsModel(
            id = playlistId.hashCode() and 0x7fffffff,
            artists = "",
            coverUri = songs.firstOrNull()?.coverUri ?: "",
            name = playlistName,
            time = "",
        )

    LaunchedEffect(songs, playlist, songsResp, playlistResp) {
        if (songsResp is Response.Success && playlistResp is Response.Success && songs.isNotEmpty() && playlist != null) {
            com.music.spotui.data.preferences.OfflineCollectionsPref.saveCollection(
                context = context,
                id = playlistId,
                name = playlist.name,
                coverUri = playlist.coverUri,
                artists = playlist.artists,
                isPlaylist = true,
                songs = songs
            )
        }
    }

    LaunchedEffect(songs) {
        if (songs.isNotEmpty()) {
            SongPlayer.prefetchList(songs.map { it.url }, context)
        }
    }
    
    var searchQuery by remember(playlistId) { mutableStateOf("") }
    var currentSort by remember(playlistId) { mutableStateOf(getPlaylistSortOption(context, playlistId)) }
    var isDescending by remember(playlistId) { mutableStateOf(isPlaylistSortDescending(context, playlistId)) }
    var showSortSheet by remember { mutableStateOf(false) }

    val filteredSongs = remember(songs, searchQuery, currentSort, isDescending) {
        val filtered = if (searchQuery.isBlank()) {
            songs
        } else {
            songs.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.singer.contains(searchQuery, ignoreCase = true)
            }
        }
        
        when (currentSort) {
            PlaylistSortOption.DATE -> if (isDescending) filtered.reversed() else filtered
            PlaylistSortOption.TITLE -> if (isDescending) filtered.sortedByDescending { it.title.lowercase() } else filtered.sortedBy { it.title.lowercase() }
            PlaylistSortOption.ARTIST -> if (isDescending) filtered.sortedByDescending { it.singer.lowercase() } else filtered.sortedBy { it.singer.lowercase() }
            PlaylistSortOption.ALBUM -> if (isDescending) filtered.sortedByDescending { it.album.lowercase() } else filtered.sortedBy { it.album.lowercase() }
        }
    }

    var menuSong by remember { mutableStateOf<com.music.spotui.data.entity.SongsModel?>(null) }
    menuSong?.let { sel ->
        com.music.spotui.ui.components.SongOptionsSheet(
            song = sel,
            navController = navController,
            context = context,
            onDismiss = { menuSong = null },
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(AppBackground.toArgb()))
    ) {
        if (songsResp is Response.Loading && playlistResp is Response.Loading) {
            Loader()
            return@Surface
        }

        var snackbarMessage by remember { mutableStateOf("") }
        var snackbarVisible by remember { mutableStateOf(false) }
        LaunchedEffect(snackbarVisible) {
            if (snackbarVisible) {
                kotlinx.coroutines.delay(1500)
                snackbarVisible = false
            }
        }

        var dominentColor by remember { mutableStateOf(Color(AppBackground.toArgb())) }
        Palette().extractSecondColorFromCoverUrl(context = context, playlist.coverUri) { color ->
            dominentColor = color
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    modifier = Modifier.padding(16.dp, 0.dp),
                    navigationIcon = {
                        Icon(
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { navController.navigateUp() },
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "",
                            tint = Color.White
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                    ),
                    title = { Text(text = "") }
                )
            }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(AppBackground.toArgb()))
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 440.dp)
                            .padding(bottom = 8.dp)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(dominentColor, Color(AppBackground.toArgb())),
                                    startY = -100f,
                                ),
                            ),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Spacer(modifier = Modifier.padding(25.dp))

                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            GlideImage(
                                modifier = Modifier.size(230.dp),
                                model = playlist.coverUri,
                                failure = placeholder(R.drawable.placeholder),
                                contentDescription = "",
                            )
                        }
                        Spacer(modifier = Modifier.padding(5.dp))
                        Text(
                            modifier = Modifier.padding(20.dp, 5.dp, 0.dp, 0.dp),
                            text = playlist.name.ifBlank { playlistName },
                            color = Color.White,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (playlist.time.isNotBlank()) {
                            Text(
                                modifier = Modifier.padding(20.dp, 4.dp, 20.dp, 0.dp),
                                text = playlist.time,
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (playlist.artists.isNotBlank()) {
                            Text(
                                modifier = Modifier.padding(20.dp, 4.dp, 0.dp, 0.dp),
                                text = "Playlist • ${playlist.artists}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .padding(20.dp, 0.dp)
                        ) {
                            var playlistDownloaded by remember(songs) {
                                mutableStateOf(songs.isNotEmpty() && SongPlayer.allDownloaded(songs, context))
                            }

                            if (snackbarVisible) {
                                Box(modifier = Modifier.weight(1f)) {
                                    Snackbar(showMessage = snackbarMessage)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.Start,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (songs.isNotEmpty()) {
                                        // Add all playlist tracks to the queue.
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_queue_add),
                                            tint = Color.White,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                ) {
                                                    playlistViewModel.addAllToQueue(filteredSongs)
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        "${filteredSongs.size} track(s) added to queue",
                                                        android.widget.Toast.LENGTH_SHORT,
                                                    ).show()
                                                },
                                            contentDescription = "Add to queue",
                                        )
                                        Spacer(modifier = Modifier.width(18.dp))
                                        Icon(
                                            imageVector = if (playlistDownloaded)
                                                Icons.Default.CheckCircle else ImageVector.vectorResource(R.drawable.ic_download),
                                            tint = if (playlistDownloaded) Color(AppPalette.toArgb()) else Color.White,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                ) {
                                                    if (!playlistDownloaded) {
                                                        SongPlayer.downloadAll(songs, context)
                                                        snackbarMessage = "Downloading ${songs.size} tracks…"
                                                        snackbarVisible = true
                                                    }
                                                },
                                            contentDescription = "Download playlist",
                                        )
                                        Spacer(modifier = Modifier.width(18.dp))
                                        // Shuffle-play: start the playlist in random order.
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_player_shuffle),
                                            tint = Color.White,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                ) {
                                                    playlistViewModel.startShuffled(songs)?.let { first ->
                                                        SongPlayer.playSong(first.url, context)
                                                        playlistViewModel.updateSongState(
                                                            first.coverUri,
                                                            first.title,
                                                            first.singer,
                                                            true,
                                                            first.id,
                                                            0,
                                                            playlist.name,
                                                        )
                                                    }
                                                },
                                            contentDescription = "Shuffle play",
                                        )
                                    }
                                }
                            }
                            // Always visible: pause when playing, resume when this
                            // list's track is paused, otherwise start from the top.
                            if (songs.isNotEmpty()) {
                                val playing = playlistViewModel.currentSongPlayingState.value
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(100.dp))
                                        .background(Color.White)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            when {
                                                playing -> playlistViewModel.setPlaying(false)
                                                songs.any { it.id == playlistViewModel.currentSongId.value } ->
                                                    playlistViewModel.setPlaying(true)
                                                else -> {
                                                    playlistViewModel.updateQueue(songs)
                                                    SongPlayer.playSong(songs[0].url, context)
                                                    playlistViewModel.updateSongState(
                                                        songs[0].coverUri,
                                                        songs[0].title,
                                                        songs[0].singer,
                                                        true,
                                                        songs[0].id,
                                                        0,
                                                        playlist.name
                                                    )
                                                }
                                            }
                                        }
                                ) {
                                    Icon(
                                        modifier = Modifier.size(25.dp),
                                        tint = Color.Black,
                                        painter = painterResource(
                                            id = if (playing) R.drawable.ic_playing else R.drawable.play_svgrepo_com,
                                        ),
                                        contentDescription = if (playing) "Pause" else "Play"
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Search bar for playlist ──
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp, 8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .height(55.dp)
                            .background(Color.White)
                            .padding(10.dp, 0.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_search_big),
                            tint = Color.Black,
                            contentDescription = "Search",
                            modifier = Modifier.size(24.dp)
                        )

                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            textStyle = TextStyle.Default.copy(
                                fontSize = 16.sp,
                                color = Color.Black,
                                fontWeight = FontWeight(500)
                            ),
                            colors = TextFieldDefaults.colors(
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = Color.Black
                            ),
                            singleLine = true,
                            placeholder = {
                                Text(
                                    text = "Search in playlist",
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        )

                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = Color.Black,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { searchQuery = "" }
                            )
                        }
                    }
                }
                
                // ── Sort action ──
                item {
                    if (songs.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp, 0.dp, 20.dp, 8.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFF2A2A30))
                                    .clickable { showSortSheet = true }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = currentSort.getDescriptiveLabel(isDescending),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Sort Options",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .padding(start = 4.dp)
                                )
                            }
                        }
                    }
                }

                itemsIndexed(filteredSongs, key = { _, song -> song.id }) { index, song ->
                    val currentColor = if (song.id == playlistViewModel.currentSongId.value)
                        Color(AppPalette.toArgb()) else Color.White

                    SwipeToPlayNextWrapper(
                        onPlayNext = {
                            playerViewModel.playNext(song)
                            android.widget.Toast.makeText(
                                context,
                                "${song.title} will play next",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AppBackground)
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onLongClick = { menuSong = song },
                                    onClick = {
                                        playlistViewModel.updateQueue(filteredSongs)
                                        SongPlayer.playSong(song.url, context)
                                        playlistViewModel.updateSongState(
                                            song.coverUri,
                                            song.title,
                                            song.singer,
                                            true,
                                            song.id,
                                            index,
                                            playlist.name
                                        )
                                    },
                                )
                                .padding(20.dp, 8.dp)
                        ) {
                            GlideImage(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                model = song.coverUri,
                                failure = placeholder(R.drawable.placeholder),
                                contentScale = ContentScale.Crop,
                                contentDescription = ""
                            )
                            Column(modifier = Modifier.padding(start = 12.dp).width(280.dp)) {
                                Text(
                                    text = song.title,
                                    color = currentColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                                Text(
                                    text = song.singer,
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.padding(80.dp)) }
            }
        }
        if (showSortSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSortSheet = false },
                containerColor = Color(0xFF1A1A1A)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Text(
                        text = "Sort by",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 12.dp)
                    )
                    HorizontalDivider(color = Color(0xFF2A2A2A))
                    Spacer(modifier = Modifier.height(4.dp))
                    PlaylistSortOption.entries.forEach { option ->
                        val isSelected = option == currentSort
                        val icon = when (option) {
                            PlaylistSortOption.DATE -> Icons.Default.DateRange
                            PlaylistSortOption.TITLE -> Icons.AutoMirrored.Filled.List
                            PlaylistSortOption.ARTIST -> Icons.Default.Person
                            PlaylistSortOption.ALBUM -> Icons.Default.Menu
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val nextDesc = if (currentSort == option) {
                                        !isDescending
                                    } else {
                                        option == PlaylistSortOption.DATE
                                    }
                                    currentSort = option
                                    isDescending = nextDesc
                                    setPlaylistSort(context, playlistId, option, nextDesc)
                                    showSortSheet = false
                                }
                                .padding(16.dp, 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) Color(AppPalette.toArgb()) else Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(18.dp))
                             Text(
                                 text = if (isSelected) option.getDescriptiveLabel(isDescending) else option.getDescriptiveLabel(option == PlaylistSortOption.DATE),
                                 color = if (isSelected) Color(AppPalette.toArgb()) else Color.White,
                                 fontSize = 15.sp,
                                 modifier = Modifier.weight(1f)
                             )
                            if (isSelected) {
                                Icon(
                                    imageVector = if (isDescending) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                    contentDescription = null,
                                    tint = Color(AppPalette.toArgb()),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

