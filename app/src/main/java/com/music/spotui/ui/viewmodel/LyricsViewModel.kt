package com.music.spotui.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.spotui.data.api.LyricsApi
import com.music.spotui.data.api.TranslationApi
import com.music.spotui.data.entity.LyricLine
import com.music.spotui.data.entity.Lyrics
import com.music.spotui.data.preferences.getSourceLanguage
import com.music.spotui.data.preferences.getTargetLanguage
import com.music.spotui.data.preferences.setSourceLanguage
import com.music.spotui.data.preferences.setTargetLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class LyricsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    sealed class State {
        object Loading : State()
        data class Loaded(val lyrics: Lyrics) : State()
        object NotFound : State()
    }

    sealed class ModelState {
        object Idle : ModelState()
        object Downloading : ModelState()
        object Ready : ModelState()
        data class Failed(val message: String) : ModelState()
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state

    private var loadedKey: String? = null

    var showLanguageBar by mutableStateOf(false)
        private set

    var translationsLoading by mutableStateOf(false)
        private set
    var translatedLines by mutableStateOf<List<LyricLine>?>(null)
        private set
    var translationError by mutableStateOf<String?>(null)
        private set

    var modelState by mutableStateOf<ModelState>(ModelState.Idle)
        private set

    var sourceLanguage by mutableStateOf(getSourceLanguage(context, "en"))
        private set
    var targetLanguage by mutableStateOf(getTargetLanguage(context, Locale.getDefault().language))
        private set
    val availableLanguages: List<Pair<String, String>> = TranslationApi.languages

    val translationsVisible: Boolean get() = translatedLines != null

    fun load(title: String, artist: String, album: String, durationSec: Int) {
        val key = "$title|$artist"
        if (loadedKey == key && _state.value !is State.NotFound) return
        loadedKey = key
        _state.value = State.Loading
        resetTranslationState()
        viewModelScope.launch(Dispatchers.IO) {
            val lyrics = LyricsApi.fetch(title, artist, album, durationSec)
            withContext(Dispatchers.Main) {
                _state.value = if (lyrics == null || lyrics.isEmpty) State.NotFound
                else {
                    val allText = lyrics.lines.joinToString(" ") { it.text }
                    sourceLanguage = TranslationApi.detectLanguage(allText)
                    State.Loaded(lyrics)
                }
            }
        }
    }

    fun toggleLanguageBar() {
        showLanguageBar = !showLanguageBar
        if (!showLanguageBar) {
            translatedLines = null
            translationsLoading = false
            translationError = null
            modelState = ModelState.Idle
        }
    }

    fun clearError() { translationError = null }

    fun startTranslation() {
        if (translationsLoading) return
        val current = _state.value
        if (current !is State.Loaded) return

        if (sourceLanguage == targetLanguage) {
            translationError = "Source and target language must be different"
            return
        }

        val translatable = current.lyrics.lines.count { it.text.isNotBlank() }
        if (translatable == 0) {
            translationError = "No translatable lyrics found"
            return
        }

        translationError = null
        translationsLoading = true
        translatedLines = null
        modelState = ModelState.Downloading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Downloading model: $sourceLanguage → $targetLanguage")
                TranslationApi.ensureModel(sourceLanguage, targetLanguage)
                withContext(Dispatchers.Main) { modelState = ModelState.Ready }

                Log.d(TAG, "Starting translation: $sourceLanguage → $targetLanguage ($translatable lines)")
                val result = TranslationApi.translateLines(
                    lines = current.lyrics.lines,
                    sourceLang = sourceLanguage,
                    targetLang = targetLanguage,
                )
                val translatedCount = result.count { it.translatedText != null }
                Log.d(TAG, "Translation complete: $translatedCount/${result.size} lines translated")
                withContext(Dispatchers.Main) {
                    if (translatedCount == 0) {
                        translationError = "Translation failed — no lines were translated."
                    } else {
                        translatedLines = result
                    }
                    translationsLoading = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Translation failed", e)
                withContext(Dispatchers.Main) {
                    modelState = ModelState.Failed(e.localizedMessage ?: "Model download failed")
                    translationError = "Translation failed: ${e.localizedMessage ?: "Unknown error"}"
                    translationsLoading = false
                }
            }
        }
    }

    fun chooseSourceLanguage(langCode: String) {
        if (langCode == sourceLanguage) return
        sourceLanguage = langCode
        setSourceLanguage(context, langCode)
        translationError = null
        translatedLines = null
    }

    fun chooseTargetLanguage(langCode: String) {
        if (langCode == targetLanguage) return
        targetLanguage = langCode
        setTargetLanguage(context, langCode)
        translationError = null
        translatedLines = null
    }

    fun swapLanguages() {
        val tmp = sourceLanguage
        sourceLanguage = targetLanguage
        targetLanguage = tmp
        setSourceLanguage(context, sourceLanguage)
        setTargetLanguage(context, targetLanguage)
        translationError = null
        translatedLines = null
    }

    private fun resetTranslationState() {
        showLanguageBar = false
        translationsLoading = false
        translatedLines = null
        translationError = null
        modelState = ModelState.Idle
    }

    companion object {
        private const val TAG = "LyricsVM"
    }
}
