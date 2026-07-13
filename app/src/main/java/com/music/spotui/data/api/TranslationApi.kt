package com.music.spotui.data.api

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.music.spotui.data.entity.LyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

object TranslationApi {
    private const val TAG = "TranslationApi"

    /** In-memory cache: "sourceText|sourceLang|targetLang" → translatedText */
    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Languages the user can pick from, shown in the UI picker (code → display name). */
    val languages: List<Pair<String, String>> = listOf(
        "af" to "Afrikaans",
        "ar" to "العربية",
        "be" to "Беларуская",
        "bg" to "Български",
        "bn" to "বাংলা",
        "ca" to "Català",
        "cs" to "Čeština",
        "cy" to "Cymraeg",
        "da" to "Dansk",
        "de" to "Deutsch",
        "el" to "Ελληνικά",
        "en" to "English",
        "es" to "Español",
        "et" to "Eesti",
        "fa" to "فارسی",
        "fi" to "Suomi",
        "fr" to "Français",
        "ga" to "Gaeilge",
        "gl" to "Galego",
        "gu" to "ગુજરાતી",
        "he" to "עברית",
        "hi" to "हिन्दी",
        "hr" to "Hrvatski",
        "hu" to "Magyar",
        "id" to "Indonesia",
        "is" to "Íslenska",
        "it" to "Italiano",
        "ja" to "日本語",
        "ka" to "ქართული",
        "kn" to "ಕನ್ನಡ",
        "ko" to "한국어",
        "lt" to "Lietuvių",
        "lv" to "Latviešu",
        "mk" to "Македонски",
        "ml" to "മലയാളം",
        "mr" to "मराठी",
        "ms" to "Melayu",
        "nb" to "Norsk bokmål",
        "nl" to "Nederlands",
        "pl" to "Polski",
        "pt" to "Português",
        "ro" to "Română",
        "ru" to "Русский",
        "sk" to "Slovenčina",
        "sl" to "Slovenščina",
        "sq" to "Shqip",
        "sv" to "Svenska",
        "sw" to "Kiswahili",
        "ta" to "தமிழ்",
        "te" to "తెలుగు",
        "th" to "ไทย",
        "tl" to "Tagalog",
        "tr" to "Türkçe",
        "uk" to "Українська",
        "ur" to "اردو",
        "vi" to "Tiếng Việt",
        "zh" to "中文",
    )

    fun displayName(code: String): String =
        languages.firstOrNull { it.first == code }?.second ?: code.uppercase()

    /**
     * Detect the language of [text] using ML Kit's on-device Language
     * Identification API. Falls back to "en" on failure or if the detected
     * language isn't supported for translation.
     */
    suspend fun detectLanguage(text: String): String {
        if (text.isBlank()) return "en"
        return try {
            val options = LanguageIdentificationOptions.Builder()
                .setConfidenceThreshold(0.5f)
                .build()
            val identifier = LanguageIdentification.getClient(options)
            val result = identifier.identifyLanguage(text).await<String>()
            if (result == "und" || languages.none { it.first == result }) "en" else result
        } catch (e: Exception) {
            Log.w(TAG, "Language detection failed", e)
            "en"
        }
    }

    /**
     * Ensure the ML Kit model for [sourceLang] → [targetLang] is downloaded.
     * Returns the ready-to-use [Translator] instance.
     */
    suspend fun ensureModel(sourceLang: String, targetLang: String): Translator =
        withContext(Dispatchers.IO) {
            require(languages.any { it.first == sourceLang }) { "Unsupported source language: $sourceLang" }
            require(languages.any { it.first == targetLang }) { "Unsupported target language: $targetLang" }

            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceLang)
                .setTargetLanguage(targetLang)
                .build()
            val translator = Translation.getClient(options)

            val conditions = DownloadConditions.Builder()
                .requireWifi()
                .build()
            translator.downloadModelIfNeeded(conditions).await()
            Log.d(TAG, "Model ready: $sourceLang → $targetLang")
            translator
        }

    /**
     * Translate all non-blank lyric lines through a single ML Kit [Translator] in one pass.
     * Blank lines are left unchanged.
     */
    suspend fun translateLines(
        lines: List<LyricLine>,
        sourceLang: String,
        targetLang: String = Locale.getDefault().language,
    ): List<LyricLine> = withContext(Dispatchers.IO) {
        val translator = ensureModel(sourceLang, targetLang)

        try {
            lines.map { line ->
                if (line.text.isBlank()) return@map line

                val cacheKey = "${line.text}|$sourceLang|$targetLang"
                cache[cacheKey]?.let { cached -> return@map line.copy(translatedText = cached) }

                val translated = translator.translate(line.text).await()
                if (!translated.isNullOrBlank()) {
                    cache[cacheKey] = translated
                    line.copy(translatedText = translated)
                } else {
                    line
                }
            }
        } finally {
            translator.close()
        }
    }
}
