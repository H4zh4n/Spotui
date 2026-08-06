package com.music.spotui.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.spotui.data.api.Response
import com.music.spotui.data.entity.AlbumsModel
import com.music.spotui.data.entity.ArtistsModel
import com.music.spotui.data.entity.HomeFeedModel
import com.music.spotui.ui.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(private val repository: AppRepository)  : ViewModel() {


    private val _albums : MutableStateFlow<Response<List<AlbumsModel>>> = MutableStateFlow(Response.Loading())
    val albums : StateFlow<Response<List<AlbumsModel>>> = _albums

    private val _artists : MutableStateFlow<Response<List<ArtistsModel>>> = MutableStateFlow(Response.Loading())
    val artists : StateFlow<Response<List<ArtistsModel>>> = _artists

    private val _home : MutableStateFlow<Response<HomeFeedModel>> = MutableStateFlow(Response.Loading())
    val home : StateFlow<Response<HomeFeedModel>> = _home

    private val _songs : MutableStateFlow<Response<List<com.music.spotui.data.entity.SongsModel>>> = MutableStateFlow(Response.Loading())
    val songs : StateFlow<Response<List<com.music.spotui.data.entity.SongsModel>>> = _songs

    private val _podcasts : MutableStateFlow<Response<com.music.spotui.data.entity.SearchResults>> = MutableStateFlow(Response.Loading())
    val podcasts : StateFlow<Response<com.music.spotui.data.entity.SearchResults>> = _podcasts

    private val _audiobooks : MutableStateFlow<Response<com.music.spotui.data.entity.SearchResults>> = MutableStateFlow(Response.Loading())
    val audiobooks : StateFlow<Response<com.music.spotui.data.entity.SearchResults>> = _audiobooks

    private val _followedArtists : MutableStateFlow<List<ArtistsModel>> = MutableStateFlow(emptyList())
    val followedArtists : StateFlow<List<ArtistsModel>> = _followedArtists

    private val _selectedFilter = MutableStateFlow("All")
    val selectedFilter: StateFlow<String> = _selectedFilter

    private val _isFollowingOnly = MutableStateFlow(false)
    val isFollowingOnly: StateFlow<Boolean> = _isFollowingOnly

    init {
        fetchHome()
        fetchArtists()
        fetchAlbums()
        fetchSongs()
        fetchPodcasts()
        fetchAudiobooks()
        fetchFollowedArtists()
    }

    fun setSelectedFilter(filter: String) {
        if (_selectedFilter.value != filter) {
            _selectedFilter.value = filter
            _isFollowingOnly.value = false
        }
    }

    fun toggleFollowing() {
        _isFollowingOnly.value = !_isFollowingOnly.value
    }

    fun resetFilters() {
        _selectedFilter.value = "All"
        _isFollowingOnly.value = false
    }

    private fun fetchHome() = viewModelScope.launch(Dispatchers.IO) {
        repository.provideHomeFeed().collect { feed ->
            _home.value = feed
        }
    }

    private fun fetchAlbums() = viewModelScope.launch(Dispatchers.IO) {
        repository.provideAlbums().collect{ album ->
            _albums.value = album as Response<List<AlbumsModel>>
        }
    }

    private fun fetchArtists() = viewModelScope.launch(Dispatchers.IO) {
        repository.provideArtists().collect { artist ->
            _artists.value = artist as Response<List<ArtistsModel>>
        }
    }

    private fun fetchSongs() = viewModelScope.launch(Dispatchers.IO) {
        repository.provideSongs().collect { songRes ->
            _songs.value = songRes
        }
    }

    private fun fetchPodcasts() = viewModelScope.launch(Dispatchers.IO) {
        repository.searchEverything("podcast").collect { podRes ->
            _podcasts.value = podRes
        }
    }

    private fun fetchAudiobooks() = viewModelScope.launch(Dispatchers.IO) {
        repository.searchEverything("audiobook").collect { abRes ->
            _audiobooks.value = abRes
        }
    }

    private fun fetchFollowedArtists() = viewModelScope.launch(Dispatchers.IO) {
        _followedArtists.value = repository.provideFollowedArtists()
    }
}