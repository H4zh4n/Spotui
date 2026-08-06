package com.music.spotui.ui.components

import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage

data class CanvasMedia(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
)

@OptIn(UnstableApi::class)
@Composable
fun VideoCanvasBackground(
    media: CanvasMedia,
    shouldPlay: Boolean,
    modifier: Modifier = Modifier,
    scrimAlpha: Float = 0.16f,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val textureView = remember {
        TextureView(context).apply {
            isOpaque = false
            isClickable = false
            isFocusable = false
        }
    }

    val player = remember(media.url) {
        val mediaSourceFactory = DefaultMediaSourceFactory(
            DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(media.headers)
        )

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(
                DefaultTrackSelector(context).apply {
                    parameters = buildUponParameters()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                }
            )
            .build()
            .apply {
                setAudioAttributes(AudioAttributes.DEFAULT, false)
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f
                setVideoTextureView(textureView)
                setMediaItem(MediaItem.fromUri(media.url))
                prepare()
            }
    }

    LaunchedEffect(shouldPlay) {
        if (shouldPlay) player.play() else player.pause()
    }

    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (shouldPlay) player.play()
                Lifecycle.Event.ON_PAUSE -> player.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { textureView },
            modifier = Modifier.fillMaxSize(),
        )
        if (scrimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
            )
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun AppleMusicFadedCanvasBackground(
    media: CanvasMedia,
    artworkUrl: String?,
    shouldPlay: Boolean,
    surfaceColor: Color,
    modifier: Modifier = Modifier,
) {
    val videoHeightFraction = 0.60f
    val videoFadeStartFraction = (videoHeightFraction - 0.18f).coerceAtLeast(0f)

    Box(modifier = modifier.background(surfaceColor)) {
        // 1. Blurred artwork for vibrant background glow
        if (!artworkUrl.isNullOrBlank()) {
            GlideImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(150.dp)
                    .alpha(0.85f)
            )
        }

        // 2. Video Canvas with vertical alpha gradient mask
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(videoHeightFraction)
                .graphicsLayer { alpha = 0.99f }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.0f to Color.Black,
                            videoFadeStartFraction to Color.Black,
                            1.0f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
        ) {
            VideoCanvasBackground(
                media = media,
                shouldPlay = shouldPlay,
                scrimAlpha = 0.10f,
            )
        }
    }
}
