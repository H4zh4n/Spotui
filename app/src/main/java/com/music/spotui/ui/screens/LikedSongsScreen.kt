package com.music.spotui.ui.screens

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import com.music.spotui.data.preferences.LikedSongsSortOption
import com.music.spotui.data.preferences.getLikedSongsSortOption
import com.music.spotui.data.preferences.isLikedSongsSortDescending
import com.music.spotui.data.preferences.setLikedSongsSortOption
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.components.Loader
import com.music.spotui.ui.components.Snackbar
import com.music.spotui.ui.components.SwipeToPlayNextWrapper
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.AppPalette
import com.music.spotui.ui.viewmodel.LikedSongsViewModel
import com.music.spotui.ui.viewmodel.PlayerViewModel

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class, ExperimentalFoundationApi::class)
@Composable
fun LikedSongsScreen(navController: NavController) {

    val likedSongsViewModel: LikedSongsViewModel = hiltViewModel()
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val songsResp by likedSongsViewModel.songs.collectAsState()
    val context = LocalContext.current

    val songs = (songsResp as? Response.Success)?.data.orEmpty()

    LaunchedEffect(songs) {
        if (songs.isNotEmpty()) {
            SongPlayer.prefetchList(songs.map { it.url }, context)
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var currentSort by remember { mutableStateOf(getLikedSongsSortOption(context)) }
    var isDescending by remember { mutableStateOf(isLikedSongsSortDescending(context)) }
    var showSortSheet by remember { mutableStateOf(false) }

    val filteredSongs = remember(songs, searchQuery, currentSort, isDescending) {
        val filtered = if (searchQuery.isBlank()) {
            songs
        } else {
            songs.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.singer.contains(searchQuery, ignoreCase = true) ||
                        it.album.contains(searchQuery, ignoreCase = true)
            }
        }
        when (currentSort) {
            LikedSongsSortOption.DATE -> if (isDescending) filtered else filtered.reversed()
            LikedSongsSortOption.TITLE -> if (isDescending) filtered.sortedByDescending { it.title.lowercase() } else filtered.sortedBy { it.title.lowercase() }
            LikedSongsSortOption.ARTIST -> if (isDescending) filtered.sortedByDescending { it.singer.lowercase() } else filtered.sortedBy { it.singer.lowercase() }
            LikedSongsSortOption.ALBUM -> if (isDescending) filtered.sortedByDescending { it.album.lowercase() } else filtered.sortedBy { it.album.lowercase() }
        }
    }

    var menuSong by remember { mutableStateOf<com.music.spotui.data.entity.SongsModel?>(null) }
    menuSong?.let { sel ->
        com.music.spotui.ui.components.SongOptionsSheet(
            song = sel,
            navController = navController,
            context = context,
            onDismiss = {
                if (!com.music.spotui.data.preferences.isSongLiked(context, sel.id.toString())) {
                    likedSongsViewModel.removeLocally(sel.id)
                }
                menuSong = null
            },
        )
    }

    var snackbarMessage by remember { mutableStateOf("") }
    var snackbarVisible by remember { mutableStateOf(false) }
    LaunchedEffect(snackbarVisible) {
        if (snackbarVisible) {
            kotlinx.coroutines.delay(1500)
            snackbarVisible = false
        }
    }

    val likedColor = Color(0xFF5038A0)

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(AppBackground.toArgb()))
    ) {
        if (songsResp is Response.Loading) {
            Loader()
            return@Surface
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
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(AppBackground.toArgb()))
                ) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(440.dp)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(likedColor, Color(AppBackground.toArgb())),
                                        startY = -100f,
                                    ),
                                ),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Spacer(modifier = Modifier.padding(25.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(230.dp)
                                        .background(
                                            brush = Brush.linearGradient(
                                                colors = listOf(Color(0xFF8E6FE0), Color(0xFF3B2A82)),
                                            )
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Favorite,
                                        contentDescription = "",
                                        tint = Color.White,
                                        modifier = Modifier.size(90.dp),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.padding(5.dp))
                            Text(
                                modifier = Modifier.padding(20.dp, 5.dp, 0.dp, 0.dp),
                                text = "Liked Songs",
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                modifier = Modifier.padding(20.dp, 4.dp, 20.dp, 0.dp),
                                text = "${songs.size} songs",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .padding(20.dp, 0.dp)
                            ) {
                                var likedDownloaded by remember(songs) {
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
                                            Icon(
                                                imageVector = if (likedDownloaded)
                                                    Icons.Default.CheckCircle else ImageVector.vectorResource(R.drawable.ic_download),
                                                tint = if (likedDownloaded) Color(AppPalette.toArgb()) else Color.White,
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clickable(
                                                        interactionSource = remember { MutableInteractionSource() },
                                                        indication = null,
                                                    ) {
                                                        if (!likedDownloaded) {
                                                            SongPlayer.downloadAll(songs, context)
                                                            snackbarMessage = "Downloading ${songs.size} tracks…"
                                                            snackbarVisible = true
                                                        }
                                                    },
                                                contentDescription = "Download liked songs",
                                            )
                                            Spacer(modifier = Modifier.width(18.dp))
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_queue_add),
                                                tint = Color.White,
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clickable(
                                                        interactionSource = remember { MutableInteractionSource() },
                                                        indication = null,
                                                    ) {
                                                        playerViewModel.addAllToQueue(songs)
                                                        android.widget.Toast.makeText(
                                                            context,
                                                            "${songs.size} track(s) added to queue",
                                                            android.widget.Toast.LENGTH_SHORT,
                                                        ).show()
                                                    },
                                                contentDescription = "Add to queue",
                                            )
                                            Spacer(modifier = Modifier.width(18.dp))
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_player_shuffle),
                                                tint = Color.White,
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clickable(
                                                        interactionSource = remember { MutableInteractionSource() },
                                                        indication = null,
                                                    ) {
                                                        likedSongsViewModel.startShuffled(songs)?.let { first ->
                                                            SongPlayer.playSong(first.url, context)
                                                            likedSongsViewModel.updateSongState(
                                                                first.coverUri,
                                                                first.title,
                                                                first.singer,
                                                                true,
                                                                first.id,
                                                                0,
                                                                "Liked Songs",
                                                            )
                                                        }
                                                    },
                                                contentDescription = "Shuffle play",
                                            )
                                        }
                                    }
                                }
                                if (songs.isNotEmpty()) {
                                    val playing = likedSongsViewModel.currentSongPlayingState.value
                                    val currentInList = songs.any { it.id == likedSongsViewModel.currentSongId.value }
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
                                                    currentInList -> likedSongsViewModel.setPlaying(!playing)
                                                    else -> {
                                                        likedSongsViewModel.updateQueue(songs)
                                                        SongPlayer.playSong(songs[0].url, context)
                                                        likedSongsViewModel.updateSongState(
                                                            songs[0].coverUri,
                                                            songs[0].title,
                                                            songs[0].singer,
                                                            true,
                                                            songs[0].id,
                                                            0,
                                                            "Liked Songs"
                                                        )
                                                    }
                                                }
                                            }
                                    ) {
                                        Icon(
                                            modifier = Modifier.size(25.dp),
                                            tint = Color.Black,
                                            painter = painterResource(
                                                id = if (currentInList && playing) R.drawable.ic_playing else R.drawable.play_svgrepo_com,
                                            ),
                                            contentDescription = if (currentInList && playing) "Pause" else "Play"
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (songs.isNotEmpty()) {
                        // Search Bar
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
                                            text = "Search in liked songs",
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

                        // Sort action button
                        item {
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

                    if (filteredSongs.isEmpty() && searchQuery.isNotBlank()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No matches found for \"$searchQuery\"",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    } else {
                        itemsIndexed(filteredSongs, key = { _, song -> song.id }) { index, song ->
                            val currentColor = if (song.id == likedSongsViewModel.currentSongId.value)
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
                                                likedSongsViewModel.updateQueue(filteredSongs)
                                                SongPlayer.playSong(song.url, context)
                                                likedSongsViewModel.updateSongState(
                                                    song.coverUri,
                                                    song.title,
                                                    song.singer,
                                                    true,
                                                    song.id,
                                                    index,
                                                    "Liked Songs"
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
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (song.explicit) {
                                                com.music.spotui.ui.components.ExplicitBadge()
                                                Spacer(Modifier.width(4.dp))
                                            }
                                            Text(
                                                text = song.title,
                                                color = currentColor,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1
                                            )
                                        }
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
                    }

                    item { Spacer(modifier = Modifier.height(160.dp)) }
                }

                com.music.spotui.ui.components.FastScrollbarForLazyList(
                    state = listState,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )

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
                            LikedSongsSortOption.entries.forEach { option ->
                                val isSelected = option == currentSort
                                val icon = when (option) {
                                    LikedSongsSortOption.DATE -> Icons.Default.DateRange
                                    LikedSongsSortOption.TITLE -> Icons.AutoMirrored.Filled.List
                                    LikedSongsSortOption.ARTIST -> Icons.Default.Person
                                    LikedSongsSortOption.ALBUM -> Icons.Default.Menu
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (currentSort == option) {
                                                isDescending = !isDescending
                                            } else {
                                                currentSort = option
                                                isDescending = (option == LikedSongsSortOption.DATE)
                                            }
                                            setLikedSongsSortOption(context, currentSort, isDescending)
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
                                        text = if (isSelected) option.getDescriptiveLabel(isDescending) else option.getDescriptiveLabel(option == LikedSongsSortOption.DATE),
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
    }
}
