package com.music.spotui.ui.notification

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.snapshotFlow
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.music.spotui.MainActivity
import com.music.spotui.R
import com.music.spotui.data.api.Api
import com.music.spotui.data.api.Response
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.di.CurrentSongState
import com.music.spotui.di.RepeatMode
import com.music.spotui.di.SongPlayer
import com.music.spotui.di.SpotifyWebPlayer
import com.music.spotui.ui.repository.AppRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hosts a [MediaSession] over the app's single ExoPlayer (owned by [SongPlayer]).
 * This is what surfaces the track in the system notification center / lock screen
 * and routes the notification's transport controls (play/pause/seek/next/prev)
 * back into playback. Next/previous are wired to the in-app queue because our
 * player only ever holds one resolved stream at a time (YouTube URLs are resolved
 * lazily per track), so we advance the queue ourselves rather than via a playlist.
 *
 * It is a [MediaLibraryService] (not just a session service) so Android Auto can
 * browse the library — Liked Songs, Downloads, playlists and albums — and start
 * playback from the car.
 */
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject
    lateinit var currentSongState: CurrentSongState
    @Inject
    lateinit var repository: AppRepository

    private var mediaSession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Android Auto browse cache: mediaId → track, and mediaId → the list it was
    // browsed from (so playing a track queues its whole playlist/album).
    private val trackById = java.util.concurrent.ConcurrentHashMap<String, SongsModel>()
    private val queueByTrackId = java.util.concurrent.ConcurrentHashMap<String, List<SongsModel>>()
    private val searchCache = java.util.concurrent.ConcurrentHashMap<String, List<SongsModel>>()
    private var webPlayer: WebMediaPlayer? = null
    private var showingWeb = false

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            currentSongState.updateBufferingState(playbackState == Player.STATE_BUFFERING)
            if (playbackState == Player.STATE_ENDED) {
                if (SongPlayer.isCrossfadeActive()) {
                    // Ignore the old player's STATE_ENDED event during an active crossfade.
                    // The crossfade routine itself handles the transition and promotes the new player.
                    return
                }
                val p = SongPlayer.exoPlayer
                if (p != null) {
                    val duration = p.duration
                    val position = p.currentPosition
                    if (duration <= 0 || position < duration - 6000) {
                        // Spurious STATE_ENDED event (e.g. player cleared/reset or ended prematurely before loading)! Ignore it!
                        return
                    }
                }
                when (currentSongState.repeat.value) {
                    RepeatMode.ONE -> {
                        val queue = currentSongState.queue.value
                        if (queue.isNotEmpty()) {
                            val curId = currentSongState.songId.value
                            val cur = queue.indexOfFirst { it.id == curId }
                                .let { if (it >= 0) it else currentSongState.songIndex.value }
                                .coerceIn(0, queue.size - 1)
                            val song = queue[cur]
                            SongPlayer.playSong(song.url, applicationContext, "song/${song.id}")
                        } else {
                            SongPlayer.exoPlayer?.seekTo(0)
                            SongPlayer.exoPlayer?.play()
                        }
                    }

                    RepeatMode.ALL -> {
                        advance(forward = true)
                    }

                    RepeatMode.OFF -> {
                        val queue = currentSongState.queue.value
                        if (queue.isNotEmpty()) {
                            val curId = currentSongState.songId.value
                            val cur = queue.indexOfFirst { it.id == curId }
                                .let { if (it >= 0) it else currentSongState.songIndex.value }
                                .coerceIn(0, queue.size - 1)
                            if (cur < queue.size - 1) {
                                advance(forward = true)
                            }
                        }
                    }
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // Mirror ExoPlayer's real play/pause state into the in-app UI so the
            // on-screen play button stays in sync when the notification controls are used.
            if (!showingWeb) currentSongState.updatePlayingState(isPlaying)
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            android.util.Log.e(
                "PlaybackService",
                "Player error during playback: ${error.message}",
                error
            )
            val queue = currentSongState.queue.value
            val curId = currentSongState.songId.value
            val cur = queue.indexOfFirst { it.id == curId }
            if (cur >= 0) {
                SongPlayer.invalidateResolvedStream(queue[cur].url)
            }
            advance(forward = true)
        }
    }

    override fun onCreate() {
        super.onCreate()
        SongPlayer.ensureCreated(this)

        // Order notification buttons: [repeat | prev | play/pause | next | close]
        val notificationProvider = object : DefaultMediaNotificationProvider(this) {
            override fun getMediaButtons(
                session: MediaSession,
                playerCommands: Player.Commands,
                customLayout: ImmutableList<CommandButton>,
                showPauseButton: Boolean
            ): ImmutableList<CommandButton> {
                val base =
                    super.getMediaButtons(session, playerCommands, customLayout, showPauseButton)
                val repeatBtn = base.find { it.sessionCommand?.customAction == "ACTION_REPEAT" }
                val closeBtn = base.find { it.sessionCommand?.customAction == "ACTION_CLOSE" }
                val transport = base.filter {
                    it.sessionCommand?.customAction != "ACTION_REPEAT" &&
                            it.sessionCommand?.customAction != "ACTION_CLOSE" &&
                            it.sessionCommand?.customAction != "ACTION_NONE"
                }
                return ImmutableList.builder<CommandButton>()
                    .apply { repeatBtn?.let { add(it) } }
                    .addAll(transport)
                    .apply { closeBtn?.let { add(it) } }
                    .build()
            }
        }
        setMediaNotificationProvider(notificationProvider)

        // Let the player advance the in-app queue itself during a crossfade.
        SongPlayer.bindState(currentSongState)
        val base = SongPlayer.exoPlayer ?: return
        base.addListener(playerListener)

        // Tapping the notification opens the app (back on the Now Playing screen).
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val sessionActivity = PendingIntent.getActivity(
            this, 0, activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        webPlayer = WebMediaPlayer(mainLooper, currentSongState) { forward -> advance(forward) }

        mediaSession = MediaLibrarySession.Builder(this, wrap(base), LibraryCallback())
            .setSessionActivity(sessionActivity)
            .build()

        // Keep the session queue in sync with the in-app queue changes
        serviceScope.launch {
            snapshotFlow { currentSongState.queue.value }
                .distinctUntilChanged()
                .collect {
                    activeForwardingPlayer?.invalidateTimeline()
                }
        }

        serviceScope.launch {
            snapshotFlow { currentSongState.songId.value }
                .distinctUntilChanged()
                .collect {
                    activeForwardingPlayer?.invalidateTimeline()
                }
        }

        // When a crossfade promotes a new ExoPlayer instance, re-bind the session to it
        // (runs on the main thread; setPlayer is the supported way to swap a session's player).
        SongPlayer.onPlayerSwapped = { newPlayer ->
            if (!showingWeb) mediaSession?.player = wrap(newPlayer)
            newPlayer.addListener(playerListener)
        }

        // Keep the notification's repeat icon in sync whenever the repeat mode
        // changes from ANY source (full-screen UI, notification button, etc.).
        serviceScope.launch {
            snapshotFlow { currentSongState.repeat.value }
                .distinctUntilChanged()
                .collect { mode ->
                    mediaSession?.setCustomLayout(buildCustomLayout(mode))
                }
        }

        // As the hidden web player streams, keep the notification in sync and swap
        // the session between the web player (during web playback) and the ExoPlayer.
        SpotifyWebPlayer.onStateChanged = {
            syncSessionPlayer()
            if (showingWeb) {
                webPlayer?.refresh()
                // Reflect the web player's real play/pause state into the in-app UI
                // so the on-screen icon matches after the notification's pause.
                currentSongState.updatePlayingState(SpotifyWebPlayer.isPlaying)
            }
        }
    }

    /** Point the media session at whichever engine is currently producing audio. */
    private fun syncSessionPlayer() {
        val wantWeb = SongPlayer.webPlaybackActive()
        if (wantWeb == showingWeb) return
        showingWeb = wantWeb
        val session = mediaSession ?: return
        session.player = if (wantWeb) {
            webPlayer ?: return
        } else {
            wrap(SongPlayer.exoPlayer ?: return)
        }
    }

    private var activeForwardingPlayer: QueueForwardingPlayer? = null

    private fun wrap(base: Player): QueueForwardingPlayer {
        return QueueForwardingPlayer(base).also { activeForwardingPlayer = it }
    }

    private inner class QueueForwardingPlayer(private val base: Player) : ForwardingPlayer(base) {
        private val listenerMap = java.util.concurrent.ConcurrentHashMap<Player.Listener, ListenerWrapper>()

        override fun addListener(listener: Player.Listener) {
            val wrapper = ListenerWrapper(listener)
            listenerMap[listener] = wrapper
            super.addListener(wrapper)
        }

        override fun removeListener(listener: Player.Listener) {
            val wrapper = listenerMap.remove(listener)
            if (wrapper != null) {
                super.removeListener(wrapper)
            } else {
                super.removeListener(listener)
            }
        }

        fun invalidateTimeline() {
            val timeline = currentTimeline
            listenerMap.values.forEach { wrapper ->
                wrapper.delegate.onTimelineChanged(timeline, Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)
            }
        }

        private inner class ListenerWrapper(val delegate: Player.Listener) : Player.Listener by delegate {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                delegate.onTimelineChanged(currentTimeline, reason)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                delegate.onMediaItemTransition(currentMediaItem, reason)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                val idx = currentMediaItemIndex
                val item = currentMediaItem
                val mappedOld = Player.PositionInfo(
                    oldPosition.windowUid ?: idx,
                    idx,
                    item,
                    oldPosition.periodUid ?: idx,
                    idx,
                    oldPosition.positionMs,
                    oldPosition.contentPositionMs,
                    oldPosition.adGroupIndex,
                    oldPosition.adIndexInAdGroup
                )
                val mappedNew = Player.PositionInfo(
                    newPosition.windowUid ?: idx,
                    idx,
                    item,
                    newPosition.periodUid ?: idx,
                    idx,
                    newPosition.positionMs,
                    newPosition.contentPositionMs,
                    newPosition.adGroupIndex,
                    newPosition.adIndexInAdGroup
                )
                delegate.onPositionDiscontinuity(mappedOld, mappedNew, reason)
            }

            override fun onEvents(player: Player, events: Player.Events) {
                delegate.onEvents(this@QueueForwardingPlayer, events)
            }
        }

        override fun getCurrentTimeline(): Timeline {
            val q = currentSongState.queue.value
            val curId = currentSongState.songId.value
            val idx = q.indexOfFirst { it.id == curId }.coerceAtLeast(0)
            val mediaItems = q.map { playable(it) }
            val currentItem = base.currentMediaItem
            return QueueTimeline(base.currentTimeline, currentItem, mediaItems, idx)
        }

        override fun getMediaItemCount(): Int = currentSongState.queue.value.size

        override fun getMediaItemAt(index: Int): MediaItem {
            val q = currentSongState.queue.value
            if (index in q.indices) {
                return playable(q[index])
            }
            return super.getMediaItemAt(index)
        }

        override fun getCurrentMediaItemIndex(): Int {
            val q = currentSongState.queue.value
            val baseItem = base.currentMediaItem
            if (baseItem != null && baseItem.mediaId.startsWith("song/")) {
                val idx = q.indexOfFirst { "song/${it.id}" == baseItem.mediaId }
                if (idx >= 0) return idx
            }
            val curId = currentSongState.songId.value
            val idx = q.indexOfFirst { it.id == curId }
            return if (idx >= 0) idx else 0
        }

        override fun getCurrentMediaItem(): MediaItem? {
            val q = currentSongState.queue.value
            val baseItem = base.currentMediaItem
            if (baseItem != null && baseItem.mediaId.startsWith("song/")) {
                val song = q.firstOrNull { "song/${it.id}" == baseItem.mediaId }
                if (song != null) return playable(song)
            }
            val curId = currentSongState.songId.value
            val song = q.firstOrNull { it.id == curId }
            return song?.let { playable(it) } ?: super.getCurrentMediaItem()
        }

        override fun getCurrentPeriodIndex(): Int {
            return currentMediaItemIndex
        }

        override fun getAvailableCommands(): Player.Commands =
            super.getAvailableCommands().buildUpon()
                .add(COMMAND_SEEK_TO_NEXT)
                .add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(COMMAND_SEEK_TO_PREVIOUS)
                .add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .build()

        override fun isCommandAvailable(command: Int): Boolean = when (command) {
            COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
            else -> super.isCommandAvailable(command)
        }

        override fun hasNextMediaItem() = true
        override fun hasPreviousMediaItem() = true
        override fun seekToNext() = advance(forward = true)
        override fun seekToNextMediaItem() = advance(forward = true)
        override fun seekToPrevious() = advance(forward = false)
        override fun seekToPreviousMediaItem() = advance(forward = false)

        override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
            val q = currentSongState.queue.value
            val curId = currentSongState.songId.value
            val curIdx = q.indexOfFirst { it.id == curId }
            if (mediaItemIndex == curIdx) {
                super.seekTo(0, positionMs)
            } else if (mediaItemIndex in q.indices) {
                val song = q[mediaItemIndex]
                currentSongState.updateSongState(
                    song.coverUri, song.title, song.singer, true,
                    song.id, mediaItemIndex, song.album
                )
                SongPlayer.playSong(song.url, applicationContext, "song/${song.id}")
            }
        }
    }

    /** Advance the in-app queue one step in the given direction and start it. */
    private fun advance(forward: Boolean) {
        val queue = currentSongState.queue.value
        if (queue.isEmpty()) return
        val curId = currentSongState.songId.value
        val cur = queue.indexOfFirst { it.id == curId }
            .let { if (it >= 0) it else currentSongState.songIndex.value }
            .coerceIn(0, queue.size - 1)

        val nextIdx: Int
        if (forward) {
            if (cur < queue.size - 1) {
                nextIdx = cur + 1
            } else {
                if (currentSongState.repeat.value == RepeatMode.ALL) {
                    nextIdx = 0
                } else {
                    return // do nothing at the end of the queue
                }
            }
        } else {
            if (cur > 0) {
                nextIdx = cur - 1
            } else {
                if (currentSongState.repeat.value == RepeatMode.ALL) {
                    nextIdx = queue.size - 1
                } else {
                    return // do nothing at the beginning of the queue
                }
            }
        }
        val song = queue[nextIdx]
        currentSongState.updateSongState(
            song.coverUri, song.title, song.singer, true,
            song.id, nextIdx, song.album
        )
        SongPlayer.playSong(song.url, applicationContext, "song/${song.id}")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaSession

    // ── Android Auto browse tree ──────────────────────────────────────────

    private companion object {
        const val ROOT = "root"
        const val NODE_LIKED = "liked"
        const val NODE_DOWNLOADS = "downloads"
        const val NODE_PLAYLISTS = "playlists"
        const val NODE_ALBUMS = "albums"
    }

    /**
     * Builds the notification custom layout: [repeat button | close button].
     * The repeat button icon reflects the current [mode].
     */
    private fun buildCustomLayout(mode: RepeatMode): ImmutableList<CommandButton> {
        val repeatIconRes = when (mode) {
            RepeatMode.OFF -> R.drawable.ic_repeat_off  // slashed arrows = disabled
            RepeatMode.ONE -> R.drawable.ic_repeat_one  // arrows + "1"
            RepeatMode.ALL -> R.drawable.ic_repeat      // plain arrows = loop all
        }
        val repeatLabel = when (mode) {
            RepeatMode.OFF -> "Repeat off"
            RepeatMode.ONE -> "Repeat one"
            RepeatMode.ALL -> "Repeat all"
        }
        val repeatButton = CommandButton.Builder()
            .setDisplayName(repeatLabel)
            .setSessionCommand(SessionCommand("ACTION_REPEAT", Bundle.EMPTY))
            .setIconResId(repeatIconRes)
            .build()

        val closeButton = CommandButton.Builder()
            .setDisplayName("Close")
            .setSessionCommand(SessionCommand("ACTION_CLOSE", Bundle.EMPTY))
            .setIconResId(R.drawable.ic_close)
            .build()

        return ImmutableList.of(repeatButton, closeButton)
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val baseResult = super.onConnect(session, controller)
            val sessionCommands = baseResult.availableSessionCommands.buildUpon()
                .add(SessionCommand("ACTION_CLOSE", Bundle.EMPTY))
                .add(SessionCommand("ACTION_REPEAT", Bundle.EMPTY))
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(baseResult.availablePlayerCommands)
                .setCustomLayout(buildCustomLayout(currentSongState.repeat.value))
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                "ACTION_CLOSE" -> {
                    // exit the player and kill the process directly to terminate the app cleanly
                    SongPlayer.pause()
                    android.os.Process.killProcess(android.os.Process.myPid())
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                "ACTION_REPEAT" -> {
                    // Cycle OFF → ALL → ONE → OFF (matches full-screen UI)
                    val next = when (currentSongState.repeat.value) {
                        RepeatMode.OFF -> RepeatMode.ALL
                        RepeatMode.ALL -> RepeatMode.ONE
                        RepeatMode.ONE -> RepeatMode.OFF
                    }
                    currentSongState.updateRepeatState(next)
                    // Update the custom layout so the notification icon reflects the new mode
                    val newLayout = buildCustomLayout(next)
                    mediaSession?.setCustomLayout(newLayout)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(folder(ROOT, "spotui"), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
            LibraryResult.ofItemList(ImmutableList.copyOf(childrenOf(parentId)), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(
            trackById[mediaId]?.let { LibraryResult.ofItem(playable(it), null) }
                ?: LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE),
        )

        // A browsed track was tapped in the car: queue the list it came from and
        // play through our own engine (streams are resolved lazily per track, so
        // we never hand the session a playlist of URIs).
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val requested = mediaItems.getOrNull(startIndex) ?: mediaItems.firstOrNull()
            val song = requested?.let { trackById[it.mediaId] }
            if (song != null) {
                val queue = queueByTrackId[requested.mediaId] ?: listOf(song)
                currentSongState.updateQueue(queue)
                val idx = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                currentSongState.updateSongState(
                    song.coverUri, song.title, song.singer, true,
                    song.id, idx, song.album,
                )
                SongPlayer.playSong(song.url, applicationContext, "song/${song.id}")
            }
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(emptyList(), C.INDEX_UNSET, C.TIME_UNSET),
            )
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> = future {
            if (query.isNotBlank()) {
                try {
                    val songs = lastSuccess(repository.searchSongs(query)).orEmpty()
                    searchCache[query] = songs
                    registerTracks("search/$query", songs)
                    session.notifySearchResultChanged(browser, query, songs.size, params)
                } catch (e: Exception) {
                    android.util.Log.e("PlaybackService", "Error in onSearch for query: $query", e)
                }
            }
            LibraryResult.ofVoid()
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val songs = searchCache[query].orEmpty()
            val items = songs.map { playable(it) }
            return Futures.immediateFuture(
                LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
            )
        }
    }

    private suspend fun childrenOf(parentId: String): List<MediaItem> = try {
        // Enforce a strict fallback timeout for Android Auto responsiveness
        kotlinx.coroutines.withTimeoutOrNull(1500) {
            when {
                parentId == ROOT -> listOf(
                    folder(
                        NODE_LIKED,
                        "Liked Songs",
                        mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
                    ),
                    folder(
                        NODE_DOWNLOADS,
                        "Downloads",
                        mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
                    ),
                    folder(
                        NODE_PLAYLISTS,
                        "Playlists",
                        mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
                    ),
                    folder(
                        NODE_ALBUMS,
                        "Albums",
                        mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
                    ),
                )

                parentId == NODE_LIKED ->
                    registerTracks(
                        NODE_LIKED,
                        lastSuccess(repository.provideLikedSongs()).orEmpty()
                    )

                parentId == NODE_DOWNLOADS ->
                    registerTracks(
                        NODE_DOWNLOADS,
                        com.music.spotui.data.preferences.getDownloadedSongs(applicationContext),
                    )

                parentId == NODE_PLAYLISTS ->
                    libraryEntries().filter {
                        it.isPlaylist && it.spotifyId != Api.LIKED_SONGS_ID && it.spotifyId != Api.DOWNLOADS_ID
                    }.map {
                        folder(
                            "playlist/${it.spotifyId}",
                            it.name,
                            it.coverUri,
                            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
                        )
                    }

                parentId == NODE_ALBUMS ->
                    libraryEntries().filter { !it.isPlaylist }.map {
                        folder(
                            "album/${android.net.Uri.encode(it.name)}/${android.net.Uri.encode(it.artists)}",
                            it.name,
                            it.coverUri,
                            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
                        )
                    }

                parentId.startsWith("playlist/") -> {
                    val songs =
                        lastSuccess(repository.providePlaylistSongs(parentId.removePrefix("playlist/")))
                    registerTracks(parentId, songs.orEmpty())
                }

                parentId.startsWith("album/") -> {
                    val parts = parentId.removePrefix("album/").split('/')
                    val name = android.net.Uri.decode(parts.getOrElse(0) { "" })
                    val artist = android.net.Uri.decode(parts.getOrElse(1) { "" })
                    registerTracks(
                        parentId,
                        lastSuccess(repository.provideAlbumSongs(name, artist)).orEmpty()
                    )
                }

                else -> emptyList()
            }
        } ?: emptyList()
    } catch (e: Exception) {
        android.util.Log.e("PlaybackService", "Error retrieving children for $parentId", e)
        emptyList()
    }

    private suspend fun libraryEntries() =
        lastSuccess(repository.provideLibrary()).orEmpty()

    /** Runs a paged/cached response flow to completion and keeps the final data. */
    private suspend fun <T> lastSuccess(flow: Flow<Response<T>>): T? {
        var result: T? = null
        try {
            // Android Auto requires quick responses to avoid infinite loading screens.
            // A 2000ms timeout ensures we never hang the media browser thread.
            kotlinx.coroutines.withTimeoutOrNull(2000) {
                flow.collect { response ->
                    if (response is Response.Success) {
                        result = response.data
                        // Found a success emission (cached or fresh). Cancel immediately to return fast.
                        throw kotlinx.coroutines.CancellationException("Success received")
                    } else if (response is Response.Error) {
                        throw kotlinx.coroutines.CancellationException("Error received")
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Expected cancellation to stop collecting
        } catch (e: Exception) {
            android.util.Log.e("PlaybackService", "Error in lastSuccess flow collection", e)
        }
        return result
    }

    private fun registerTracks(parentId: String, songs: List<SongsModel>): List<MediaItem> {
        songs.forEach { song ->
            trackById["song/${song.id}"] = song
            queueByTrackId["song/${song.id}"] = songs
        }
        return songs.map { playable(it) }
    }

    private fun folder(
        id: String,
        title: String,
        coverUri: String = "",
        mediaType: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
    ): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(mediaType)
                    .apply { if (coverUri.isNotBlank()) setArtworkUri(android.net.Uri.parse(coverUri)) }
                    .build(),
            )
            .build()

    private fun playable(song: SongsModel): MediaItem =
        MediaItem.Builder()
            .setMediaId("song/${song.id}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.singer)
                    .setAlbumTitle(song.album)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .apply {
                        if (song.coverUri.isNotBlank()) setArtworkUri(
                            android.net.Uri.parse(
                                song.coverUri
                            )
                        )
                    }
                    .build(),
            )
            .build()

    private fun <T> future(block: suspend () -> T): ListenableFuture<T> {
        val f = SettableFuture.create<T>()
        serviceScope.launch {
            try {
                f.set(block())
            } catch (e: Exception) {
                f.setException(e)
            }
        }
        return f
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Stop playback + tear the service down when the app is swiped away.
        SongPlayer.pause()
        stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        SongPlayer.exoPlayer?.removeListener(playerListener)
        SongPlayer.onPlayerSwapped = null
        SpotifyWebPlayer.onStateChanged = null
        webPlayer?.release()
        webPlayer = null
        searchCache.clear()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}

class QueueTimeline(
    private val baseTimeline: Timeline,
    private val currentMediaItem: MediaItem?,
    private val queue: List<MediaItem>,
    private val currentIndex: Int
) : Timeline() {

    override fun getWindowCount(): Int = queue.size

    override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
        val item = queue.getOrNull(windowIndex) ?: MediaItem.EMPTY
        if (windowIndex == currentIndex && currentMediaItem != null) {
            val activeTimeline = baseTimeline
            if (!activeTimeline.isEmpty) {
                activeTimeline.getWindow(0, window, defaultPositionProjectionUs)
                window.mediaItem = item
                return window
            }
        }
        window.uid = windowIndex
        window.mediaItem = item
        window.manifest = null
        window.presentationStartTimeMs = C.TIME_UNSET
        window.windowStartTimeMs = C.TIME_UNSET
        window.elapsedRealtimeEpochOffsetMs = C.TIME_UNSET
        window.isSeekable = true
        window.isDynamic = false
        window.liveConfiguration = null
        window.defaultPositionUs = 0
        window.durationUs = C.TIME_UNSET
        window.firstPeriodIndex = windowIndex
        window.lastPeriodIndex = windowIndex
        window.positionInFirstPeriodUs = 0
        window.isPlaceholder = false
        return window
    }

    override fun getPeriodCount(): Int = queue.size

    override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
        val id = queue.getOrNull(periodIndex)?.mediaId ?: periodIndex.toString()
        if (periodIndex == currentIndex && currentMediaItem != null) {
            val activeTimeline = baseTimeline
            if (!activeTimeline.isEmpty) {
                activeTimeline.getPeriod(0, period, setIds)
                period.windowIndex = periodIndex
                if (setIds) {
                    period.id = id
                    period.uid = id
                }
                return period
            }
        }
        period.id = id
        period.uid = id
        period.windowIndex = periodIndex
        period.durationUs = C.TIME_UNSET
        period.positionInWindowUs = 0
        return period
    }

    override fun getIndexOfPeriod(uid: Any): Int {
        val id = uid as? String ?: return C.INDEX_UNSET
        return queue.indexOfFirst { it.mediaId == id }
    }

    override fun getUidOfPeriod(periodIndex: Int): Any {
        return queue.getOrNull(periodIndex)?.mediaId ?: periodIndex.toString()
    }
}
