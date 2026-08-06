package com.music.spotui.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val SpotifyGreen = Color(0xFF1DB954)

// Fixed thumb height — never changes during scroll
private val THUMB_HEIGHT = 48.dp

/**
 * Spotify-style scrollbar for LazyColumn.
 */
@Composable
fun FastScrollbarForLazyList(
    state: LazyListState,
    modifier: Modifier = Modifier,
    activeColor: Color = SpotifyGreen,
    showBadge: Boolean = true,
) {
    val totalItems = state.layoutInfo.totalItemsCount
    if (totalItems <= 3) return

    val coroutineScope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val alpha = remember { Animatable(0f) }

    val isScrolling = state.isScrollInProgress
    LaunchedEffect(isScrolling, isDragging) {
        if (isScrolling || isDragging) {
            alpha.animateTo(1f, animationSpec = tween(150))
        } else {
            delay(1500)
            alpha.animateTo(0f, animationSpec = tween(500))
        }
    }

    // Scroll progress from actual list state (used when NOT dragging)
    val scrollProgress by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val visible = info.visibleItemsInfo
            val total = info.totalItemsCount
            if (total <= 1 || visible.isEmpty()) {
                0f
            } else {
                val firstIndex = state.firstVisibleItemIndex
                val firstOffset = state.firstVisibleItemScrollOffset.toFloat()
                val firstItemSize = visible.first().size.toFloat().coerceAtLeast(1f)
                val exactIndex = firstIndex + (firstOffset / firstItemSize)
                (exactIndex / (total - 1).toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(32.dp)
    ) {
        val density = LocalDensity.current
        val containerHeightPx = with(density) { maxHeight.toPx() }
        val thumbHeightPx = with(density) { THUMB_HEIGHT.toPx() }
        val scrollableHeightPx = (containerHeightPx - thumbHeightPx).coerceAtLeast(1f)

        // Thumb position: locked to finger during drag, follows scroll state otherwise
        val thumbOffsetYPx = if (isDragging) {
            (dragFraction * scrollableHeightPx).coerceIn(0f, scrollableHeightPx)
        } else {
            (scrollProgress * scrollableHeightPx).coerceIn(0f, scrollableHeightPx)
        }

        if (alpha.value > 0f || isDragging) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(alpha.value)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val downEvent = awaitPointerEvent(PointerEventPass.Initial)
                                val down = downEvent.changes.firstOrNull { it.pressed }
                                    ?: continue
                                down.consume()
                                isDragging = true

                                // Map touch Y to 0..1 fraction and scroll
                                val f0 = (down.position.y / containerHeightPx).coerceIn(0f, 1f)
                                dragFraction = f0
                                val idx0 = (f0 * (totalItems - 1)).roundToInt()
                                    .coerceIn(0, totalItems - 1)
                                coroutineScope.launch { state.scrollToItem(idx0) }

                                var pointerId = down.id
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == pointerId }
                                        ?: event.changes.firstOrNull()

                                    if (change == null || !change.pressed) {
                                        isDragging = false
                                        break
                                    }

                                    change.consume()
                                    pointerId = change.id

                                    val f = (change.position.y / containerHeightPx).coerceIn(0f, 1f)
                                    dragFraction = f
                                    val targetIndex = (f * (totalItems - 1)).roundToInt()
                                        .coerceIn(0, totalItems - 1)
                                    coroutineScope.launch { state.scrollToItem(targetIndex) }
                                }
                            }
                        }
                    }
            ) {
                // Floating indicator badge when dragging
                if (showBadge && isDragging) {
                    val currentTrackIndex = (state.firstVisibleItemIndex + 1).coerceAtMost(totalItems)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF282828),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset {
                                IntOffset(
                                    x = with(density) { (-40).dp.roundToPx() },
                                    y = (thumbOffsetYPx + thumbHeightPx / 2f - with(density) { 16.dp.toPx() }).roundToInt()
                                )
                            }
                            .wrapContentSize(unbounded = true)
                    ) {
                        Text(
                            text = "$currentTrackIndex / $totalItems",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                // Scrollbar Thumb — fixed height, never stretches or shrinks
                val thumbWidth = if (isDragging) 6.dp else 4.dp
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 3.dp)
                        .offset { IntOffset(0, thumbOffsetYPx.roundToInt()) }
                        .width(thumbWidth)
                        .height(THUMB_HEIGHT)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isDragging) activeColor else activeColor.copy(alpha = 0.8f))
                )
            }
        }
    }
}

