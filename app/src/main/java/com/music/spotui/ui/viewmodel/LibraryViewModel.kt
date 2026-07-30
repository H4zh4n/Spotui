package com.music.spotui.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.spotui.data.api.Response
import com.music.spotui.data.entity.AccountModel
import com.music.spotui.data.entity.LibraryEntry
import com.music.spotui.ui.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

import kotlinx.coroutines.Job

enum class LibraryFilterType {
    ALL, PLAYLISTS, ALBUMS, ARTISTS
}

@HiltViewModel
class LibraryViewModel @Inject constructor(private val repository: AppRepository) : ViewModel() {

    private val _entries: MutableStateFlow<Response<List<LibraryEntry>>> = MutableStateFlow(Response.Loading())
    val entries: StateFlow<Response<List<LibraryEntry>>> = _entries

    private val _account: MutableStateFlow<Response<AccountModel>> = MutableStateFlow(Response.Loading())
    val account: StateFlow<Response<AccountModel>> = _account

    private val _followedArtists: MutableStateFlow<List<com.music.spotui.data.entity.ArtistsModel>> =
        MutableStateFlow(emptyList())
    val followedArtists: StateFlow<List<com.music.spotui.data.entity.ArtistsModel>> = _followedArtists

    private val _selectedFilter: MutableStateFlow<LibraryFilterType> = MutableStateFlow(LibraryFilterType.ALL)
    val selectedFilter: StateFlow<LibraryFilterType> = _selectedFilter

    private val _isDownloadedOnly: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isDownloadedOnly: StateFlow<Boolean> = _isDownloadedOnly

    private var loadJob: Job? = null

    init {
        load()
        loadAccount()
        loadFollowedArtists()
    }

    fun setFilterType(type: LibraryFilterType) {
        _selectedFilter.value = if (_selectedFilter.value == type) LibraryFilterType.ALL else type
    }

    fun toggleDownloadedOnly() {
        _isDownloadedOnly.value = !_isDownloadedOnly.value
    }

    fun clearFilters() {
        _selectedFilter.value = LibraryFilterType.ALL
        _isDownloadedOnly.value = false
    }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            repository.provideLibrary().collect { response ->
                // Keep showing existing data while a background refresh is in-flight,
                // so the user never sees a skeleton flash or loses scroll position.
                if (response !is Response.Loading || _entries.value !is Response.Success) {
                    _entries.value = response
                }
            }
        }
    }

    private fun loadFollowedArtists() = viewModelScope.launch(Dispatchers.IO) {
        _followedArtists.value = repository.provideFollowedArtists()
    }

    private fun loadAccount() = viewModelScope.launch(Dispatchers.IO) {
        repository.provideAccount().collect { _account.value = it }
    }
}
