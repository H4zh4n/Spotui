package com.music.spotui.data.preferences

import android.content.Context

object BackupPref {
    private const val PREF = "BackupPrefs"
    private const val KEY_DIR_URI = "backup_directory_uri"
    private const val KEY_AUTO_BACKUP_ENABLED = "auto_backup_enabled"

    fun getDirectoryUri(context: Context): String? {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_DIR_URI, null)?.takeIf { it.isNotBlank() }
    }

    fun setDirectoryUri(context: Context, uri: String?) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY_DIR_URI, uri ?: "")
            .apply()
    }

    fun isAutoBackupEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_BACKUP_ENABLED, true)
    }

    fun setAutoBackupEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_AUTO_BACKUP_ENABLED, enabled)
            .apply()
    }
}
