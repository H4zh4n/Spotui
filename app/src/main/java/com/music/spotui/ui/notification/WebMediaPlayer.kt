package com.music.spotui.ui.notification

import android.net.Uri
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.music.spotui.di.CurrentSongState
import com.music.spotui.di.SongPlayer
import com.music.spotui.di.SpotifyWebPlayer

/**
 * A [SimpleBasePlayer] that mirrors the hidden Spotify web player's state
 * (metadata from [CurrentSongState], position/duration/playing from
 * [SpotifyWebPlayer]) so the system media notification / lock screen shows the
 * track while audio streams from the WebView. Transport controls route back to
 * the web player. Swapped into the MediaSession only while web playback is active.
 */
class WebMediaPlayer(
    looper: Looper,
    private val currentSongState: CurrentSongState,
    private val onAdvance: (Boolean) -> Unit,
) : SimpleBasePlayer(looper) {

    override fun getState(): State {
        val title = currentSongState.title.value
        val artist = currentSongState.singer.value
        val cover = currentSongState.coverUri.value
        val id = currentSongState.songId.value.toString()

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
        com.music.spotui.util.ArtworkHelper.attachArtwork(
            metadataBuilder,
            com.music.spotui.MyApplication.instance,
            cover,
            id,
            currentSongState.songUrl.value
        )
        val metadata = metadataBuilder.build()
        val mediaItem = MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metadata)
            .build()

        val durMs = SpotifyWebPlayer.durationMs
        val posMs = SpotifyWebPlayer.positionMs.coerceAtLeast(0)
        val itemData = MediaItemData.Builder(id)
            .setMediaItem(mediaItem)
            .setMediaMetadata(metadata)
            .setDurationUs(if (durMs > 0) durMs * 1000 else C.TIME_UNSET)
            .setIsSeekable(true)
            .build()

        val commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_BACK,
                Player.COMMAND_SEEK_FORWARD,
            )
            .build()

        return State.Builder()
            .setAvailableCommands(commands)
            .setPlaybackState(Player.STATE_READY)
            .setPlayWhenReady(
                SpotifyWebPlayer.isPlaying,
                Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            )
            .setPlaylist(listOf(itemData))
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(posMs)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) SongPlayer.play() else SongPlayer.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> onAdvance(true)
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> onAdvance(false)
            else -> if (positionMs >= 0) SpotifyWebPlayer.seekTo(positionMs)
        }
        return Futures.immediateVoidFuture()
    }

    /** Called from the web-player poll to push fresh position/state to the session. */
    fun refresh() = invalidateState()
}
