package com.music.spotui.ui.screens

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.music.spotui.data.api.SpotifySync
import com.music.spotui.data.preferences.OfflineCollectionsPref
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.music.spotui.R
import com.music.spotui.data.api.Response
import com.music.spotui.data.entity.AlbumsModel
import com.music.spotui.data.entity.ArtistsModel
import com.music.spotui.data.entity.HomeFeedModel
import com.music.spotui.data.entity.HomeItem
import com.music.spotui.data.entity.HomeSection
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.components.Loader
import com.music.spotui.ui.navigation.Routes
import com.music.spotui.ui.navigation.albumRoute
import com.music.spotui.ui.navigation.artistRoute
import com.music.spotui.ui.navigation.playlistRoute
import com.music.spotui.ui.navigation.showRoute
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.AppPalette
import com.music.spotui.ui.theme.GridBackground
import com.music.spotui.ui.viewmodel.HomeViewModel
import com.music.spotui.ui.viewmodel.PlayerViewModel
import java.time.LocalTime


@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun HomeScreen(navController: NavController){

    val homeViewModel : HomeViewModel = hiltViewModel()
    val home by homeViewModel.home.collectAsState()
    val albums by homeViewModel.albums.collectAsState()
    val artists by homeViewModel.artists.collectAsState()
    val songs by homeViewModel.songs.collectAsState()
    val podcasts by homeViewModel.podcasts.collectAsState()
    val audiobooks by homeViewModel.audiobooks.collectAsState()
    val followedArtists by homeViewModel.followedArtists.collectAsState()
    val selectedFilter by homeViewModel.selectedFilter.collectAsState()
    val isFollowingOnly by homeViewModel.isFollowingOnly.collectAsState()

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(AppBackground.toArgb()))
            .statusBarsPadding()
    ) {
        val feed = (home as? Response.Success)?.data
        val albumsList = (albums as? Response.Success)?.data.orEmpty()
        val artistsList = (artists as? Response.Success)?.data.orEmpty()
        val songsList = (songs as? Response.Success)?.data.orEmpty()

        when (selectedFilter) {
            "Music" -> {
                HomeMusicFeedContent(
                    navController = navController,
                    homeViewModel = homeViewModel,
                    feed = feed,
                    albumsList = albumsList,
                    artistsList = artistsList,
                    songsList = songsList,
                    followedArtists = followedArtists,
                    selectedFilter = selectedFilter,
                    isFollowingOnly = isFollowingOnly
                )
            }
            "Podcasts" -> {
                HomePodcastsFeedContent(
                    navController = navController,
                    homeViewModel = homeViewModel,
                    podcastsResult = podcasts,
                    selectedFilter = selectedFilter,
                    isFollowingOnly = isFollowingOnly
                )
            }
            "Audiobooks" -> {
                HomeAudiobooksFeedContent(
                    navController = navController,
                    homeViewModel = homeViewModel,
                    audiobooksResult = audiobooks,
                    selectedFilter = selectedFilter
                )
            }
            else -> {
                when {
                    feed != null && feed.sections.isNotEmpty() -> {
                        HomeFeedContent(
                            navController = navController,
                            feed = feed,
                            homeViewModel = homeViewModel,
                            selectedFilter = selectedFilter,
                            isFollowingOnly = isFollowingOnly
                        )
                    }
                    home is Response.Loading -> {
                        Loader()
                    }
                    albumsList.isNotEmpty() || artistsList.isNotEmpty() -> {
                        SumUpHomeScreen(
                            navController = navController,
                            albums = albumsList,
                            artists = artistsList,
                            homeViewModel = homeViewModel,
                            selectedFilter = selectedFilter,
                            isFollowingOnly = isFollowingOnly
                        )
                    }
                    else -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Text(
                                    text = "Spotify session expired or unauthenticated",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Log in to load your personalized playlists, recommendations, and library.",
                                    color = Color.Gray,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(Color(0xFF1ED760))
                                        .clickable { navController.navigate(Routes.Login.route) }
                                        .padding(horizontal = 24.dp, vertical = 12.dp)
                                ) {
                                    Text(
                                        text = "Log in to Spotify",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Go to Downloads",
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                    modifier = Modifier
                                        .clickable { navController.navigate(Routes.Downloads.route) }
                                        .padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun onHomeItemClick(navController: NavController, item: HomeItem) {
    when (item) {
        is HomeItem.Album -> navController.navigate(albumRoute(item.name, item.artists.ifBlank { item.subtitle }))
        is HomeItem.Artist -> navController.navigate(artistRoute(item.name, item.id))
        is HomeItem.Playlist ->
            if (item.id.isNotBlank()) navController.navigate(playlistRoute(item.id, item.name))
            else navController.navigate(albumRoute(item.name))
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun HomeFeedContent(
    navController: NavController,
    feed: HomeFeedModel,
    homeViewModel: HomeViewModel,
    selectedFilter: String,
    isFollowingOnly: Boolean
) {
    val sections = feed.sections
    val gridSection = sections.firstOrNull()?.takeIf { it.title.isBlank() }
    val carousels = if (gridSection != null) sections.drop(1) else sections

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(AppBackground.toArgb()))
    ) {
        item {
            HomeHeaderRow(
                navController = navController,
                selectedFilter = selectedFilter,
                isFollowingOnly = isFollowingOnly,
                onSelectFilter = { homeViewModel.setSelectedFilter(it) },
                onToggleFollowing = { homeViewModel.toggleFollowing() },
                onResetFilters = { homeViewModel.resetFilters() }
            )
        }
        gridSection?.let { section ->
            item {
                HomeShortcutGrid(navController, section.items.take(8))
            }
        }
        items(carousels.size) { i ->
            HomeFeedSection(navController, carousels[i])
        }
        item { Spacer(modifier = Modifier.height(160.dp)) }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun HomeHeaderRow(
    navController: NavController,
    selectedFilter: String,
    isFollowingOnly: Boolean,
    onSelectFilter: (String) -> Unit,
    onToggleFollowing: () -> Unit,
    onResetFilters: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.music.spotui.data.api.ProfileCache.ensure(context)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 26.dp, bottom = 8.dp),
    ) {
        val avatarUrl = com.music.spotui.data.api.ProfileCache.imageUrl
        val initial = com.music.spotui.data.api.ProfileCache.name
            ?.trim()?.firstOrNull()?.uppercase() ?: "•"
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(start = 16.dp)
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(0xFFE8622C))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { navController.navigate(Routes.Settings.route) },
        ) {
            if (avatarUrl != null) {
                GlideImage(
                    model = avatarUrl,
                    contentScale = ContentScale.Crop,
                    contentDescription = "Profile",
                    modifier = Modifier.size(34.dp),
                )
            } else {
                Text(
                    text = initial,
                    color = Color.Black,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            val filters = listOf("All", "Music", "Podcasts", "Audiobooks")
            items(filters.size) { i ->
                val label = filters[i]
                when (label) {
                    "All" -> {
                        val isSel = selectedFilter == "All"
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (isSel) Color(0xFF64D36D) else Color(0xFF2A2A2A))
                                .clickable { onResetFilters() }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = "All",
                                color = if (isSel) Color.Black else Color.White,
                                fontSize = 14.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                    }
                    "Music" -> {
                        val isSel = selectedFilter == "Music"
                        if (isSel) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .zIndex(2f)
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0xFF64D36D))
                                        .clickable { onResetFilters() }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "Music",
                                        color = Color.Black,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .zIndex(1f)
                                        .offset(x = (-16).dp)
                                        .clip(RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50, topStartPercent = 0, bottomStartPercent = 0))
                                        .background(if (isFollowingOnly) Color(0xFF59B861) else Color(0xFF2A2A2A))
                                        .clickable { onToggleFollowing() }
                                        .padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                                ) {
                                    Text(
                                        text = "Following",
                                        color = if (isFollowingOnly) Color.Black else Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = if (isFollowingOnly) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFF2A2A2A))
                                    .clickable { onSelectFilter("Music") }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = "Music",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                    "Podcasts" -> {
                        val isSel = selectedFilter == "Podcasts"
                        if (isSel) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .zIndex(2f)
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0xFF64D36D))
                                        .clickable { onResetFilters() }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "Podcasts",
                                        color = Color.Black,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .zIndex(1f)
                                        .offset(x = (-16).dp)
                                        .clip(RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50, topStartPercent = 0, bottomStartPercent = 0))
                                        .background(if (isFollowingOnly) Color(0xFF59B861) else Color(0xFF2A2A2A))
                                        .clickable { onToggleFollowing() }
                                        .padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                                ) {
                                    Text(
                                        text = "Following",
                                        color = if (isFollowingOnly) Color.Black else Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = if (isFollowingOnly) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFF2A2A2A))
                                    .clickable { onSelectFilter("Podcasts") }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = "Podcasts",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                    "Audiobooks" -> {
                        val isSel = selectedFilter == "Audiobooks"
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (isSel) Color(0xFF64D36D) else Color(0xFF2A2A2A))
                                .clickable {
                                    if (isSel) onResetFilters() else onSelectFilter("Audiobooks")
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = "Audiobooks",
                                color = if (isSel) Color.Black else Color.White,
                                fontSize = 14.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun HomeMusicFeedContent(
    navController: NavController,
    homeViewModel: HomeViewModel,
    feed: HomeFeedModel?,
    albumsList: List<AlbumsModel>,
    artistsList: List<ArtistsModel>,
    songsList: List<SongsModel>,
    followedArtists: List<ArtistsModel>,
    selectedFilter: String,
    isFollowingOnly: Boolean,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val playerViewModel: PlayerViewModel = hiltViewModel()

    val followedNames = remember(followedArtists) { followedArtists.map { it.name.lowercase().trim() }.toSet() }

    val filteredAlbums = remember(albumsList, isFollowingOnly, followedNames) {
        if (!isFollowingOnly || followedNames.isEmpty()) albumsList
        else albumsList.filter { album ->
            followedNames.any { fName -> album.artists.lowercase().contains(fName) }
        }
    }

    val filteredArtists = remember(artistsList, followedArtists, isFollowingOnly) {
        if (!isFollowingOnly) artistsList
        else if (followedArtists.isNotEmpty()) followedArtists
        else artistsList
    }

    val filteredSongs = remember(songsList, isFollowingOnly, followedNames) {
        if (!isFollowingOnly || followedNames.isEmpty()) songsList
        else songsList.filter { song ->
            followedNames.any { fName -> song.singer.lowercase().contains(fName) }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(AppBackground.toArgb()))
    ) {
        item {
            HomeHeaderRow(
                navController = navController,
                selectedFilter = selectedFilter,
                isFollowingOnly = isFollowingOnly,
                onSelectFilter = { homeViewModel.setSelectedFilter(it) },
                onToggleFollowing = { homeViewModel.toggleFollowing() },
                onResetFilters = { homeViewModel.resetFilters() }
            )
        }

        if (isFollowingOnly) {
            // ── Spotify "Latest releases" Feed ──
            item {
                Text(
                    text = "Latest releases",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 12.dp)
                )
            }

            if (filteredAlbums.isNotEmpty()) {
                items(filteredAlbums.size) { i ->
                    val album = filteredAlbums[i]
                    LatestReleaseCard(
                        album = album,
                        navController = navController,
                        homeViewModel = homeViewModel,
                        playerViewModel = playerViewModel
                    )
                }
            }

            if (filteredArtists.isNotEmpty()) {
                item {
                    HomeArtists(artists = filteredArtists, navController = navController)
                }
            }
        } else {
            // ── Standard Music Feed ──
            val gridItems = feed?.sections?.firstOrNull()?.items.orEmpty().take(8)
            if (gridItems.isNotEmpty()) {
                item {
                    HomeShortcutGrid(navController, gridItems)
                }
            }
            if (filteredSongs.isNotEmpty()) {
                item {
                    Text(
                        text = "Top Songs",
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp)
                    )
                }
                val topList = filteredSongs.take(10)
                items(topList.size) { i ->
                    val song = topList[i]
                    HomeSongRow(
                        song = song,
                        onPlay = {
                            playerViewModel.updateQueue(topList)
                            playerViewModel.playSongAt(topList, i, context)
                        }
                    )
                }
            }

            if (filteredAlbums.isNotEmpty()) {
                item {
                    HomeAlbums(album = filteredAlbums, navController = navController)
                }
            }

            if (filteredArtists.isNotEmpty()) {
                item {
                    HomeArtists(artists = filteredArtists, navController = navController)
                }
            }
        }

        item { Spacer(modifier = Modifier.height(160.dp)) }
    }
}

@OptIn(ExperimentalGlideComposeApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun LatestReleaseCard(
    album: AlbumsModel,
    navController: NavController,
    homeViewModel: HomeViewModel,
    playerViewModel: PlayerViewModel
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var isSaved by remember { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var isPlayingLoading by remember { mutableStateOf(false) }

    val isSingle = album.type.equals("single", ignoreCase = true)
    val cardBg = if (album.id % 2 == 0) Color(0xFF23171B) else Color(0xFF242424)
    val formattedTime = remember(album.time) { formatReleaseDate(album.time) }

    val toggleSave = {
        isSaved = !isSaved
        if (isSaved) {
            OfflineCollectionsPref.saveCollection(
                context = context,
                id = "album:${album.name}|${album.artists}",
                name = album.name,
                coverUri = album.coverUri,
                artists = album.artists,
                isPlaylist = false,
                songs = emptyList()
            )
            SpotifySync.setAlbumSaved(context, album.id.toString(), true)
            Toast.makeText(context, "Added to Your Library", Toast.LENGTH_SHORT).show()
        } else {
            SpotifySync.setAlbumSaved(context, album.id.toString(), false)
            Toast.makeText(context, "Removed from Your Library", Toast.LENGTH_SHORT).show()
        }
    }

    val playRelease = {
        if (!isPlayingLoading) {
            isPlayingLoading = true
            scope.launch {
                try {
                    var played = false
                    homeViewModel.getAlbumSongs(album.name, album.artists).collect { res ->
                        if (!played && res is Response.Success && !res.data.isNullOrEmpty()) {
                            played = true
                            val songs = res.data
                            val song = songs[0]
                            playerViewModel.updateQueue(songs)
                            SongPlayer.playSong(song.url, context)
                            playerViewModel.updateSongState(
                                song.coverUri,
                                song.title,
                                song.singer,
                                true,
                                song.id,
                                0,
                                album.name
                            )
                        }
                    }
                    if (!played) {
                        navController.navigate(albumRoute(album.name, album.artists))
                    }
                } finally {
                    isPlayingLoading = false
                }
            }
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable {
                navController.navigate(albumRoute(album.name, album.artists))
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top Row: Artwork + Title/Artist + Options (⋮)
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                GlideImage(
                    model = album.coverUri,
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    loading = placeholder(R.drawable.placeholder),
                    failure = placeholder(R.drawable.placeholder),
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = album.artists.ifBlank { "Artist" },
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${album.name}${if (formattedTime.isNotBlank()) " • $formattedTime" else ""}",
                        color = Color(0xFFB3B3B3),
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { showBottomSheet = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = Color(0xFFB3B3B3),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Middle Summary Text
            val songText = if (isSingle) "1 song" else "12 songs"
            Text(
                text = "$songText • ${album.name}",
                color = Color(0xFFCCCCCC),
                fontSize = 13.sp,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Control Row: (+) Save and (▶) Play buttons aligned to right
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.weight(1f))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // (+) / (✓) Add to library button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { toggleSave() }
                    ) {
                        Icon(
                            imageVector = if (isSaved) Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,
                            contentDescription = if (isSaved) "In library" else "Add to library",
                            tint = if (isSaved) Color(0xFF64D36D) else Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // (▶) Play button with loading state
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable(enabled = !isPlayingLoading) { playRelease() }
                    ) {
                        if (isPlayingLoading) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play release",
                                tint = Color.Black,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            containerColor = Color(0xFF1E1E1E),
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = album.name,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = album.artists,
                    color = Color(0xFFB3B3B3),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Option 1: Go to album
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            showBottomSheet = false
                            navController.navigate(albumRoute(album.name, album.artists))
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Go to album",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(text = "Go to album", color = Color.White, fontSize = 15.sp)
                }

                // Option 2: Go to artist
                if (album.artists.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showBottomSheet = false
                                navController.navigate(artistRoute(album.artists))
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Go to artist",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(text = "Go to artist", color = Color.White, fontSize = 15.sp)
                    }
                }

                // Option 3: Save to library
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            showBottomSheet = false
                            toggleSave()
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,
                        contentDescription = "Save album",
                        tint = if (isSaved) Color(0xFF64D36D) else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = if (isSaved) "Remove from Your Library" else "Save to Your Library",
                        color = Color.White,
                        fontSize = 15.sp
                    )
                }

                // Option 4: Share
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            showBottomSheet = false
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_TEXT, "Check out ${album.name} by ${album.artists} on Spotui!")
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Release"))
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(text = "Share release", color = Color.White, fontSize = 15.sp)
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun HomeSongRow(
    song: SongsModel,
    onPlay: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF181818))
            .clickable { onPlay() }
            .padding(8.dp)
    ) {
        GlideImage(
            model = song.coverUri,
            contentDescription = song.title,
            contentScale = ContentScale.Crop,
            loading = placeholder(R.drawable.placeholder),
            failure = placeholder(R.drawable.placeholder),
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = song.singer,
                color = Color(0xFFB3B3B3),
                fontSize = 12.sp,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFF1ED760))
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = Color.Black,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun HomePodcastsFeedContent(
    navController: NavController,
    homeViewModel: HomeViewModel,
    podcastsResult: Response<com.music.spotui.data.entity.SearchResults>,
    selectedFilter: String,
    isFollowingOnly: Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val playerViewModel: PlayerViewModel = hiltViewModel()

    val searchResults = (podcastsResult as? Response.Success)?.data
    val shows = searchResults?.shows.orEmpty()
    val episodes = searchResults?.episodes.orEmpty()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(AppBackground.toArgb()))
    ) {
        item {
            HomeHeaderRow(
                navController = navController,
                selectedFilter = selectedFilter,
                isFollowingOnly = isFollowingOnly,
                onSelectFilter = { homeViewModel.setSelectedFilter(it) },
                onToggleFollowing = { homeViewModel.toggleFollowing() },
                onResetFilters = { homeViewModel.resetFilters() }
            )
        }

        item {
            Text(
                text = "Podcasts & Shows",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp)
            )
        }

        if (podcastsResult is Response.Loading && shows.isEmpty()) {
            item {
                Loader()
            }
        }

        if (shows.isNotEmpty()) {
            item {
                Text(
                    text = "Top Shows",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 8.dp)
                )
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(shows.size) { i ->
                        val show = shows[i]
                        Column(
                            modifier = Modifier
                                .width(140.dp)
                                .clickable {
                                    navController.navigate(showRoute(show.id, show.name))
                                }
                        ) {
                            GlideImage(
                                model = show.coverUri,
                                contentDescription = show.name,
                                contentScale = ContentScale.Crop,
                                loading = placeholder(R.drawable.placeholder),
                                failure = placeholder(R.drawable.placeholder),
                                modifier = Modifier
                                    .size(140.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = show.name,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Text(
                                text = show.publisher,
                                color = Color(0xFFB3B3B3),
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        if (episodes.isNotEmpty()) {
            item {
                Text(
                    text = "Recent Episodes",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp)
                )
            }

            items(episodes.size) { i ->
                val ep = episodes[i]
                HomeSongRow(
                    song = ep,
                    onPlay = {
                        playerViewModel.updateQueue(episodes)
                        playerViewModel.playSongAt(episodes, i, context)
                    }
                )
            }
        } else if (shows.isEmpty() && podcastsResult is Response.Loading) {
            item {
                Loader()
            }
        }

        item { Spacer(modifier = Modifier.height(160.dp)) }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun HomeAudiobooksFeedContent(
    navController: NavController,
    homeViewModel: HomeViewModel,
    audiobooksResult: Response<com.music.spotui.data.entity.SearchResults>,
    selectedFilter: String,
) {
    val searchResults = (audiobooksResult as? Response.Success)?.data
    val albums = searchResults?.albums.orEmpty()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(AppBackground.toArgb()))
    ) {
        item {
            HomeHeaderRow(
                navController = navController,
                selectedFilter = selectedFilter,
                isFollowingOnly = false,
                onSelectFilter = { homeViewModel.setSelectedFilter(it) },
                onToggleFollowing = { homeViewModel.toggleFollowing() },
                onResetFilters = { homeViewModel.resetFilters() }
            )
        }

        item {
            Text(
                text = "Audiobooks & Stories",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp)
            )
        }

        if (albums.isNotEmpty()) {
            item {
                Text(
                    text = "Popular Audiobooks",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 8.dp)
                )
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(albums.size) { i ->
                        val ab = albums[i]
                        Column(
                            modifier = Modifier
                                .width(140.dp)
                                .clickable {
                                    navController.navigate(albumRoute(ab.name, ab.artists))
                                }
                        ) {
                            GlideImage(
                                model = ab.coverUri,
                                contentDescription = ab.name,
                                contentScale = ContentScale.Crop,
                                loading = placeholder(R.drawable.placeholder),
                                failure = placeholder(R.drawable.placeholder),
                                modifier = Modifier
                                    .size(140.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = ab.name,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Text(
                                text = ab.artists.ifBlank { "Audiobook" },
                                color = Color(0xFFB3B3B3),
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        if (audiobooksResult is Response.Loading && albums.isEmpty()) {
            item {
                Loader()
            }
        }

        item { Spacer(modifier = Modifier.height(160.dp)) }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun HomeShortcutGrid(navController: NavController, items: List<HomeItem>) {
    Column(modifier = Modifier.padding(8.dp, 4.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp, 4.dp),
            ) {
                rowItems.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(GridBackground.toArgb()))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onHomeItemClick(navController, item) },
                    ) {
                        GlideImage(
                            modifier = Modifier.size(48.dp),
                            contentScale = ContentScale.Crop,
                            model = item.imageUrl,
                            loading = placeholder(R.drawable.placeholder),
                            failure = placeholder(R.drawable.placeholder),
                            contentDescription = "",
                        )
                        Text(
                            text = item.name,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            modifier = Modifier.padding(8.dp, 4.dp),
                        )
                    }
                }
                if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HomeFeedSection(navController: NavController, section: HomeSection) {
    Text(
        text = section.title,
        color = Color.White,
        fontSize = 21.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(16.dp, 20.dp, 16.dp, 4.dp),
    )
    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp, 0.dp)) {
        items(section.items.size) { i ->
            HomeFeedCard(section.items[i]) { onHomeItemClick(navController, section.items[i]) }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun HomeFeedCard(item: HomeItem, onClick: () -> Unit) {
    val isArtist = item is HomeItem.Artist
    val subtitle = when (item) {
        is HomeItem.Album -> item.subtitle
        is HomeItem.Playlist -> item.subtitle
        is HomeItem.Artist -> "Artist"
    }
    Column(
        horizontalAlignment = if (isArtist) Alignment.CenterHorizontally else Alignment.Start,
        modifier = Modifier
            .width(150.dp)
            .padding(6.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
    ) {
        GlideImage(
            modifier = Modifier
                .size(150.dp)
                .clip(if (isArtist) CircleShape else RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
            model = item.imageUrl,
            loading = placeholder(R.drawable.placeholder),
            failure = placeholder(R.drawable.placeholder),
            contentDescription = "",
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.name,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            textAlign = if (isArtist) TextAlign.Center else TextAlign.Start,
        )
        Text(
            text = subtitle,
            color = Color(0xFFB3B3B3),
            fontSize = 11.sp,
            maxLines = 2,
            textAlign = if (isArtist) TextAlign.Center else TextAlign.Start,
        )
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun SumUpHomeScreen(
    navController: NavController,
    albums: List<AlbumsModel>,
    artists: List<ArtistsModel>,
    homeViewModel: HomeViewModel,
    selectedFilter: String,
    isFollowingOnly: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(Color(AppBackground.toArgb()))
    ){
        HomeHeaderRow(
            navController = navController,
            selectedFilter = selectedFilter,
            isFollowingOnly = isFollowingOnly,
            onSelectFilter = { homeViewModel.setSelectedFilter(it) },
            onToggleFollowing = { homeViewModel.toggleFollowing() },
            onResetFilters = { homeViewModel.resetFilters() }
        )
        GreetingSection()

        if (albums.isNotEmpty()) {
            HomePlaylistGrid(navController, albums)
            HomeAlbums(album = albums, navController)
        }
        if (artists.isNotEmpty()) {
            HomeArtists(artists = artists, navController)
        }
        if (albums.isNotEmpty()) {
            ImageCard(navController, albums)
        }
    }
}



@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun GreetingSection(name : String = "User") {
    val currentHour = LocalTime.now().hour
    val greeting = when {
        currentHour < 12 -> "Good Morning"
        currentHour < 17 -> "Good Afternoon"
        else -> "Good Evening"
    }
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.Center) {
            Text(
                text = greeting,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
                )
            Text(
                text = "Have a Nice Day",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontSize = 13.sp
                )
        }
//        Icon(imageVector = Icons.Outlined.Person, contentDescription = "Profile", tint = Color.White)
    }
}

//@Composable
//fun ChipSection(
//    chip : List<String>
//) {
//    var selectedChip by remember {
//        mutableStateOf(0)
//    }
//    LazyRow{
//        items(chip.size){
//            Box(contentAlignment = Alignment.Center,
//                modifier = Modifier
//                    .padding(15.dp, 0.dp, 0.dp, 0.dp)
//                    .clickable {
//                        selectedChip = it
//                    }
//                    .clip(RoundedCornerShape(50.dp))
//                    .background(
//                        if (selectedChip == it) Color.Green
//                        else Color.Gray
//                    )
//                    .padding(10.dp, 5.dp)
//
//            ){
//                Text(text = chip[it], color = Color.White)
//            }
//        }
//    }
//}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun HomePlaylistGrid(navController: NavController, albums: List<AlbumsModel>) {
    // Use up to 8 albums, but don't assume there are at least 8 (rate-limited /
    // small feeds can return fewer) — that previously caused IndexOutOfBounds.
    val gridAlbums = albums.take(8)

    val chunkedAlbums = gridAlbums.chunked(2)
    Log.d("giveme", chunkedAlbums.toString())
    Column(
        modifier = Modifier
            .padding(0.dp, 10.dp)
    ){
        repeat(chunkedAlbums.size){
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .padding(15.dp, 5.dp, 7.dp, 0.dp)
                    .fillMaxWidth()
            )
            {
                repeat(chunkedAlbums[it].size){ album ->
                    Row(
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(2.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(GridBackground.toArgb()))
                            .width(180.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                val albumModel = chunkedAlbums[it][album]
                                Log.d("check", albumModel.name)
                                navController.navigate(albumRoute(albumModel.name, albumModel.artists))
                            }
                    ) {
                        GlideImage(modifier = Modifier
                            .size(55.dp),
                            contentScale = ContentScale.Crop,
                            model = chunkedAlbums[it][album].coverUri,
                            loading = placeholder(R.drawable.placeholder),
                            failure = placeholder(R.drawable.placeholder),
                            contentDescription = "Profile")
                        Text(modifier = Modifier.padding(5.dp),
                            text = chunkedAlbums[it][album].name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                    }
                }

            }
        }
    }
}


@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun HomeAlbums(
    album : List<AlbumsModel>,
    navController: NavController
) {
    val reversedAlbum = album.reversed().dropLast(1)
    Text(modifier = Modifier
        .padding(20.dp, 10.dp, 0.dp, 0.dp),
        text = "Albums",
        color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold)
        LazyRow(modifier = Modifier.padding(6.dp)){
            items(reversedAlbum.size){ album ->
                Box(modifier = Modifier
                    .padding(10.dp)
                    .width(150.dp)
                    .height(195.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        navController.navigate(albumRoute(reversedAlbum[album].name, reversedAlbum[album].artists))
                    }
            ){
                Column(
                    horizontalAlignment = Alignment.Start,
                    ) {

                    GlideImage(modifier = Modifier
                        .size(150.dp),
                        contentScale = ContentScale.Crop,
                        model = reversedAlbum[album].coverUri,
                        loading = placeholder(R.drawable.placeholder),
                        failure = placeholder(R.drawable.placeholder),
                        contentDescription = "Albums")
                    Text(
                        fontSize = 13.sp,
                        text = reversedAlbum[album].name,
                        textAlign = TextAlign.Center,
                        color = Color.White,
                        fontWeight = FontWeight.Bold)
                    Text(
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        text = reversedAlbum[album].artists,
                        color = Color.LightGray)
                }

            }
        }
    }
}

@Composable
fun HomeRecentlyPlayed(
    navController: NavController,
    albums : List<String>
) {
    Text(modifier = Modifier
        .padding(20.dp, 10.dp, 0.dp, 0.dp),
        text = "Recently Played",
        color = Color.White,
        fontSize = 23.sp,
        fontWeight = FontWeight.Bold)
    LazyRow(modifier = Modifier.padding(6.dp)){
        items(albums.size){
            Box(modifier = Modifier
                .padding(10.dp)
                .width(130.dp)
                .height(140.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    navController.navigate(Routes.Player.route)
                }
            ){
                Column(horizontalAlignment = Alignment.Start) {
                    Image(modifier = Modifier
                        .size(120.dp)
                        .background(Color.Green),
                        contentScale = ContentScale.Crop,
                        painter = painterResource(id = R.drawable.album),
                        contentDescription = "Albums")
                    Text(modifier = Modifier.padding(2.dp),
                        text = "Album name",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp)
                }

            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun HomeArtists(
    artists : List<ArtistsModel>,
    navController: NavController
) {
    Text(modifier = Modifier
        .padding(20.dp, 10.dp, 0.dp, 0.dp),
        text = "Best of Artists",
        color = Color.White,
        fontSize = 23.sp,
        fontWeight = FontWeight.Bold)
    LazyRow(modifier = Modifier.padding(6.dp)){
        items(artists.size){artist ->
            Box(modifier = Modifier
                .padding(10.dp)
                .width(150.dp)
                .height(200.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    Log.d("check", artists[artist].name)
                    navController.navigate(artistRoute(artists[artist].name, artists[artist].id))
                }
            ){
                Column(horizontalAlignment = Alignment.Start) {



                    GlideImage(modifier = Modifier
                        .size(150.dp),
                        contentScale = ContentScale.Crop,
                        model = artists[artist].coverUri,
                        loading = placeholder(R.drawable.placeholder),
                        failure = placeholder(R.drawable.placeholder),
                        contentDescription = "Albums")
                    Text(modifier = Modifier.padding(2.dp),
                        text = "This is ${artists[artist].name}",
                        color = Color.LightGray,
                        fontSize = 11.sp)
                }

            }
        }
    }
}


@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun ImageCard(
    navController: NavController,
    allAlbums: List<AlbumsModel>,
    modifier: Modifier = Modifier
) {

    val albums = allAlbums.takeLast(3)
    Text(modifier = Modifier
        .padding(20.dp, 10.dp, 0.dp, 0.dp),
        text = "Discover",
        color = Color.White,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold)
    Column(
        modifier = Modifier.padding(0.dp, 10.dp, 0.dp, 50.dp)
    ) {
        repeat(albums.size) { album ->
            Card(
                shape = RoundedCornerShape(15.dp),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 5.dp
                ),
                modifier = Modifier
                    .padding(15.dp)
                    .fillMaxWidth()
                    .height(380.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                       navController.navigate(albumRoute(albums[album].name, albums[album].artists))
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    GlideImage(
                        modifier = Modifier.fillMaxSize(),
                        model = albums[album].coverUri,
                        contentDescription = "artists",
                        loading = placeholder(R.drawable.placeholder),
                        failure = placeholder(R.drawable.placeholder),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color(AppBackground.toArgb())
                                    ),
                                    startY = 150f
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(0.dp, 0.dp, 0.dp, 30.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Text(
                            text = "Album : ${albums[album].name}",
                            style = TextStyle(color = Color.White, fontSize = 20.sp),
                            textAlign = TextAlign.Center
                        )

                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(160.dp))
    }
}

private fun formatReleaseDate(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return ""

    if (trimmed.contains("ago", ignoreCase = true) || trimmed.matches(Regex("^[A-Z][a-z]{2}\\s+\\d{1,2}$"))) {
        return trimmed
    }

    val parsedDate: java.util.Date? = runCatching {
        val format = when {
            trimmed.contains("T") -> java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            trimmed.length == 10 && trimmed.contains("-") -> java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            trimmed.length == 4 -> java.text.SimpleDateFormat("yyyy", java.util.Locale.US)
            else -> null
        }
        format?.parse(trimmed)
    }.getOrNull()

    if (parsedDate != null) {
        val diffMs = System.currentTimeMillis() - parsedDate.time
        val diffDays = (diffMs / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
        return when {
            diffDays == 0 -> "Today"
            diffDays == 1 -> "1 day ago"
            diffDays in 2..6 -> "$diffDays days ago"
            diffDays in 7..13 -> "1 week ago"
            diffDays in 14..27 -> "${diffDays / 7} weeks ago"
            else -> {
                val sdf = java.text.SimpleDateFormat("MMM d", java.util.Locale.US)
                sdf.format(parsedDate)
            }
        }
    }

    return trimmed
}





















