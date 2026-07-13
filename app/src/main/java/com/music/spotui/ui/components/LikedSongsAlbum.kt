package com.music.spotui.ui.components

import android.annotation.SuppressLint
import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.music.spotui.R
import com.music.spotui.data.entity.AlbumsModel
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.data.preferences.addLikedSongId
import com.music.spotui.data.preferences.getLikedSongIds
import com.music.spotui.data.preferences.getSongsByIds
import com.music.spotui.data.preferences.isSongLiked
import com.music.spotui.data.preferences.removeLikedSongId
import com.music.spotui.di.Palette
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.AppPalette
import com.music.spotui.ui.viewmodel.AlbumViewModel
import com.music.spotui.ui.viewmodel.PlayerViewModel
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material.icons.automirrored.filled.List

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun LikedSongsScreen(
    albums: List<AlbumsModel>,
    songs: List<SongsModel>,
    navController: NavController,
    context: Context
) {
    val albumViewModel : AlbumViewModel =  hiltViewModel()
    val playerViewModel : PlayerViewModel = hiltViewModel()


    val album = albums.filter { it.name == "Liked Songs" }
    val likesSongIds = getLikedSongIds(context)
    var likedSongs  by remember { mutableStateOf(emptyList<SongsModel>()) }

    var dominentColor by remember {
        mutableStateOf(Color(AppBackground.toArgb()))
    }
    Palette().extractSecondColorFromCoverUrl(context = context, album[0].coverUri){ color ->
        dominentColor = color
    }
    val likeState = albumViewModel.likeState.value
    LaunchedEffect(likeState) {
        likedSongs = getSongsByIds(likesSongIds, songs).sortedBy { it.title }
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
                        ) {
                            navController.navigateUp()
                        },
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "",
                        tint = Color.White)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                ),
                title = {
                    Text(text = "")
                }
            )
        }
    ){


        Column(modifier = Modifier
            .fillMaxSize()
            .background(Color(AppBackground.toArgb()))
            .verticalScroll(rememberScrollState())
        ) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(dominentColor, Color(AppBackground.toArgb())),
                            startY = -100f,

                            ),

                        )
                ,
                verticalArrangement = Arrangement.Center,
                // horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.padding(25.dp))

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    GlideImage(
                        modifier = Modifier.size(180.dp),
                        model = album[0].coverUri,
                        failure = placeholder(R.drawable.placeholder),
                        //loading = placeholder(R.drawable.album),
                        //contentScale = ContentScale.Crop,
                        contentDescription = "",
                    )
                }
                Spacer(modifier = Modifier.padding(20.dp))



                Row(horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(25.dp, 0.dp)
                ){

                    Column(
                        modifier = Modifier
                            .width(200.dp)
                    ) {
                        Text(modifier = Modifier,
                            text = "Liked Songs",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold)
                        Text(modifier = Modifier,
                            text = " ${likedSongs.size} songs",
                            color = Color.Gray,
                            letterSpacing = 0.sp,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium)
                    }


                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (likedSongs.isNotEmpty()) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_queue_add),
                                tint = Color.White,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) {
                                        playerViewModel.addAllToQueue(likedSongs)
                                        android.widget.Toast.makeText(
                                            context,
                                            "${likedSongs.size} track(s) added to queue",
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                                contentDescription = "Add to queue",
                            )
                        }

                        if (!albumViewModel.currentSongPlayingState.value && likedSongs.isNotEmpty()) {
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
                                        albumViewModel.updateQueue(likedSongs)
                                        SongPlayer.playSong(likedSongs[0].url, context)
                                        albumViewModel.updateSongState(
                                            likedSongs[0].coverUri,
                                            likedSongs[0].title,
                                            likedSongs[0].singer,
                                            true,
                                            likedSongs[0].id,
                                            0,
                                            "Liked Songs"
                                        )
                                    }
                            ) {
                                Icon(
                                    modifier = Modifier.size(25.dp),
                                    tint = Color.Black,
                                    painter = painterResource(id = R.drawable.play_svgrepo_com),
                                    contentDescription = ""
                                )
                            }
                        }
                    }


                }

            }

//            Spacer(modifier = Modifier.padding(25.dp))

            if(likedSongs.isNotEmpty()){
                repeat(likedSongs.size) {song ->

                    var isLiked by remember {
                        mutableStateOf(isSongLiked(context, likedSongs[song].id.toString()))
                    }
                    var showSavedIn by remember { mutableStateOf(false) }
                    if (showSavedIn) {
                        SavedInSheet(
                            song = likedSongs[song],
                            context = context,
                            onDismiss = { showSavedIn = false },
                            onLikedChanged = { isLiked = it },
                        )
                    }
                    val songId = likedSongs[song].id
                    val currentPlayingIndicatorColor = if(songId == albumViewModel.currentSongId.value) Color(
                        AppPalette.toArgb()) else Color.White

                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.StartToEnd) {
                                playerViewModel.addToQueue(likedSongs[song])
                                android.widget.Toast.makeText(
                                    context,
                                    "${likedSongs[song].title} added to queue",
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AppBackground)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    albumViewModel.updateQueue(likedSongs)
                                    SongPlayer.playSong(likedSongs[song].url, context)
                                    albumViewModel.updateSongState(
                                        likedSongs[song].coverUri,
                                        likedSongs[song].title,
                                        likedSongs[song].singer,
                                        true,
                                        likedSongs[song].id,
                                        song,
                                        "Liked Songs"
                                    )
                                }
                                .padding(20.dp, 8.dp)
                        ) {

                            Row(
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.width(280.dp)
                            ) {
                                GlideImage(
                                    modifier = Modifier
                                        .padding(0.dp, 0.dp, 10.dp, 0.dp)
                                        .size(50.dp),
                                    model = likedSongs[song].coverUri,
                                    contentScale = ContentScale.Crop,
                                    failure = placeholder(R.drawable.placeholder),
                                    loading = placeholder(R.drawable.placeholder),
                                    contentDescription = ""
                                )
                                Column {
                                    Text(
                                        text = likedSongs[song].title,
                                        color = currentPlayingIndicatorColor,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = likedSongs[song].singer,
                                        color = Color.Gray,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Icon(
                                modifier = Modifier
                                    .size(20.dp)
                                    .combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            if (isLiked) {
                                                removeLikedSongId(context, songId.toString())
                                            } else {
                                                addLikedSongId(context, songId.toString())
                                            }
                                            //isLiked = isSongLiked(context, songId.toString())
                                            albumViewModel.updateLikeState(!albumViewModel.likeState.value)
                                        },
                                        onLongClick = { showSavedIn = true },
                                    ),
                                painter = if (isLiked){
                                    painterResource(id = R.drawable.added)
                                }
                                else{
                                    painterResource(id = R.drawable.ic_add)
                                }
                                ,
                                tint = Color.LightGray,
                                contentDescription = ""
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.padding(80.dp))
        }

    }

}