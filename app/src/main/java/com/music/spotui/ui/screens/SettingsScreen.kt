package com.music.spotui.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import com.music.spotui.data.preferences.BackupPref
import com.music.spotui.util.BackupHelper
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.music.spotui.data.BatteryOptimizationHelper
import com.music.spotui.data.preferences.CROSSFADE_MAX_MS
import com.music.spotui.data.preferences.StreamQuality
import com.music.spotui.data.preferences.getCellularQuality
import com.music.spotui.data.preferences.getCrossfadeMs
import com.music.spotui.data.preferences.setCrossfadeMs
import com.music.spotui.data.preferences.getDownloadQuality
import com.music.spotui.data.preferences.isVideoFallbackEnabled
import com.music.spotui.data.preferences.isAutoPlayEnabled
import com.music.spotui.data.preferences.getWifiQuality
import com.music.spotui.data.preferences.setCellularQuality
import com.music.spotui.data.preferences.setDownloadQuality
import com.music.spotui.data.preferences.setAutoPlayEnabled
import com.music.spotui.data.preferences.setVideoFallbackEnabled
import com.music.spotui.data.preferences.setWifiQuality
import com.music.spotui.data.preferences.getUpdateRepoUrl
import com.music.spotui.data.preferences.setUpdateRepoUrl
import com.music.spotui.data.preferences.resetUpdateRepoUrl
import com.music.spotui.data.preferences.DEFAULT_UPDATE_REPO_URL
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.music.spotui.ui.components.DefaultAppPrompt
import com.music.spotui.util.DefaultLinkHelper
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.AppPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current

    var wifiQ by remember { mutableStateOf(getWifiQuality(context)) }
    var cellQ by remember { mutableStateOf(getCellularQuality(context)) }
    var dlQ by remember { mutableStateOf(getDownloadQuality(context)) }
    var crossfadeMs by remember { mutableStateOf(getCrossfadeMs(context).toFloat()) }
    var videoFallback by remember { mutableStateOf(isVideoFallbackEnabled(context)) }
    var autoPlay by remember { mutableStateOf(isAutoPlayEnabled(context)) }
    var batteryOptExempt by remember { mutableStateOf(BatteryOptimizationHelper.isIgnoringBatteryOptimization(context)) }
    var updateRepoUrl by remember { mutableStateOf(getUpdateRepoUrl(context)) }
    var isDefaultLinkHandler by remember { mutableStateOf(DefaultLinkHelper.isAppDefaultLinkHandler(context)) }
    var showDefaultGuide by remember { mutableStateOf(false) }

    var backupDirUri by remember { mutableStateOf(BackupPref.getDirectoryUri(context)) }
    var folderName by remember(backupDirUri) { mutableStateOf(BackupHelper.getFolderDisplayName(context, backupDirUri)) }
    var isAutoBackup by remember { mutableStateOf(BackupPref.isAutoBackupEnabled(context)) }
    var isRestoring by remember { mutableStateOf(false) }
    var isBackingUp by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val dirPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            BackupPref.setDirectoryUri(context, uri.toString())
            backupDirUri = uri.toString()
            folderName = BackupHelper.getFolderDisplayName(context, uri.toString())
            scope.launch {
                val autoOk = BackupHelper.performAutoBackup(context)
                val msg = if (autoOk) "Backup folder set to $folderName (Auto-backup created)" else "Backup folder set to $folderName"
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    val restoreFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isRestoring = true
            scope.launch {
                val (success, message) = BackupHelper.restoreFromFileUri(context, uri)
                isRestoring = false
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                if (success) {
                    wifiQ = getWifiQuality(context)
                    cellQ = getCellularQuality(context)
                    dlQ = getDownloadQuality(context)
                    crossfadeMs = getCrossfadeMs(context).toFloat()
                    videoFallback = isVideoFallbackEnabled(context)
                    autoPlay = isAutoPlayEnabled(context)
                    updateRepoUrl = getUpdateRepoUrl(context)
                    backupDirUri = BackupPref.getDirectoryUri(context)
                    isAutoBackup = BackupPref.isAutoBackupEnabled(context)
                }
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDefaultLinkHandler = DefaultLinkHelper.isAppDefaultLinkHandler(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val batteryOptLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        batteryOptExempt = BatteryOptimizationHelper.isIgnoringBatteryOptimization(context)
    }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(26.dp)
                            .clickable { navController.popBackStack() }
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = AppBackground)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                // Clear the bottom nav + mini player so the last section
                // (account / log out) isn't hidden under the bar.
                .padding(bottom = 200.dp)
        ) {
            SectionTitle("Background playback")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        batteryOptLauncher.launch(BatteryOptimizationHelper.buildAppSettingsIntent(context))
                    }
                    .background(Color(0xFF1A1A20))
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Battery optimization", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (batteryOptExempt) "Exempt — app won't be killed" else "Not exempt — tap to change",
                        color = if (batteryOptExempt) Color(0xFF81C784) else Color(0xFFB3B3B3),
                        fontSize = 12.sp,
                    )
                }
                if (batteryOptExempt) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Enabled",
                        tint = AppPalette,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            BatteryOptimizationHelper.getManufacturerTips()?.let { (name, tip) ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Tip for $name",
                    color = Color(0xFFB3B3B3),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = tip,
                    color = Color(0xFF808080),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1A1A20))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("Link handling")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        if (isDefaultLinkHandler) {
                            DefaultLinkHelper.openSpotuiDefaultSettings(context)
                        } else {
                            showDefaultGuide = true
                        }
                    }
                    .background(Color(0xFF1A1A20))
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Open Spotify links by default", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (isDefaultLinkHandler) "Spotui handles Spotify URLs by default" else "Not default — tap to open setup guide",
                        color = if (isDefaultLinkHandler) Color(0xFF81C784) else Color(0xFFB3B3B3),
                        fontSize = 12.sp,
                    )
                }
                if (isDefaultLinkHandler) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Enabled",
                        tint = AppPalette,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = "Open Settings",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            SectionTitle("Audio quality")
            QualityPicker(
                title = "Streaming over Wi-Fi",
                selected = wifiQ,
                showFlacWarning = wifiQ == StreamQuality.LOSSLESS,
            ) { wifiQ = it; setWifiQuality(context, it) }

            QualityPicker(
                title = "Streaming over cellular",
                selected = cellQ,
                showFlacWarning = cellQ == StreamQuality.LOSSLESS,
            ) { cellQ = it; setCellularQuality(context, it) }

            QualityPicker(
                title = "Download quality",
                selected = dlQ,
            ) { dlQ = it; setDownloadQuality(context, it) }

            // Live lossless-server status (spotbye). Lossless only resolves when a
            // server is up; otherwise playback goes straight to YouTube.
            var losslessStatus by remember { mutableStateOf("Lossless servers: checking…") }
            LaunchedEffect(Unit) {
                val up = runCatching { com.metrolist.spotify.SpotiFlac.upLosslessProviders() }.getOrNull()
                losslessStatus = when {
                    up == null -> "Lossless servers: status unavailable"
                    up.isEmpty() -> "Lossless servers: 0/3 up — streaming (YouTube)"
                    else -> "Lossless servers: ${up.size}/3 up (${up.sorted().joinToString(", ")})"
                }
            }
            Text(
                losslessStatus,
                color = Color(0xFFB3B3B3),
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            )

            Spacer(Modifier.height(12.dp))
            SectionTitle("Matching")
            SettingsSwitchRow(
                title = "Allow video fallback",
                subtitle = "Use regular YouTube videos only after Music song results fail",
                checked = videoFallback,
            ) {
                videoFallback = it
                setVideoFallbackEnabled(context, it)
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("Playback")
            SettingsSwitchRow(
                title = "Auto-play on startup",
                subtitle = "Resume playing the last track when the app opens",
                checked = autoPlay,
            ) {
                autoPlay = it
                setAutoPlayEnabled(context, it)
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("Crossfade")
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Crossfade", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    if (crossfadeMs <= 0f) "Off" else "${(crossfadeMs / 1000f).let { String.format("%.0f", it) }}s",
                    color = if (crossfadeMs <= 0f) Color(0xFFB3B3B3) else AppPalette,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                "Blend the end of a song into the start of the next",
                color = Color(0xFFB3B3B3),
                fontSize = 13.sp,
            )
            Slider(
                value = crossfadeMs,
                onValueChange = { crossfadeMs = it },
                onValueChangeFinished = { setCrossfadeMs(context, crossfadeMs.toInt()) },
                valueRange = 0f..CROSSFADE_MAX_MS.toFloat(),
                steps = (CROSSFADE_MAX_MS / 1000) - 1, // 1-second stops
                colors = SliderDefaults.colors(
                    thumbColor = AppPalette,
                    activeTrackColor = AppPalette,
                    inactiveTrackColor = Color(0xFF333333),
                ),
            )
            Spacer(Modifier.height(12.dp))
            SectionTitle("Updates")
            Text(
                "Update source repository",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "GitHub repo URL used to check for new versions",
                color = Color(0xFFB3B3B3),
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = updateRepoUrl,
                onValueChange = {
                    updateRepoUrl = it
                    setUpdateRepoUrl(context, it)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = AppPalette,
                    unfocusedBorderColor = Color(0xFF333333),
                    cursorColor = AppPalette,
                    focusedPlaceholderColor = Color(0xFF666666),
                    unfocusedPlaceholderColor = Color(0xFF666666),
                ),
                placeholder = { Text("https://github.com/Owner/Repo") },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Reset to default",
                        tint = if (updateRepoUrl != DEFAULT_UPDATE_REPO_URL) AppPalette else Color(0xFF444444),
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .clickable {
                                updateRepoUrl = DEFAULT_UPDATE_REPO_URL
                                resetUpdateRepoUrl(context)
                            }
                    )
                },
            )
            Spacer(Modifier.height(12.dp))
            SectionTitle("Backup & Restore")

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = !isBackingUp) {
                            if (backupDirUri.isNullOrBlank()) {
                                dirPickerLauncher.launch(null)
                            } else {
                                isBackingUp = true
                                scope.launch {
                                    val (success, message) = BackupHelper.performManualBackup(context)
                                    isBackingUp = false
                                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        .background(Color(0xFF1A1A20))
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Back Up Now", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                isBackingUp -> "Creating backup in background…"
                                backupDirUri.isNullOrBlank() -> "Tap to choose folder & back up"
                                else -> "Folder: $folderName"
                            },
                            color = if (backupDirUri.isNullOrBlank()) Color(0xFFFFB74D) else Color(0xFFB3B3B3),
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }
                    if (isBackingUp) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = AppPalette,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Save,
                            contentDescription = "Back Up Now",
                            tint = AppPalette,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                if (!backupDirUri.isNullOrBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1A1A20))
                            .clickable(enabled = !isBackingUp) { dirPickerLauncher.launch(null) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = "Change Backup Folder",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = !isRestoring) {
                        restoreFileLauncher.launch(arrayOf("application/json", "*/*"))
                    }
                    .background(Color(0xFF1A1A20))
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Restore from File", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (isRestoring) "Restoring backup in background…" else "Import playlists and settings from a Spotui backup file",
                        color = Color(0xFFB3B3B3),
                        fontSize = 12.sp,
                    )
                }
                if (isRestoring) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = AppPalette,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = "Restore",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            SettingsSwitchRow(
                title = "Automatic backup",
                subtitle = if (backupDirUri.isNullOrBlank()) "Automatically backs up settings and playlists when app opens" else "Auto-backup saved to $folderName",
                checked = isAutoBackup,
            ) { enabled ->
                if (enabled && backupDirUri.isNullOrBlank()) {
                    dirPickerLauncher.launch(null)
                } else {
                    isAutoBackup = enabled
                    BackupPref.setAutoBackupEnabled(context, enabled)
                    if (enabled) {
                        scope.launch { BackupHelper.performAutoBackup(context) }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("Account")
            Text(
                text = "Log out",
                color = Color(0xFFE57373),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        com.music.spotui.data.api.SpotifySession.setSpDc(context, "")
                        com.music.spotui.data.api.Api.HomeCache.clear()
                        navController.navigate(com.music.spotui.ui.navigation.Routes.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                    .padding(vertical = 14.dp)
            )
            Spacer(Modifier.height(40.dp))
        }

        if (showDefaultGuide) {
            DefaultAppPrompt(
                forceShow = true,
                onDismiss = {
                    showDefaultGuide = false
                    isDefaultLinkHandler = DefaultLinkHelper.isAppDefaultLinkHandler(context)
                }
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = AppPalette,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color(0xFFB3B3B3), fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AppPalette,
                uncheckedThumbColor = Color(0xFFB3B3B3),
                uncheckedTrackColor = Color(0xFF333333),
            ),
        )
    }
}

@Composable
private fun QualityPicker(
    title: String,
    selected: StreamQuality,
    showFlacWarning: Boolean = false,
    onSelect: (StreamQuality) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        StreamQuality.values().forEach { q ->
            val isSel = q == selected
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelect(q) }
                        .background(if (isSel) Color(0xFF1A1A20) else Color.Transparent)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(q.label, color = Color.White, fontSize = 15.sp)
                        Text(q.detail, color = Color(0xFFB3B3B3), fontSize = 12.sp)
                    }
                    if (isSel) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Selected",
                            tint = AppPalette,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (isSel && q == StreamQuality.LOSSLESS && showFlacWarning) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Lossless streams are resolved per-session and aren't cached to disk, which may add a few seconds of loading time when playing music.",
                        color = Color(0xFFFFB74D),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x33FFB74D))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}
