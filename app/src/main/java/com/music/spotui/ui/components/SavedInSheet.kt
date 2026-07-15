package com.music.spotui.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.metrolist.spotify.Spotify
import com.metrolist.spotify.models.SpotifyPlaylist
import com.music.spotui.R
import com.music.spotui.data.api.SpotifySync
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.data.preferences.addLikedSongId
import com.music.spotui.data.preferences.isSongLiked
import com.music.spotui.data.preferences.removeLikedSongId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val SpotifyGreen = Color(0xFF1ED760)

/**
 * Spotify's "Saved in" sheet: Liked Songs plus every user playlist, each row
 * toggling the track in/out of it (mirrored to the real Spotify account), and a
 * "New playlist" action that creates one on Spotify with the track in it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun SavedInSheet(
    song: SongsModel,
    context: Context,
    onDismiss: () -> Unit,
    onLikedChanged: (Boolean) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var liked by remember { mutableStateOf(isSongLiked(context, song.id.toString())) }
    var playlists by remember { mutableStateOf<List<SpotifyPlaylist>?>(null) }
    // playlistId → does it contain this track (filled lazily per row).
    val membership = remember { mutableStateMapOf<String, Boolean>() }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var isCreatingPlaylist by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        playlists = withContext(Dispatchers.IO) {
            Spotify.myPlaylists().getOrNull()?.items?.filter { it.id.isNotBlank() } ?: emptyList()
        }
    }

    fun createNow() {
        val name = newName.trim().ifBlank { "My Playlist" }
        isCreatingPlaylist = true
        val currentIsPublic = isPublic
        newName = ""
        isPublic = false
        SpotifySync.createPlaylistWithTrack(context, name, song.spotifyTrackId, public = currentIsPublic) { success ->
            scope.launch {
                if (success) {
                    val updated = withContext(Dispatchers.IO) {
                        Spotify.myPlaylists().getOrNull()?.items?.filter { it.id.isNotBlank() }
                    }
                    if (updated != null) {
                        playlists = updated
                        val newPl = updated.find { it.name == name }
                        if (newPl != null && song.spotifyTrackId.isNotBlank()) {
                            membership[newPl.id] = true
                        }
                    }
                    creating = false
                } else {
                    android.widget.Toast.makeText(context, "Failed to create playlist", android.widget.Toast.LENGTH_SHORT).show()
                }
                isCreatingPlaylist = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1A1A1A),
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp, 4.dp, 20.dp, 12.dp),
            ) {
                Text("Saved in", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                /*
                Text(
                    "New playlist",
                    color = SpotifyGreen,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(
                        enabled = !isCreatingPlaylist,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { creating = true },
                )
                */
            }

            /*
            if (creating) {
                TextField(
                    enabled = !isCreatingPlaylist,
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    placeholder = { Text("Playlist name", color = Color.Gray) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { createNow() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF2A2A2A),
                        unfocusedContainerColor = Color(0xFF2A2A2A),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = SpotifyGreen,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp, 0.dp, 20.dp, 8.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp, 4.dp, 20.dp, 12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Make public",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isPublic) "Anyone can find and listen" else "Only you can view and listen",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        enabled = !isCreatingPlaylist,
                        checked = isPublic,
                        onCheckedChange = { isPublic = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = SpotifyGreen,
                            uncheckedThumbColor = Color(0xFFB3B3B3),
                            uncheckedTrackColor = Color(0xFF333333),
                            checkedBorderColor = Color.Transparent,
                            uncheckedBorderColor = Color.Transparent,
                        )
                    )
                }
                Row(modifier = Modifier.padding(20.dp, 0.dp, 20.dp, 8.dp)) {
                    Text(
                        if (isCreatingPlaylist) "Creating..." else "Create",
                        color = if (isCreatingPlaylist) Color.Gray else SpotifyGreen,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(
                            enabled = !isCreatingPlaylist,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { createNow() },
                    )
                    Spacer(Modifier.width(24.dp))
                    Text(
                        "Cancel",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        modifier = Modifier.clickable(
                            enabled = !isCreatingPlaylist,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { creating = false; newName = ""; isPublic = false },
                    )
                }
            }
            */

            // ── Liked Songs ──
            SavedInRow(
                name = "Liked Songs",
                subtitle = "",
                saved = liked,
                cover = {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF4A39EA), Color(0xFF868AE1)))
                            ),
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                },
            ) {
                liked = !liked
                if (liked) addLikedSongId(context, song.id.toString())
                else removeLikedSongId(context, song.id.toString())
                SpotifySync.setTrackSaved(context, song.spotifyTrackId, liked)
                onLikedChanged(liked)
            }

            when (val list = playlists) {
                null -> Text("Loading playlists…", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(20.dp, 12.dp))
                else -> LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(list.size) { i ->
                        val pl = list[i]
                        // Resolve membership lazily (cached per session in SpotifySync).
                        LaunchedEffect(pl.id, song.spotifyTrackId) {
                            if (membership[pl.id] == null && song.spotifyTrackId.isNotBlank()) {
                                membership[pl.id] = withContext(Dispatchers.IO) {
                                    SpotifySync.playlistTrackIds(context, pl.id).contains(song.spotifyTrackId)
                                }
                            }
                        }
                        val saved = membership[pl.id] == true
                        SavedInRow(
                            name = pl.name,
                            subtitle = pl.tracks?.total?.let { n -> "$n song" + (if (n == 1) "" else "s") } ?: "",
                            saved = saved,
                            cover = {
                                GlideImage(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    model = pl.images.firstOrNull()?.url,
                                    contentScale = ContentScale.Crop,
                                    failure = placeholder(R.drawable.placeholder),
                                    loading = placeholder(R.drawable.placeholder),
                                    contentDescription = "",
                                )
                            },
                        ) {
                            if (song.spotifyTrackId.isBlank()) return@SavedInRow
                            membership[pl.id] = !saved
                            if (saved) SpotifySync.removeTrackFromPlaylist(context, pl.id, song.spotifyTrackId)
                            else SpotifySync.addTrackToPlaylist(context, pl.id, song.spotifyTrackId)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.padding(10.dp))
        }
    }
}

@Composable
private fun SavedInRow(
    name: String,
    subtitle: String,
    saved: Boolean,
    cover: @Composable () -> Unit,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onToggle() }
            .padding(20.dp, 8.dp),
    ) {
        cover()
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp, end = 8.dp),
        ) {
            Text(name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = Color.Gray, fontSize = 13.sp, maxLines = 1)
            }
        }
        if (saved) {
            Icon(Icons.Default.CheckCircle, contentDescription = "Saved", tint = SpotifyGreen, modifier = Modifier.size(26.dp))
        } else {
            Icon(Icons.Default.Add, contentDescription = "Add", tint = Color(0xFFB3B3B3), modifier = Modifier.size(26.dp))
        }
    }
}
