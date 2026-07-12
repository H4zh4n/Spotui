package com.music.spotui.ui.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.utils.YTPlayerUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

@HiltViewModel
class AlternativeSearchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    var searchResults by mutableStateOf<List<SongItem>>(emptyList())
        private set
    var isSearching by mutableStateOf(false)
        private set
    var searchQuery by mutableStateOf("")
        private set
    var previewingVideoId by mutableStateOf<String?>(null)
        private set
    var isResolvingPreview by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var previewPlayer: ExoPlayer? = null
    private var searchJob: Job? = null
    private var resolveJob: Job? = null
    private var lastInitQuery: String = ""

    fun initQuery(title: String, artist: String) {
        val query = "$title $artist".trim()
        if (query == lastInitQuery) return
        lastInitQuery = query
        searchQuery = query
        searchResults = emptyList()
        error = null
        search(query)
    }

    fun updateQuery(query: String) {
        searchQuery = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            search(query)
        }
    }

    private fun search(query: String) {
        if (query.isBlank()) {
            searchResults = emptyList()
            return
        }
        isSearching = true
        error = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
            }
            result.fold(
                onSuccess = { sr ->
                    searchResults = sr.items.filterIsInstance<SongItem>()
                },
                onFailure = { e ->
                    Log.w(TAG, "YouTube search failed", e)
                    error = "Search failed"
                    searchResults = emptyList()
                }
            )
            isSearching = false
        }
    }

    fun preview(song: SongItem) {
        if (previewingVideoId == song.id) {
            stopPreview()
            return
        }
        stopPreview()
        previewingVideoId = song.id
        isResolvingPreview = true
        com.music.spotui.di.SongPlayer.pause()
        resolveJob = viewModelScope.launch {
            val playback = withContext(Dispatchers.IO) {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val quality = com.music.spotui.data.preferences.currentStreamingQuality(context)
                YTPlayerUtils.playerResponseForPlayback(
                    videoId = song.id,
                    audioQuality = quality.audioQuality,
                    connectivityManager = cm,
                    skipValidation = true,
                )
            }
            playback.fold(
                onSuccess = { data ->
                    val player = getOrCreatePreviewPlayer()
                    withContext(Dispatchers.Main) {
                        player.setMediaItem(MediaItem.fromUri(data.streamUrl))
                        player.prepare()
                        player.play()
                    }
                    isResolvingPreview = false
                },
                onFailure = { e ->
                    Log.w(TAG, "Preview resolve failed for ${song.id}", e)
                    previewingVideoId = null
                    isResolvingPreview = false
                }
            )
        }
    }

    fun stopPreview() {
        resolveJob?.cancel()
        resolveJob = null
        previewPlayer?.let { p ->
            p.stop()
            p.release()
        }
        previewPlayer = null
        previewingVideoId = null
        isResolvingPreview = false
    }

    private fun getOrCreatePreviewPlayer(): ExoPlayer {
        previewPlayer?.let { return it }
        val p = ExoPlayer.Builder(context)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .build(),
                false, // don't handle audio focus — this is transient preview
            )
            .setHandleAudioBecomingNoisy(false)
            .build()
        previewPlayer = p
        return p
    }

    override fun onCleared() {
        stopPreview()
    }

    companion object {
        private const val TAG = "AltSearchVM"
    }
}
