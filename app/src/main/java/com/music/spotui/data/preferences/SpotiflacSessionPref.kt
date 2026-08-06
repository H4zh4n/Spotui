package com.music.spotui.data.preferences

import android.content.Context

/**
 * Stores the signed-session obtained from SpotiFLAC's gated backend (experimental
 * path 1a): the server-issued session_id + session_secret used to HMAC-sign the
 * community download requests. Captured by the WebView verification screen after
 * the user solves the Turnstile themselves.
 */
private const val PREF = "SpotiflacSession"

fun saveSpotiflacSession(
    context: Context,
    sessionId: String,
    sessionSecret: String,
    installId: String,
    expiresAt: String,
) {
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
        .putString("session_id", sessionId)
        .putString("session_secret", sessionSecret)
        .putString("install_id", installId)
        .putString("expires_at", expiresAt)
        .apply()
}

data class SpotiflacSession(
    val sessionId: String,
    val sessionSecret: String,
    val installId: String,
    val expiresAt: String,
)

fun getSpotiflacSession(context: Context): SpotiflacSession? {
    val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    val id = p.getString("session_id", null)?.takeIf { it.isNotBlank() } ?: return null
    val secret = p.getString("session_secret", null)?.takeIf { it.isNotBlank() } ?: return null
    return SpotiflacSession(
        sessionId = id,
        sessionSecret = secret,
        installId = p.getString("install_id", "").orEmpty(),
        expiresAt = p.getString("expires_at", "").orEmpty(),
    )
}

fun hasSpotiflacSession(context: Context): Boolean = getSpotiflacSession(context) != null

fun clearSpotiflacSession(context: Context) {
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
}
