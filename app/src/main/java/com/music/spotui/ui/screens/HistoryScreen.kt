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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.music.spotui.R
import com.music.spotui.data.preferences.HistoryEntry
import com.music.spotui.data.preferences.clearListeningHistory
import com.music.spotui.data.preferences.getListeningHistory
import com.music.spotui.data.preferences.removeListeningHistory
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.AppPalette
import com.music.spotui.ui.theme.GridBackground
import java.text.DateFormat
import java.util.Date

private val CardBg = Color(0xFF18181C)
private val BarTrack = Color(0xFF2A2A2A)
private val SpotifyGreen = Color(0xFF1ED760)
private val MutedText = Color(0xFFB3B3B3)

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun HistoryScreen(navController: NavController) {
    val context = LocalContext.current
    var history by remember { mutableStateOf(getListeningHistory(context)) }

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
        LazyColumn(
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
                            ) {
                                clearListeningHistory(context)
                                history = emptyList()
                            },
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
                                        SpotifyGreen.copy(alpha = 0.18f),
                                        AppPalette.copy(alpha = 0.10f),
                                    )
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column {
                            Text(
                                "Your Stats",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${history.size} plays logged",
                                color = MutedText,
                                fontSize = 13.sp,
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                            ) {
                                StatPill(
                                    label = "Artists",
                                    value = "${topArtists.size}",
                                    accent = SpotifyGreen,
                                )
                                StatPill(
                                    label = "Tracks",
                                    value = "${topTracks.size}",
                                    accent = AppPalette,
                                )
                                StatPill(
                                    label = "Plays",
                                    value = "${history.size}",
                                    accent = Color(0xFFE8622C),
                                )
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
                        )
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
                        )
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
                items(history.size) { i ->
                    val entry = history[i]
                    HistoryRow(entry) {
                        removeListeningHistory(context, entry)
                        history = getListeningHistory(context)
                    }
                }
            }
            item { Spacer(Modifier.height(140.dp)) }
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
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(CardBg)
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
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(CardBg)
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
private fun HistoryRow(entry: HistoryEntry, onRemove: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp, 6.dp),
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
