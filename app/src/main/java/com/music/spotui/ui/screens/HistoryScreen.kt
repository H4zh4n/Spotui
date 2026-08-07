package com.music.spotui.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.music.spotui.R
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.data.preferences.HistoryEntry
import com.music.spotui.data.preferences.HistorySortOption
import com.music.spotui.data.preferences.clearListeningHistory
import com.music.spotui.data.preferences.getHistorySortOption
import com.music.spotui.data.preferences.getListeningHistory
import com.music.spotui.data.preferences.isHistorySortDescending
import com.music.spotui.data.preferences.removeListeningHistory
import com.music.spotui.data.preferences.setHistorySortOption
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.navigation.artistRoute
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.AppPalette
import com.music.spotui.ui.viewmodel.PlayerViewModel
import java.text.DateFormat
import java.util.Date

private val CardBg = Color(0xFF18181C)
private val BarTrack = Color(0xFF2A2A2A)
private val SpotifyGreen = Color(0xFF1ED760)
private val MutedText = Color(0xFFB3B3B3)

@OptIn(ExperimentalGlideComposeApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(navController: NavController) {
    val context = LocalContext.current
    val playerViewModel: PlayerViewModel = hiltViewModel()
    var history by remember { mutableStateOf(getListeningHistory(context)) }
    var showClearDialog by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var currentSort by remember { mutableStateOf(getHistorySortOption(context)) }
    var isDescending by remember { mutableStateOf(isHistorySortDescending(context)) }
    var showSortSheet by remember { mutableStateOf(false) }

    val filteredHistory = remember(history, searchQuery, currentSort, isDescending) {
        val filtered = if (searchQuery.isBlank()) {
            history
        } else {
            history.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.singer.contains(searchQuery, ignoreCase = true) ||
                        it.album.contains(searchQuery, ignoreCase = true)
            }
        }
        when (currentSort) {
            HistorySortOption.DATE -> if (isDescending) filtered else filtered.reversed()
            HistorySortOption.TITLE -> if (isDescending) filtered.sortedByDescending { it.title.lowercase() } else filtered.sortedBy { it.title.lowercase() }
            HistorySortOption.ARTIST -> if (isDescending) filtered.sortedByDescending { it.singer.lowercase() } else filtered.sortedBy { it.singer.lowercase() }
        }
    }

    fun playEntry(entry: HistoryEntry) {
        if (entry.url.isBlank()) return
        playerViewModel.updateQueue(listOf(
            SongsModel(
                id = entry.songId,
                title = entry.title,
                album = entry.album,
                singer = entry.singer,
                coverUri = entry.image,
                url = entry.url,
            ),
        ))
        playerViewModel.updateSongState(
            entry.image, entry.title, entry.singer, true, entry.songId, 0, entry.album,
        )
        SongPlayer.playSong(entry.url, context, "song/${entry.songId}")
    }

    val topArtists = remember(history) {
        history.groupingBy { it.singer.substringBefore(",").trim() }
            .eachCount().entries
            .filter { it.key.isNotBlank() }
            .sortedByDescending { it.value }
            .take(5)
    }
    val topTracks = remember(history) {
        history.groupingBy { "${it.title} — ${it.singer}" }
            .eachCount().entries
            .sortedByDescending { it.value }
            .take(5)
    }

    val maxArtistPlays = remember(topArtists) { topArtists.firstOrNull()?.value?.toFloat() ?: 1f }
    val maxTrackPlays = remember(topTracks) { topTracks.firstOrNull()?.value?.toFloat() ?: 1f }

    Surface(modifier = Modifier.fillMaxSize()) {
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(AppBackground.toArgb()))
                    .statusBarsPadding()
            ) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp, 16.dp, 16.dp, 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(26.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { navController.navigateUp() },
                            )
                            Spacer(Modifier.width(16.dp))
                            Text("Listening history", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                        if (history.isNotEmpty()) {
                            Text(
                                "Clear all",
                                color = MutedText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { showClearDialog = true },
                            )
                        }
                    }
                }

                if (history.isEmpty()) {
                    item {
                        Text(
                            "Nothing here yet — play something!",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(16.dp, 32.dp),
                        )
                    }
                } else {
                    // ── Stats card ──
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF2E1C5A),
                                            Color(0xFF181824),
                                        )
                                    )
                                )
                                .padding(16.dp),
                        ) {
                            Column {
                                Text("YOUR LISTENING HABITS", color = Color(0xFFB09BE8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround,
                                ) {
                                    StatPill("Total plays", "${history.size}", SpotifyGreen)
                                    StatPill("Unique songs", "${history.distinctBy { it.songId }.size}", Color(0xFF4CB0E8))
                                    StatPill("Artists", "${history.distinctBy { it.singer.substringBefore(",") }.size}", Color(0xFFE89BDB))
                                }
                            }
                        }
                    }

                    // ── Top artists ──
                    if (topArtists.isNotEmpty()) {
                        item {
                            Text(
                                "Top artists",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp),
                            )
                        }
                        items(topArtists.size) { i ->
                            val entry = topArtists[i]
                            val artistImage = remember(entry.key, history) {
                                history.lastOrNull {
                                    it.singer.substringBefore(",").trim() == entry.key
                                }?.image ?: ""
                            }
                            TopArtistRow(
                                rank = i + 1,
                                name = entry.key,
                                plays = entry.value,
                                progress = entry.value.toFloat() / maxArtistPlays,
                                imageUrl = artistImage,
                            ) {
                                navController.navigate(artistRoute(entry.key))
                            }
                        }
                    }

                    // ── Top tracks ──
                    if (topTracks.isNotEmpty()) {
                        item {
                            Text(
                                "Top tracks",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp),
                            )
                        }
                        items(topTracks.size) { i ->
                            val entry = topTracks[i]
                            val parts = entry.key.split(" — ", limit = 2)
                            val trackTitle = parts.getOrElse(0) { entry.key }
                            val trackArtist = parts.getOrElse(1) { "" }
                            val trackImage = remember(entry.key, history) {
                                history.lastOrNull {
                                    "${it.title} — ${it.singer}" == entry.key
                                }?.image ?: ""
                            }
                            TopTrackRow(
                                rank = i + 1,
                                title = trackTitle,
                                artist = trackArtist,
                                plays = entry.value,
                                progress = entry.value.toFloat() / maxTrackPlays,
                                imageUrl = trackImage,
                            ) {
                                val latest = history.lastOrNull {
                                    "${it.title} — ${it.singer}" == entry.key
                                }
                                if (latest != null) playEntry(latest)
                            }
                        }
                    }

                    // ── History list ──
                    item {
                        Text(
                            "History",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp),
                        )
                    }

                    // Search Bar
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp, 8.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .height(50.dp)
                                .background(Color.White)
                                .padding(10.dp, 0.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_search_big),
                                tint = Color.Black,
                                contentDescription = "Search",
                                modifier = Modifier.size(22.dp)
                            )

                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.weight(1f),
                                textStyle = TextStyle.Default.copy(
                                    fontSize = 15.sp,
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
                                        text = "Search in history",
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
                                        .size(22.dp)
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
                                .padding(16.dp, 0.dp, 16.dp, 8.dp),
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

                    if (filteredHistory.isEmpty() && searchQuery.isNotBlank()) {
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
                        items(filteredHistory.size) { i ->
                            val entry = filteredHistory[i]
                            HistoryRow(
                                entry = entry,
                                onRemove = {
                                    removeListeningHistory(context, entry)
                                    history = getListeningHistory(context)
                                },
                                onClick = { playEntry(entry) },
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(140.dp)) }
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
                        HistorySortOption.entries.forEach { option ->
                            val isSelected = option == currentSort
                            val icon = when (option) {
                                HistorySortOption.DATE -> Icons.Default.DateRange
                                HistorySortOption.TITLE -> Icons.AutoMirrored.Filled.List
                                HistorySortOption.ARTIST -> Icons.Default.Person
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (currentSort == option) {
                                            isDescending = !isDescending
                                        } else {
                                            currentSort = option
                                            isDescending = (option == HistorySortOption.DATE)
                                        }
                                        setHistorySortOption(context, currentSort, isDescending)
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
                                    text = if (isSelected) option.getDescriptiveLabel(isDescending) else option.getDescriptiveLabel(option == HistorySortOption.DATE),
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

            if (showClearDialog) {
                AlertDialog(
                    onDismissRequest = { showClearDialog = false },
                    title = { Text("Clear listening history?", color = Color.White, fontWeight = FontWeight.Bold) },
                    text = { Text("This will remove all ${history.size} plays. This action cannot be undone.", color = MutedText) },
                    confirmButton = {
                        TextButton(onClick = {
                            clearListeningHistory(context)
                            history = emptyList()
                            showClearDialog = false
                        }) {
                            Text("Clear", color = Color(0xFFE57373))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearDialog = false }) {
                            Text("Cancel", color = Color.White)
                        }
                    },
                    containerColor = Color(0xFF1A1A1A),
                    titleContentColor = Color.White,
                    textContentColor = MutedText,
                )
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, color = MutedText, fontSize = 11.sp)
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun TopArtistRow(
    rank: Int,
    name: String,
    plays: Int,
    progress: Float,
    imageUrl: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(CardBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(12.dp),
    ) {
        Text(
            text = "$rank",
            color = MutedText,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(22.dp),
        )
        GlideImage(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
            model = imageUrl.ifBlank { null },
            contentScale = ContentScale.Crop,
            failure = placeholder(R.drawable.placeholder),
            loading = placeholder(R.drawable.placeholder),
            contentDescription = "",
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                name,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = AppPalette,
                trackColor = BarTrack,
                strokeCap = StrokeCap.Round,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "$plays plays",
                color = MutedText,
                fontSize = 12.sp,
            )
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun TopTrackRow(
    rank: Int,
    title: String,
    artist: String,
    plays: Int,
    progress: Float,
    imageUrl: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(CardBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(12.dp),
    ) {
        Text(
            text = "$rank",
            color = MutedText,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(22.dp),
        )
        GlideImage(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp)),
            model = imageUrl.ifBlank { null },
            contentScale = ContentScale.Crop,
            failure = placeholder(R.drawable.placeholder),
            loading = placeholder(R.drawable.placeholder),
            contentDescription = "",
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (artist.isNotBlank()) {
                Text(
                    artist,
                    color = MutedText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = AppPalette,
                trackColor = BarTrack,
                strokeCap = StrokeCap.Round,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "$plays plays",
                color = MutedText,
                fontSize = 12.sp,
            )
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun HistoryRow(entry: HistoryEntry, onRemove: () -> Unit, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp, 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
    ) {
        GlideImage(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(4.dp)),
            model = entry.image,
            contentScale = ContentScale.Crop,
            failure = placeholder(R.drawable.placeholder),
            loading = placeholder(R.drawable.placeholder),
            contentDescription = "",
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp, end = 8.dp),
        ) {
            Text(entry.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${entry.singer} • ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.ts))}",
                color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Remove",
            tint = MutedText,
            modifier = Modifier
                .size(18.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onRemove() },
        )
    }
}
