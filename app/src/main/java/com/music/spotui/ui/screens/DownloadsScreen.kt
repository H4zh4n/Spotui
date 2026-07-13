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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person


import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import com.music.spotui.data.preferences.DownloadSortOption
import com.music.spotui.data.preferences.getDownloadedSongs
import com.music.spotui.data.preferences.getDownloadsSortOption
import com.music.spotui.data.preferences.isDownloadsSortDescending
import com.music.spotui.data.preferences.setDownloadsSortOption
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.AppPalette
import com.music.spotui.ui.viewmodel.PlayerViewModel
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState

fun DownloadSortOption.getDescriptiveLabel(isDescending: Boolean): String {
    return when (this) {
        DownloadSortOption.DATE -> if (isDescending) "Date added (newest to oldest)" else "Date added (oldest to newest)"
        DownloadSortOption.TITLE -> if (isDescending) "Title (Z to A)" else "Title (A to Z)"
        DownloadSortOption.ARTIST -> if (isDescending) "Artist (Z to A)" else "Artist (A to Z)"
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalGlideComposeApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun DownloadsScreen(navController: NavController) {

    val playerViewModel: PlayerViewModel = hiltViewModel()
    val context = LocalContext.current

    // Completed downloads (from prefs) + live in-progress ones (from SongPlayer). Poll
    // while the screen is open so a track appears here the moment its download starts,
    // shows a live percentage, and moves into the list when it finishes.
    var songs by remember { mutableStateOf(getDownloadedSongs(context)) }
    var inProgress by remember {
        mutableStateOf(com.music.spotui.di.SongPlayer.downloadingSnapshot())
    }
    var showSortSheet by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var currentSort by remember { mutableStateOf(getDownloadsSortOption(context)) }
    var isDescending by remember { mutableStateOf(isDownloadsSortDescending(context)) }

    androidx.compose.runtime.LaunchedEffect(currentSort, isDescending) {
        songs = getDownloadedSongs(context)
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            val snap = com.music.spotui.di.SongPlayer.downloadingSnapshot()
            // A download leaving the snapshot means it finished → refresh the saved list.
            if (snap.size != inProgress.size) songs = getDownloadedSongs(context)
            inProgress = snap
            kotlinx.coroutines.delay(400)
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

    var searchQuery by remember { mutableStateOf("") }
    // filter downloads by simple title/singer string match
    val displayedSongs = remember(songs, searchQuery) {
        if (searchQuery.isBlank()) {
            songs
        } else {
            songs.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.singer.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val accent = Color(0xFF1DB954)

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(AppBackground.toArgb()))
    ) {
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(AppBackground.toArgb()))
                    .verticalScroll(rememberScrollState())
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    accent.copy(alpha = 0.5f),
                                    Color(AppBackground.toArgb())
                                ),
                                startY = -100f,
                            ),
                        ),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Spacer(modifier = Modifier.padding(25.dp))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .background(accent.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.ic_download),
                                contentDescription = "",
                                tint = accent,
                                modifier = Modifier.size(90.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.padding(5.dp))
                    Text(
                        modifier = Modifier.padding(20.dp, 5.dp, 0.dp, 0.dp),
                        text = "Downloaded",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        modifier = Modifier.padding(20.dp, 4.dp, 20.dp, 0.dp),
                        text = "${songs.size} songs • available offline",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // ── Search bar for downloads ──
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
                                text = "Search downloaded songs",
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

                // ── Sort and Clear all action ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp, 0.dp, 20.dp, 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (songs.isNotEmpty()) {
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
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    if (songs.isNotEmpty() && searchQuery.isBlank()) {
                        Text(
                            text = "Clear all",
                            color = Color(0xFFE57373),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFF1A1A20))
                                .clickable {
                                    showClearConfirmDialog = true
                                }
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                        )
                    }
                }

                // ── In-progress downloads (with live progress bar) ──
                inProgress.forEach { (song, pct) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp, 8.dp),
                    ) {
                        GlideImage(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            model = song.coverUri,
                            failure = placeholder(R.drawable.placeholder),
                            contentScale = ContentScale.Crop,
                            contentDescription = "",
                        )
                        Column(
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .weight(1f)
                        ) {
                            Text(
                                text = song.title,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { (pct.coerceIn(0, 100)) / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = accent,
                                trackColor = Color(0xFF333333),
                            )
                        }
                        Text(
                            text = "$pct%",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }

                if (songs.isEmpty() && inProgress.isEmpty()) {
                    Text(
                        text = "No downloads yet. Tap ⋯ on a track and choose Download.",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(20.dp),
                    )
                } else if (displayedSongs.isEmpty() && inProgress.isEmpty()) {
                    Text(
                        text = "No matches found for \"$searchQuery\"",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(20.dp),
                    )
                } else {
                    repeat(displayedSongs.size) { index ->
                        val song = displayedSongs[index]
                        val currentColor = if (song.id == playerViewModel.currentSongId.value)
                            Color(AppPalette.toArgb()) else Color.White

                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.StartToEnd) {
                                    playerViewModel.addToQueue(song)
                                    android.widget.Toast.makeText(
                                        context,
                                        "${song.title} added to queue",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                                false
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = true,
                            enableDismissFromEndToStart = false,
                            backgroundContent = {
                                Box(
                                    contentAlignment = Alignment.CenterStart,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF1DB954)) // Spotify Green
                                        .padding(horizontal = 24.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_queue_add),
                                        contentDescription = "Add to queue",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
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
                                            playerViewModel.updateQueue(displayedSongs)
                                            SongPlayer.playSong(song.url, context)
                                            playerViewModel.updateSongState(
                                                song.coverUri, song.title, song.singer,
                                                true, song.id, index, "Downloaded"
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
                                Column(
                                    modifier = Modifier
                                        .padding(start = 12.dp)
                                        .width(280.dp)
                                ) {
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
                }

                Spacer(modifier = Modifier.padding(80.dp))
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
                            DownloadSortOption.entries.forEach { option ->
                                val isSelected = option == currentSort
                                 val icon = when (option) {
                                     DownloadSortOption.DATE -> Icons.Default.DateRange
                                     DownloadSortOption.TITLE -> Icons.AutoMirrored.Filled.List
                                     DownloadSortOption.ARTIST -> Icons.Default.Person
                                 }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (currentSort == option) {
                                                isDescending = !isDescending
                                            } else {
                                                currentSort = option
                                                isDescending = (option == DownloadSortOption.DATE)
                                            }
                                            setDownloadsSortOption(context, currentSort, isDescending)
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
                                        text = if (isSelected) option.getDescriptiveLabel(isDescending) else option.getDescriptiveLabel(option == DownloadSortOption.DATE),
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
                if (showClearConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { showClearConfirmDialog = false },
                        title = { Text(text = "Clear all downloads?", color = Color.White, fontWeight = FontWeight.Bold) },
                        text = { Text(text = "Are you sure you want to remove all downloaded songs? This action cannot be undone.", color = Color(0xFFB3B3B3)) },
                        confirmButton = {
                            TextButton(onClick = {
                                val n =
                                    com.music.spotui.data.preferences.clearAllDownloads(context)
                                songs = getDownloadedSongs(context)
                                showClearConfirmDialog = false
                                android.widget.Toast.makeText(
                                    context, "Removed $n download${if (n == 1) "" else "s"}",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }) {
                                Text("Clear", color = Color(0xFFE57373))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showClearConfirmDialog = false }) {
                                Text("Cancel", color = Color.White)
                            }
                        },
                        containerColor = Color(0xFF1A1A1A),
                        titleContentColor = Color.White,
                        textContentColor = Color(0xFFB3B3B3),
                    )
                }
            }
        }
    }
}
