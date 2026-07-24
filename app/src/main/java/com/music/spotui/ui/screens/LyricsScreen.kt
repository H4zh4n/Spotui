package com.music.spotui.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.music.spotui.data.api.TranslationApi
import com.music.spotui.data.entity.Lyrics
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.viewmodel.LyricsViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Polls ExoPlayer's position every 250ms so the active lyric line tracks the music. */
@Composable
private fun rememberPlaybackPositionMs(): State<Long> {
    val pos = remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            pos.value = SongPlayer.getCurrentPosition().coerceAtLeast(0L)
            delay(250L)
        }
    }
    return pos
}

private fun activeIndexFor(lyrics: Lyrics, positionMs: Long): Int =
    if (lyrics.synced) lyrics.lines.indexOfLast { it.timeMs <= positionMs + 250 }.coerceAtLeast(0)
    else -1

/** Seek to a tapped synced line and make sure we're playing. */
private fun jumpTo(timeMs: Long) {
    SongPlayer.seekTo(timeMs)
    if (!SongPlayer.isPlaying()) SongPlayer.play()
}

/**
 * Full-screen synced-lyrics overlay (Spotify "Lyrics" view). The current line is
 * highlighted bright and the list auto-scrolls to keep it centered; tapping a line
 * jumps playback to it (synced lyrics only). Falls back to a static scroll for plain
 * (un-timed) lyrics.
 */
