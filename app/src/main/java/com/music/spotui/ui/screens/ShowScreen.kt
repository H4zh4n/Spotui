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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
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
import com.music.spotui.data.api.Response
import com.music.spotui.data.preferences.ShowSortOption
import com.music.spotui.data.preferences.getShowSortOption
import com.music.spotui.data.preferences.isShowSortDescending
import com.music.spotui.data.preferences.setShowSortOption
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.components.Loader
import com.music.spotui.ui.components.SwipeToPlayNextWrapper
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.AppPalette
import com.music.spotui.ui.viewmodel.PlayerViewModel
import com.music.spotui.ui.viewmodel.ShowViewModel

@OptIn(ExperimentalGlideComposeApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ShowScreen(navController: NavController, showId: String, showName: String = "") {
    val vm: ShowViewModel = hiltViewModel()
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val context = LocalContext.current
    LaunchedEffect(showId) { vm.loadShow(showId) }

    val episodesState by vm.episodes.collectAsState()
    val show by vm.show.collectAsState()
    val episodes = (episodesState as? Response.Success)?.data.orEmpty()

    var searchQuery by remember(showId) { mutableStateOf("") }
    var currentSort by remember(showId) { mutableStateOf(getShowSortOption(context, showId)) }
    var isDescending by remember(showId) { mutableStateOf(isShowSortDescending(context, showId)) }
    var showSortSheet by remember { mutableStateOf(false) }

    val filteredEpisodes = remember(episodes, searchQuery, currentSort, isDescending) {
        val filtered = if (searchQuery.isBlank()) {
            episodes
        } else {
            episodes.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.singer.contains(searchQuery, ignoreCase = true)
            }
        }
        when (currentSort) {
            ShowSortOption.DATE -> if (isDescending) filtered else filtered.reversed()
            ShowSortOption.TITLE -> if (isDescending) filtered.sortedByDescending { it.title.lowercase() } else filtered.sortedBy { it.title.lowercase() }
            ShowSortOption.DURATION -> if (isDescending) filtered.sortedByDescending { it.durationMs } else filtered.sortedBy { it.durationMs }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(AppBackground.toArgb()))
                .statusBarsPadding(),
        ) {
            item {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    tint = Color.White,
                    contentDescription = "Back",
                    modifier = Modifier
                        .padding(16.dp)
                        .size(26.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { navController.navigateUp() },
                )
            }
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    GlideImage(
                        model = show?.coverUri ?: episodes.firstOrNull()?.coverUri,
                        contentScale = ContentScale.Crop,
                        failure = placeholder(R.drawable.placeholder),
                        modifier = Modifier.size(180.dp).clip(RoundedCornerShape(8.dp)),
                        contentDescription = null,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = show?.name ?: showName,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    show?.publisher?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = Color(0xFFB3B3B3), fontSize = 13.sp)
                    }
                    if (episodes.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Icon(
                            painter = painterResource(id = R.drawable.ic_queue_add),
                            tint = Color.White,
                            modifier = Modifier
                                .size(24.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    playerViewModel.addAllToQueue(episodes)
                                    android.widget.Toast.makeText(
                                        context,
                                        "${episodes.size} episode(s) added to queue",
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                },
                            contentDescription = "Add all to queue",
                        )
                    }
                }
            }

            if (episodesState is Response.Loading) {
                item { Loader() }
            }

            if (episodes.isNotEmpty()) {
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
                                    text = "Search in episodes",
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

            if (filteredEpisodes.isEmpty() && searchQuery.isNotBlank()) {
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
                items(filteredEpisodes.size) { i ->
                    val ep = filteredEpisodes[i]
                    SwipeToPlayNextWrapper(
                        onPlayNext = {
                            playerViewModel.playNext(ep)
                            android.widget.Toast.makeText(
                                context,
                                "${ep.title} will play next",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AppBackground)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    vm.updateQueue(filteredEpisodes)
                                    SongPlayer.playSong(ep.url, context)
                                    vm.updateSongState(ep.coverUri, ep.title, ep.singer, true, ep.id, i, ep.album)
                                }
                                .padding(16.dp, 10.dp),
                        ) {
                            GlideImage(
                                model = ep.coverUri,
                                contentScale = ContentScale.Crop,
                                failure = placeholder(R.drawable.placeholder),
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)),
                                contentDescription = null,
                            )
                            Column(modifier = Modifier.padding(start = 12.dp)) {
                                Text(
                                    ep.title,
                                    color = if (ep.id == vm.currentSongId.value) Color(0xFF1ED760) else Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(ep.singer, color = Color(0xFFB3B3B3), fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(130.dp)) }
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
                    ShowSortOption.entries.forEach { option ->
                        val isSelected = option == currentSort
                        val icon = when (option) {
                            ShowSortOption.DATE -> Icons.Default.DateRange
                            ShowSortOption.TITLE -> Icons.AutoMirrored.Filled.List
                            ShowSortOption.DURATION -> Icons.Default.Menu
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (currentSort == option) {
                                        isDescending = !isDescending
                                    } else {
                                        currentSort = option
                                        isDescending = (option == ShowSortOption.DATE)
                                    }
                                    setShowSortOption(context, showId, currentSort, isDescending)
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
                                text = if (isSelected) option.getDescriptiveLabel(isDescending) else option.getDescriptiveLabel(option == ShowSortOption.DATE),
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
