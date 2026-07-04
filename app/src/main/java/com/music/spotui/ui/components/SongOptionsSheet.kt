package com.music.spotui.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.music.spotui.R
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.data.preferences.addLikedSongId
import com.music.spotui.data.preferences.isSongLiked
import com.music.spotui.data.preferences.removeLikedSongId
import com.music.spotui.ui.navigation.Routes
import com.music.spotui.ui.navigation.albumRoute
import com.music.spotui.ui.navigation.artistRoute
import com.music.spotui.ui.theme.AppPalette

/**
 * Long-press context menu for a single track. Mirrors Spotify's "3-dot" sheet:
 * play next, add to queue, like/unlike, jump to artist/album, share.
 *
 * Queue operations go through [PlayerViewModel], which mutates the shared
 * (singleton) playback queue. The like toggle persists to local prefs.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun SongOptionsSheet(
    song: SongsModel,
    navController: NavController,
    context: Context,
    onDismiss: () -> Unit,
) {
    val playerViewModel: com.music.spotui.ui.viewmodel.PlayerViewModel =
        androidx.hilt.navigation.compose.hiltViewModel()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var liked by remember { mutableStateOf(isSongLiked(context, song.id.toString())) }
    var downloaded by remember { mutableStateOf(com.music.spotui.data.preferences.isDownloaded(context, song.id.toString())) }
    var downloadingNow by remember { mutableStateOf(com.music.spotui.di.SongPlayer.isDownloading(song.url)) }
    var downloadPct by remember { mutableStateOf(com.music.spotui.di.SongPlayer.downloadProgress(song.url)) }
    // Poll progress while a download is active so the menu row shows live "Downloading… NN%".
    androidx.compose.runtime.LaunchedEffect(downloadingNow) {
        while (downloadingNow) {
            downloadPct = com.music.spotui.di.SongPlayer.downloadProgress(song.url)
            kotlinx.coroutines.delay(300)
        }
    }

    // Spotify-style "Saved in" sheet (Liked Songs + playlists, add/remove/create).
    var showSavedIn by remember { mutableStateOf(false) }
    if (showSavedIn) {
        SavedInSheet(
            song = song,
            context = context,
            onDismiss = { showSavedIn = false; onDismiss() },
            onLikedChanged = { liked = it },
        )
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1A1A1A),
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp, 4.dp, 16.dp, 12.dp)
            ) {
                GlideImage(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    model = song.coverUri,
                    contentScale = ContentScale.Crop,
                    contentDescription = ""
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(song.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.singer, color = Color.Gray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            HorizontalDivider(color = Color(0xFF2A2A2A))

            SongMenuRow(Icons.Default.PlayArrow, "Play next") {
                playerViewModel.playNext(song)
                onDismiss()
            }
            SongMenuRow(Icons.AutoMirrored.Filled.List, "Add to queue") {
                playerViewModel.addToQueue(song)
                onDismiss()
            }
            SongMenuRow(
                Icons.Default.Add, "Add to playlist",
                enabled = song.spotifyTrackId.isNotBlank(), trailingArrow = true,
            ) {
                showSavedIn = true
            }
            SongMenuRow(
                icon = if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                label = if (liked) "Remove from Liked Songs" else "Add to Liked Songs",
                iconTint = if (liked) Color(AppPalette.toArgb()) else Color.White,
            ) {
                if (liked) removeLikedSongId(context, song.id.toString())
                else addLikedSongId(context, song.id.toString())
                liked = !liked
                // Mirror the like to the real Spotify account.
                com.music.spotui.data.api.SpotifySync.setTrackSaved(context, song.spotifyTrackId, liked)
            }
            SongMenuRow(
                icon = if (downloaded) Icons.Default.CheckCircle else ImageVector.vectorResource(R.drawable.ic_download),
                label = when {
                    downloaded -> "Downloaded — remove"
                    downloadingNow -> if (downloadPct in 1..99) "Downloading… $downloadPct%" else "Downloading…"
                    else -> "Download"
                },
                iconTint = if (downloaded) Color(AppPalette.toArgb()) else Color.White,
                enabled = !downloadingNow,
            ) {
                if (downloaded) {
                    com.music.spotui.data.preferences.removeDownload(context, song.id.toString())
                    downloaded = false
                } else {
                    downloadingNow = true
                    com.music.spotui.di.SongPlayer.downloadSong(song, context) { ok ->
                        downloadingNow = false
                        downloaded = ok
                    }
                }
            }
            val artist = song.singer.substringBefore(",").trim()
            SongMenuRow(Icons.Default.Person, "Go to artist", enabled = artist.isNotBlank(), trailingArrow = true) {
                // Resolve the exact artist (id) from the track so the name can't
                // fuzzy-match to a different artist.
                playerViewModel.goToArtist(song.spotifyTrackId, song.singer) { route ->
                    navController.navigate(route)
                }
                onDismiss()
            }
            SongMenuRow(Icons.Default.Add, "Go to album", enabled = song.album.isNotBlank(), trailingArrow = true) {
                navController.navigate(albumRoute(song.album, song.singer))
                onDismiss()
            }
            SongMenuRow(Icons.Default.Share, "Share") {
                // Share the real Spotify track link when we know the id.
                val shareText = song.spotifyTrackId.takeIf { it.isNotBlank() }
                    ?.let { "https://open.spotify.com/track/$it" }
                    ?: "Listening to ${song.title} by ${song.singer}"
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                context.startActivity(Intent.createChooser(send, "Share"))
                onDismiss()
            }
            Spacer(modifier = Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun SongMenuRow(
    icon: ImageVector,
    label: String,
    iconTint: Color = Color.White,
    enabled: Boolean = true,
    trailingArrow: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(16.dp, 14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) iconTint else Color.Gray.copy(alpha = 0.4f),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(18.dp))
        Text(
            text = label,
            color = if (enabled) Color.White else Color.Gray.copy(alpha = 0.4f),
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        if (trailingArrow) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