@Composable
fun LyricsScreen(
    title: String,
    artist: String,
    album: String,
    accentColor: Color,
    onClose: () -> Unit,
) {
    val vm: LyricsViewModel = hiltViewModel()
    val density = LocalDensity.current
    val screenHeight = with(density) {
        val dpHeight = LocalConfiguration.current.screenHeightDp.dp
        if (dpHeight.value > 0f) dpHeight.toPx() else 2000f
    }
    val coroutineScope = rememberCoroutineScope()

    var offsetY by remember { mutableFloatStateOf(0f) }
    val animatable = remember { Animatable(0f) }
    var animationJob by remember { mutableStateOf<Job?>(null) }
    var hasAppeared by remember { mutableStateOf(false) }

    suspend fun slideTo(targetValue: Float, velocity: Float = 0f) {
        try {
            animatable.snapTo(offsetY)
            animatable.animateTo(
                targetValue = targetValue,
                initialVelocity = velocity,
                animationSpec = if (targetValue == 0f || targetValue == screenHeight)
                    tween(300) else spring()
            ) { offsetY = this.value }
        } catch (_: CancellationException) { }
    }

    fun cancelRunningAnimation() {
        animationJob?.cancel()
        animationJob = null
    }

    fun launchAnimation(targetValue: Float, velocity: Float = 0f) {
        cancelRunningAnimation()
        animationJob = coroutineScope.launch { slideTo(targetValue, velocity) }
    }

    val dismissLyrics = {
        launchAnimation(screenHeight)
    }

    // Entrance animation: slide up from the bottom
    LaunchedEffect(Unit) {
        offsetY = screenHeight
        slideTo(0f)
        hasAppeared = true
    }

    // Safety net: when fully dismissed, remove the overlay
    LaunchedEffect(offsetY) {
        if (hasAppeared && offsetY >= screenHeight - 1f) {
            onClose()
        }
    }

    // Hardware back press dismisses with animation
    BackHandler(onBack = dismissLyrics)

    // Swipe-down-to-dismiss nested scroll (same pattern as PlayerScreen)
    val nestedScrollConnection = remember(screenHeight) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (source == NestedScrollSource.Drag) {
                    cancelRunningAnimation()
                }
                if (offsetY > 0f && delta < 0f) {
                    val newOffset = (offsetY + delta).coerceIn(0f, screenHeight)
                    offsetY = newOffset
                    return Offset(0f, delta)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val delta = available.y
                if (source == NestedScrollSource.Drag) {
                    cancelRunningAnimation()
                }
                if (delta > 0f && (source == NestedScrollSource.Drag || offsetY > 0f)) {
                    offsetY = (offsetY + delta).coerceIn(0f, screenHeight)
                    return Offset(0f, delta)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offsetY > 0f) {
                    val targetValue = when {
                        available.y < -500f -> 0f
                        available.y > 500f -> screenHeight
                        else -> if (offsetY > screenHeight * 0.25f) screenHeight else 0f
                    }
                    val job = coroutineScope.launch {
                        slideTo(targetValue, available.y)
                    }
                    animationJob = job
                    try { job.join() } catch (_: Exception) { }
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (offsetY > 0f) {
                    val targetValue = if (offsetY > screenHeight * 0.25f) screenHeight else 0f
                    val job = coroutineScope.launch {
                        slideTo(targetValue, available.y)
                    }
                    animationJob = job
                    try { job.join() } catch (_: Exception) { }
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(title, artist) {
        if (vm.state.value is LyricsViewModel.State.Loaded) return@LaunchedEffect
        val durationSec = (SongPlayer.getDuration() / 1000).toInt()
        vm.load(title, artist, album, durationSec)
    }
    val state by vm.state.collectAsState()
    val positionMs by rememberPlaybackPositionMs()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val anyPressed = event.changes.any { it.pressed }
                        if (anyPressed) {
                            cancelRunningAnimation()
                        }
                    }
                }
            }
            .graphicsLayer {
                translationY = offsetY
                alpha = (1f - (offsetY / screenHeight)).coerceIn(0f, 1f)
            }
            .background(Color(0xFF121212))
            .background(
                Brush.verticalGradient(
                    colors = listOf(accentColor, accentColor.copy(alpha = 0.55f), Color.Black),
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp, 8.dp)
            ) {
                Column(modifier = Modifier.padding(end = 12.dp)) {
                    Text("Lyrics", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier
                            .size(30.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { dismissLyrics() }
                    )
                }
            }

            when (val s = state) {
                is LyricsViewModel.State.Loading ->
                    Box(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                is LyricsViewModel.State.NotFound ->
                    Box(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Couldn't find lyrics for this track", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
                    }
                is LyricsViewModel.State.Loaded -> {
                    val lyrics = s.lyrics
                    val activeIndex = activeIndexFor(lyrics, positionMs)
                    val listState = rememberLazyListState()
                    val translated = vm.translatedLines
                    val showTranslations = vm.translationsVisible
                    LaunchedEffect(activeIndex) {
                        if (activeIndex >= 0) {
                            listState.animateScrollToItem(activeIndex.coerceAtLeast(0), scrollOffset = -260)
                        }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(24.dp, 16.dp, 24.dp, 120.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        itemsIndexed(lyrics.lines) { index, line ->
                            LyricLineText(
                                text = line.text,
                                isActive = index == activeIndex,
                                synced = lyrics.synced,
                                fontSize = 24.sp,
                                onTap = if (lyrics.synced) ({ jumpTo(line.timeMs) }) else null,
                                translatedText = if (showTranslations && translated != null && index < translated.size) translated[index].translatedText else null,
                            )
                        }
                    }
                }
            }
        }

        TranslateFloatingPanel(vm = vm, modifier = Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun TranslateFloatingPanel(vm: LyricsViewModel, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(start = 16.dp, bottom = 24.dp)) {
        if (vm.showLanguageBar) {
            Column(
                modifier = Modifier
                    .background(Color(0xCC1E1E1E), shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                    .padding(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(28.dp)
                            .background(Color.White, shape = androidx.compose.foundation.shape.CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { vm.dismissTranslate() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ClearAll,
                            contentDescription = "Clear translation",
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    TranslationBar(vm = vm)
                }
                vm.translationError?.let { error ->
                    Text(
                        text = error,
                        color = Color(0xFFFF6B6B),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, top = 4.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.size(8.dp))
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color(0xCC1E1E1E), shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { vm.toggleLanguageBar() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Translate,
                contentDescription = "Translate",
                tint = if (vm.showLanguageBar) Color(0xFF1DB954) else Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** How many lines the inline lyrics card previews before "Show lyrics". */
private const val PREVIEW_LINE_COUNT = 5

/**
 * Inline lyrics card shown in the Now Playing scroll (no full-screen chrome).
 * Spotify-style *preview*: only a handful of lines (following the active synced
 * line) plus a "Show lyrics" button that opens the full-screen lyrics view.
 */
@Composable
fun InlineLyrics(
    title: String,
    artist: String,
    album: String,
    accentColor: Color,
    onExpand: () -> Unit,
) {
    val vm: LyricsViewModel = hiltViewModel()
    LaunchedEffect(title, artist) {
        val durationSec = (SongPlayer.getDuration() / 1000).toInt()
        vm.load(title, artist, album, durationSec)
    }
    val state by vm.state.collectAsState()
    val positionMs by rememberPlaybackPositionMs()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp, 8.dp, 16.dp, 40.dp)
            .shadow(
                elevation = 8.dp,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                clip = false
            )
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.45f),
                        accentColor.copy(alpha = 0.35f)
                    )
                )
            )
            .background(Color.Black.copy(alpha = 0.45f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.25f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onExpand() }
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Lyrics preview", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(12.dp))

        when (val s = state) {
            is LyricsViewModel.State.Loading ->
                Text("Loading lyrics…", color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp)
            is LyricsViewModel.State.NotFound ->
                Text("No lyrics found for this track", color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp)
            is LyricsViewModel.State.Loaded -> {
                val lyrics = s.lyrics
                val activeIndex = activeIndexFor(lyrics, positionMs)
                // Preview window: keep the active synced line in view; plain
                // lyrics just show the first few lines.
                val windowStart =
                    if (lyrics.synced) activeIndex.coerceIn(0, (lyrics.lines.size - PREVIEW_LINE_COUNT).coerceAtLeast(0))
                    else 0
                val translated = vm.translatedLines
                val showTranslations = vm.translationsVisible
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    lyrics.lines.drop(windowStart).take(PREVIEW_LINE_COUNT).forEachIndexed { i, line ->
                        val globalIndex = windowStart + i
                        LyricLineText(
                            text = line.text,
                            isActive = globalIndex == activeIndex,
                            synced = lyrics.synced,
                            fontSize = 22.sp,
                            onTap = if (lyrics.synced) ({ jumpTo(line.timeMs) }) else null,
                            translatedText = if (showTranslations && translated != null && globalIndex < translated.size) translated[globalIndex].translatedText else null,
                        )
                    }
                }
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(18.dp))
                Box(
                    modifier = Modifier
                        .background(Color.White, shape = androidx.compose.foundation.shape.RoundedCornerShape(50))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onExpand() }
                        .padding(16.dp, 8.dp)
                ) {
                    Text("Show lyrics", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Language-selection bar:  [Source ▾]  →  [Target ▾]  [Translate]
 * Each side opens a dropdown menu; the Translate button triggers the fetch.
 */
@Composable
private fun TranslationBar(vm: LyricsViewModel) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp)
    ) {
        LangChip(
            code = vm.sourceLanguage,
            label = TranslationApi.displayName(vm.sourceLanguage),
            onClick = { vm.chooseSourceLanguage(it) },
            languages = vm.availableLanguages,
        )
        Text(
            text = "  \u2192  ",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { vm.swapLanguages() }
                .padding(horizontal = 4.dp, vertical = 4.dp),
        )
        LangChip(
            code = vm.targetLanguage,
            label = TranslationApi.displayName(vm.targetLanguage),
            onClick = { vm.chooseTargetLanguage(it) },
            languages = vm.availableLanguages,
        )

        when {
            vm.translationsLoading && vm.modelState is LyricsViewModel.ModelState.Downloading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .background(Color.White.copy(alpha = 0.15f), shape = androidx.compose.foundation.shape.RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "  Downloading\u2026",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
            vm.translationsLoading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .background(Color.White.copy(alpha = 0.15f), shape = androidx.compose.foundation.shape.RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "  Translating\u2026",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
            else -> {
                Box(
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .background(Color.White, shape = androidx.compose.foundation.shape.RoundedCornerShape(50))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { vm.startTranslation() }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Translate", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

/** A single tappable language chip that opens a [LanguagePickerDialog]. */
@Composable
private fun LangChip(
    code: String,
    label: String,
    onClick: (String) -> Unit,
    languages: List<Pair<String, String>>,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.12f), shape = androidx.compose.foundation.shape.RoundedCornerShape(50))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { expanded = true }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
        }
        if (expanded) {
            LanguagePickerBottomSheet(
                currentCode = code,
                languages = languages,
                onDismiss = { expanded = false },
                onSelect = { selectedCode ->
                    onClick(selectedCode)
                    expanded = false
                }
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePickerBottomSheet(
    currentCode: String,
    languages: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val filteredLanguages = remember(searchQuery, languages) {
        if (searchQuery.isBlank()) {
            languages
        } else {
            languages.filter { (code, name) ->
                name.contains(searchQuery, ignoreCase = true) ||
                code.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    LaunchedEffect(Unit) {
        val index = languages.indexOfFirst { it.first == currentCode }
        if (index >= 0) {
            // Scroll to the item and apply a negative offset (~150.dp) to center it in the bottom sheet viewport.
            val offsetPx = with(density) { -150.dp.roundToPx() }
            listState.scrollToItem(index, offsetPx)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E1E1E), // Sleek dark gray
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .heightIn(max = 450.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select Language",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            // Search Box
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search languages...", color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search icon",
                        tint = Color.White.copy(alpha = 0.5f)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color.White.copy(alpha = 0.05f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                    focusedBorderColor = Color(0xFF1DB954), // Spotify Green
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    cursorColor = Color(0xFF1DB954)
                ),
                singleLine = true,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )

            // Languages List
            if (filteredLanguages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No languages found",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    itemsIndexed(filteredLanguages) { index, (code, name) ->
                        val isSelected = code == currentCode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(code)
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = name,
                                color = if (isSelected) Color(0xFF1DB954) else Color.White,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color(0xFF1DB954),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        if (index < filteredLanguages.lastIndex) {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricLineText(
    text: String,
    isActive: Boolean,
    synced: Boolean,
    fontSize: androidx.compose.ui.unit.TextUnit,
    onTap: (() -> Unit)?,
    translatedText: String? = null,
) {
    if (text.isBlank()) {
        Box(modifier = Modifier.size(1.dp))
        return
    }
    val target = when {
        !synced -> Color.White.copy(alpha = 0.95f)
        isActive -> Color.White
        else -> Color.White.copy(alpha = 0.60f)
    }
    val color by animateColorAsState(targetValue = target, label = "lyricColor")
    val clickModifier = if (onTap != null) Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
    ) { onTap() } else Modifier

    if (translatedText != null) {
        Column(modifier = clickModifier) {
            Text(
                text = text,
                color = color,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = translatedText,
                color = color.copy(alpha = 0.6f),
                fontSize = (fontSize.value * 0.7f).sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    } else {
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            modifier = clickModifier,
        )
    }
}