/**
 * Spotify-style scrollbar for Column with verticalScroll (ScrollState).
 */
@Composable
fun FastScrollbarForScrollState(
    state: ScrollState,
    modifier: Modifier = Modifier,
    activeColor: Color = SpotifyGreen,
    showBadge: Boolean = true,
) {
    if (state.maxValue <= 0) return

    val coroutineScope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val alpha = remember { Animatable(0f) }

    val isScrolling = state.isScrollInProgress
    LaunchedEffect(isScrolling, isDragging) {
        if (isScrolling || isDragging) {
            alpha.animateTo(1f, animationSpec = tween(150))
        } else {
            delay(1500)
            alpha.animateTo(0f, animationSpec = tween(500))
        }
    }

    val scrollProgress by remember {
        derivedStateOf {
            if (state.maxValue == 0) 0f else (state.value.toFloat() / state.maxValue).coerceIn(0f, 1f)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(32.dp)
    ) {
        val density = LocalDensity.current
        val containerHeightPx = with(density) { maxHeight.toPx() }
        val thumbHeightPx = with(density) { THUMB_HEIGHT.toPx() }
        val scrollableHeightPx = (containerHeightPx - thumbHeightPx).coerceAtLeast(1f)

        val thumbOffsetYPx = if (isDragging) {
            (dragFraction * scrollableHeightPx).coerceIn(0f, scrollableHeightPx)
        } else {
            (scrollProgress * scrollableHeightPx).coerceIn(0f, scrollableHeightPx)
        }

        if (alpha.value > 0f || isDragging) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(alpha.value)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val downEvent = awaitPointerEvent(PointerEventPass.Initial)
                                val down = downEvent.changes.firstOrNull { it.pressed }
                                    ?: continue
                                down.consume()
                                isDragging = true

                                val f0 = (down.position.y / containerHeightPx).coerceIn(0f, 1f)
                                dragFraction = f0
                                val scroll0 = (f0 * state.maxValue).roundToInt()
                                    .coerceIn(0, state.maxValue)
                                coroutineScope.launch { state.scrollTo(scroll0) }

                                var pointerId = down.id
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == pointerId }
                                        ?: event.changes.firstOrNull()

                                    if (change == null || !change.pressed) {
                                        isDragging = false
                                        break
                                    }

                                    change.consume()
                                    pointerId = change.id

                                    val f = (change.position.y / containerHeightPx).coerceIn(0f, 1f)
                                    dragFraction = f
                                    val targetScroll = (f * state.maxValue).roundToInt()
                                        .coerceIn(0, state.maxValue)
                                    coroutineScope.launch { state.scrollTo(targetScroll) }
                                }
                            }
                        }
                    }
            ) {
                // Floating percentage badge when dragging
                if (showBadge && isDragging) {
                    val percent = (scrollProgress * 100).roundToInt()
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF282828),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset {
                                IntOffset(
                                    x = with(density) { (-40).dp.roundToPx() },
                                    y = (thumbOffsetYPx + thumbHeightPx / 2f - with(density) { 16.dp.toPx() }).roundToInt()
                                )
                            }
                            .wrapContentSize(unbounded = true)
                    ) {
                        Text(
                            text = "$percent%",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                // Scrollbar Thumb — fixed height, never stretches or shrinks
                val thumbWidth = if (isDragging) 6.dp else 4.dp
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 3.dp)
                        .offset { IntOffset(0, thumbOffsetYPx.roundToInt()) }
                        .width(thumbWidth)
                        .height(THUMB_HEIGHT)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isDragging) activeColor else activeColor.copy(alpha = 0.8f))
                )
            }
        }
    }
}
