package com.music.spotui.data.api

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
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

    /** Maps ISO 639-1 codes to ML Kit [TranslateLanguage] constants. */
    private val mlKitLangMap: Map<String, String> = mapOf(
        "en" to TranslateLanguage.ENGLISH,
        "es" to TranslateLanguage.SPANISH,
        "pt" to TranslateLanguage.PORTUGUESE,
        "fr" to TranslateLanguage.FRENCH,
        "de" to TranslateLanguage.GERMAN,
        "it" to TranslateLanguage.ITALIAN,
        "ja" to TranslateLanguage.JAPANESE,
        "ko" to TranslateLanguage.KOREAN,
        "zh" to TranslateLanguage.CHINESE,
        "ru" to TranslateLanguage.RUSSIAN,
        "ar" to TranslateLanguage.ARABIC,
        "hi" to TranslateLanguage.HINDI,
        "tr" to TranslateLanguage.TURKISH,
        "pl" to TranslateLanguage.POLISH,
        "nl" to TranslateLanguage.DUTCH,
        "sv" to TranslateLanguage.SWEDISH,
    )

    /** Languages the user can pick from, shown in the UI picker (code → display name). */
    val languages: List<Pair<String, String>> = listOf(
        "en" to "English",
        "es" to "Espa\u00f1ol",
        "pt" to "Portugu\u00eas",
        "fr" to "Fran\u00e7ais",
        "de" to "Deutsch",
        "it" to "Italiano",
        "ja" to "\u65e5\u672c\u8a9e",
        "ko" to "\ud55c\uad6d\uc5b4",
        "zh" to "\u4e2d\u6587",
        "ru" to "\u0420\u0443\u0441\u0441\u043a\u0438\u0439",
        "ar" to "\u0627\u0644\u0639\u0631\u0628\u064a\u0629",
        "hi" to "\u0939\u093f\u0928\u094d\u0926\u0940",
        "tr" to "T\u00fcrk\u00e7e",
        "pl" to "Polski",
        "nl" to "Nederlands",
        "sv" to "Svenska",
    )

    fun displayName(code: String): String =
        languages.firstOrNull { it.first == code }?.second ?: code.uppercase()

    fun detectLanguage(text: String): String {
        var cjk = 0;
        var hangul = 0;
        var cyrillic = 0;
        var arabic = 0;
        var devanagari = 0
        var latin = 0;
        var total = 0
        for (ch in text) {
            if (ch.isWhitespace()) continue
            total++
            val code = ch.code
            when {
                code in 0xAC00..0xD7AF || code in 0x1100..0x11FF || code in 0x3130..0x318F -> hangul++
                code in 0x4E00..0x9FFF || code in 0x3400..0x4DBF || code in 0xF900..0xFAFF -> cjk++
                code in 0x0400..0x04FF -> cyrillic++
                code in 0x0600..0x06FF || code in 0x0750..0x077F || code in 0xFB50..0xFDFF || code in 0xFE70..0xFEFF -> arabic++
                code in 0x0900..0x097F -> devanagari++
                code in 0x0041..0x005A || code in 0x0061..0x007A || code in 0x00C0..0x024F -> latin++
            }
        }
        if (total == 0) return "en"
        val threshold = total * 0.3
        return when {
            hangul > threshold -> "ko"
            cjk > threshold -> "zh"
            cyrillic > threshold -> "ru"
            arabic > threshold -> "ar"
            devanagari > threshold -> "hi"
            else -> "en"
        }
    }

    /**
     * Ensure the ML Kit model for [sourceLang] → [targetLang] is downloaded.
     * Returns the ready-to-use [Translator] instance.
     */
    suspend fun ensureModel(sourceLang: String, targetLang: String): Translator =
        withContext(Dispatchers.IO) {
            val source = mlKitLangMap[sourceLang]
                ?: throw IllegalArgumentException("Unsupported source language: $sourceLang")
            val target = mlKitLangMap[targetLang]
                ?: throw IllegalArgumentException("Unsupported target language: $targetLang")

            val options = TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(target)
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
