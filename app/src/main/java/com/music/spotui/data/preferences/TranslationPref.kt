package com.music.spotui.data.preferences

import android.content.Context

private const val PREF_NAME = "translation_prefs"
private const val KEY_SOURCE = "source_language"
private const val KEY_TARGET = "target_language"

private fun prefs(c: Context) = c.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

fun getSourceLanguage(c: Context, default: String): String =
    prefs(c).getString(KEY_SOURCE, default) ?: default

fun setSourceLanguage(c: Context, lang: String) =
    prefs(c).edit().putString(KEY_SOURCE, lang).apply()

fun getTargetLanguage(c: Context, default: String): String =
    prefs(c).getString(KEY_TARGET, default) ?: default

fun setTargetLanguage(c: Context, lang: String) =
    prefs(c).edit().putString(KEY_TARGET, lang).apply()