package com.music.spotui.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.net.Uri
import android.os.Build
import android.provider.Settings

data class HandlerAppInfo(val packageName: String, val label: String)

object DefaultLinkHelper {
    private const val PREF_NAME = "default_link_prefs"
    private const val KEY_DONT_SHOW_AGAIN = "dont_show_default_prompt"

    fun isAppDefaultLinkHandler(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val dvm = context.getSystemService(DomainVerificationManager::class.java)
                if (dvm != null) {
                    val userState = dvm.getDomainVerificationUserState(context.packageName)
                    if (userState != null && userState.isLinkHandlingAllowed) {
                        val hostStates = userState.hostToStateMap
                        val approved = hostStates.any { (host, state) ->
                            host.contains("spotify") && (
                                state == DomainVerificationUserState.DOMAIN_STATE_VERIFIED ||
                                state == DomainVerificationUserState.DOMAIN_STATE_SELECTED
                            )
                        }
                        if (approved) return true
                    }
                }
                false
            } else {
                val testIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/track/55T3HvQKuTr6N57mMoIgMc"))
                val resolveInfo = context.packageManager.resolveActivity(testIntent, PackageManager.MATCH_DEFAULT_ONLY)
                resolveInfo?.activityInfo?.packageName == context.packageName
            }
        } catch (e: Exception) {
            false
        }
    }

    fun isOfficialSpotifyInstalled(context: Context): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo("com.spotify.music", 0)
            true
        }.getOrDefault(false)
    }

    fun getCurrentDefaultHandlerApp(context: Context): HandlerAppInfo? {
        val testIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/track/55T3HvQKuTr6N57mMoIgMc"))
        val pm = context.packageManager
        val resolveInfo = pm.resolveActivity(testIntent, PackageManager.MATCH_DEFAULT_ONLY) ?: return null
        val activityInfo = resolveInfo.activityInfo ?: return null
        val targetPackage = activityInfo.packageName

        if (targetPackage == context.packageName) return null

        if (targetPackage == "android" || targetPackage == "com.android.internal.app" || targetPackage.contains("resolver")) {
            return null
        }

        val appLabel = runCatching {
            val appInfo = pm.getApplicationInfo(targetPackage, 0)
            pm.getApplicationLabel(appInfo).toString()
        }.getOrDefault(targetPackage)

        return HandlerAppInfo(packageName = targetPackage, label = appLabel)
    }

    fun openAppDetailsSettings(context: Context, packageName: String) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val intent = Intent(
                    Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                    Uri.parse("package:$packageName")
                ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                context.startActivity(intent)
            } else {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                context.startActivity(intent)
            }
        }.onFailure {
            val fallback = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            runCatching { context.startActivity(fallback) }
        }
    }

    fun openSpotuiDefaultSettings(context: Context) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val intent = Intent(
                    Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                context.startActivity(intent)
            } else {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                context.startActivity(intent)
            }
        }.onFailure {
            val fallback = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            runCatching { context.startActivity(fallback) }
        }
    }

    fun shouldShowPrompt(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DONT_SHOW_AGAIN, false)) return false
        return !isAppDefaultLinkHandler(context)
    }

    fun setDontShowAgain(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DONT_SHOW_AGAIN, true)
            .apply()
    }

    fun resetDontShowAgain(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DONT_SHOW_AGAIN, false)
            .apply()
    }
}
