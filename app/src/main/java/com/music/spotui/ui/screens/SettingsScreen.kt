package com.music.spotui.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import com.music.spotui.R
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.style.TextOverflow
import com.music.spotui.data.preferences.AudioProviderOrderItem
import com.music.spotui.data.preferences.getAudioProviderOrder
import com.music.spotui.data.preferences.setAudioProviderOrder
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalUriHandler
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
    var showProviderStatusDialog by remember { mutableStateOf(false) }
    var providerStatuses by remember { mutableStateOf(emptyList<com.metrolist.spotify.SpotiFlac.ProviderStatus>()) }
    var isRefreshingStatuses by remember { mutableStateOf(false) }
    var providerOrder by remember { mutableStateOf(getAudioProviderOrder(context)) }
    var showProviderOrderDialog by remember { mutableStateOf(false) }

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
        var showDevicesSheet by remember { mutableStateOf(false) }

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
            SectionTitle("Devices & Bluetooth")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { showDevicesSheet = true }
                    .background(Color(0xFF1A1A20))
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Audio Output Devices", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        com.music.spotui.ui.utils.AudioDeviceHelper.getCurrentAudioRouteName(context),
                        color = Color(0xFF1ED760),
                        fontSize = 12.sp,
                    )
                }
                Icon(
                    painter = painterResource(id = R.drawable.ic_devices),
                    contentDescription = "Devices",
                    tint = Color(0xFF1ED760),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

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
                onDeezerLogin = { navController.navigate(com.music.spotui.ui.navigation.Routes.DeezerLogin.route) }
            ) { wifiQ = it; setWifiQuality(context, it) }

            QualityPicker(
                title = "Streaming over cellular",
                selected = cellQ,
                showFlacWarning = cellQ == StreamQuality.LOSSLESS,
                onDeezerLogin = { navController.navigate(com.music.spotui.ui.navigation.Routes.DeezerLogin.route) }
            ) { cellQ = it; setCellularQuality(context, it) }

            QualityPicker(
                title = "Download quality",
                selected = dlQ,
                showFlacWarning = dlQ == StreamQuality.LOSSLESS,
                onDeezerLogin = { navController.navigate(com.music.spotui.ui.navigation.Routes.DeezerLogin.route) }
            ) { dlQ = it; setDownloadQuality(context, it) }

            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        com.music.spotui.di.SongPlayer.clearCaches(context)
                        android.widget.Toast.makeText(context, "Stream cache cleared", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    .background(Color(0xFF1E1E24))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Clear Audio Stream Cache", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Unlocks all cached streams and forces re-resolution", color = Color.Gray, fontSize = 11.sp)
                }
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Clear cache",
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }

            var losslessStatusSummary by remember { mutableStateOf("Checking lossless mirrors…") }
            LaunchedEffect(Unit) {
                providerStatuses = com.metrolist.spotify.SpotiFlac.getProviderStatuses()
                val upCount = providerStatuses.count { it.isUp && !it.isCooldown }
                losslessStatusSummary = "$upCount/${providerStatuses.size} online • Tap to inspect providers"
            }

            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        scope.launch {
                            isRefreshingStatuses = true
                            providerStatuses = com.metrolist.spotify.SpotiFlac.getProviderStatuses()
                            val upCount = providerStatuses.count { it.isUp && !it.isCooldown }
                            losslessStatusSummary = "$upCount/${providerStatuses.size} online • Tap to inspect providers"
                            isRefreshingStatuses = false
                            showProviderStatusDialog = true
                        }
                    }
                    .background(Color(0xFF1E1E24))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Lossless Provider Status", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(losslessStatusSummary, color = Color(0xFFB3B3B3), fontSize = 12.sp)
                }
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Inspect Status",
                    tint = AppPalette,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { showProviderOrderDialog = true }
                    .background(Color(0xFF1E1E24))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Audio Provider Priority", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(providerOrder.joinToString(" → ") { it.displayName }, color = Color(0xFFB3B3B3), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(
                    imageVector = Icons.Filled.SwapVert,
                    contentDescription = "Priority Order",
                    tint = AppPalette,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("Deezer (preferred source)")
            var deezerEnabled by remember { mutableStateOf(com.music.spotui.data.preferences.isDeezerEnabled(context)) }
            val deezerConnected = com.music.spotui.data.preferences.getDeezerArl(context) != null
            val deezerTier = com.music.spotui.data.preferences.getDeezerTier(context)

            SettingsSwitchRow(
                title = "Use Deezer",
                subtitle = "Stream from Deezer first, fall back to YouTube",
                checked = deezerEnabled,
            ) {
                deezerEnabled = it
                com.music.spotui.data.preferences.setDeezerEnabled(context, it)
                com.music.spotui.di.SongPlayer.deezerEnabled = it
            }
            Text(
                text = if (deezerConnected) {
                    "Connected" + if (deezerTier.isNotBlank()) " — $deezerTier" else ""
                } else "Not connected",
                color = Color(0xFFB3B3B3),
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
            Text(
                text = if (deezerConnected) "Reconnect / switch account" else "Log in to Deezer",
                color = AppPalette,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        navController.navigate(com.music.spotui.ui.navigation.Routes.DeezerLogin.route)
                    }
                    .padding(vertical = 14.dp),
            )
            if (deezerConnected) {
                Text(
                    text = "Disconnect Deezer",
                    color = Color(0xFFE57373),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            com.music.spotui.data.preferences.clearDeezer(context)
                            navController.navigate(com.music.spotui.ui.navigation.Routes.Settings.route) {
                                popUpTo(com.music.spotui.ui.navigation.Routes.Settings.route) { inclusive = true }
                            }
                        }
                        .padding(vertical = 12.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("SpotiFLAC (experimental)")
            val sfConnected = com.music.spotui.data.preferences.hasSpotiflacSession(context)
            Text(
                text = if (sfConnected) "Verified — signed session active" else "Not verified — tap below to solve Turnstile check",
                color = Color(0xFFB3B3B3),
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
            Text(
                text = if (sfConnected) "Re-verify SpotiFLAC" else "Verify SpotiFLAC",
                color = Color(0xFF00C7B7),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        navController.navigate(com.music.spotui.ui.navigation.Routes.SpotiflacVerify.route)
                    }
                    .padding(vertical = 14.dp),
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
            SectionTitle("Troubleshooting")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            com.music.spotui.di.SongPlayer.clearCaches(context)
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                android.widget.Toast.makeText(
                                    context,
                                    "YouTube session & stream caches reset",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    .background(Color(0xFF1A1A20))
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Reset YouTube & Bot Session", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Clears session tokens, visitor ID, PoToken generator, and resolved stream caches",
                        color = Color(0xFFB3B3B3),
                        fontSize = 12.sp,
                    )
                }
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Reset Session",
                    tint = AppPalette,
                    modifier = Modifier.size(20.dp)
                )
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
            Spacer(Modifier.height(24.dp))
            val uriHandler = LocalUriHandler.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Maintained with ♥ by ",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Hazhan Salih",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://github.com/H4zh4n/Spotui/")
                    }
                )
            }
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

        if (showDevicesSheet) {
            com.music.spotui.ui.components.DevicesSheet(
                context = context,
                onDismiss = { showDevicesSheet = false }
            )
        }

        if (showProviderStatusDialog) {
            AlertDialog(
                onDismissRequest = { showProviderStatusDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Lossless Provider Status", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (isRefreshingStatuses) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = AppPalette)
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable {
                                        scope.launch {
                                            isRefreshingStatuses = true
                                            com.metrolist.spotify.SpotiFlac.clearStatusCache()
                                            providerStatuses = com.metrolist.spotify.SpotiFlac.getProviderStatuses()
                                            isRefreshingStatuses = false
                                        }
                                    }
                            )
                        }
                    }
                },
                text = {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            "Real-time availability of lossless audio mirrors (Tidal, Qobuz, Amazon, Deezer, Monochrome). Playback automatically resolves from the fastest available online provider.",
                            color = Color(0xFFB3B3B3),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        providerStatuses.forEach { status ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1A1A20))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(status.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(status.detail, color = Color(0xFF999999), fontSize = 11.sp)
                                }
                                Spacer(Modifier.width(6.dp))
                                val (statusText, statusBg, statusFg) = when {
                                    status.isCooldown -> Triple("Cooldown (${status.cooldownRemainingSec}s)", Color(0x33FFB74D), Color(0xFFFFB74D))
                                    status.isUp -> Triple("Online", Color(0x3381C784), Color(0xFF81C784))
                                    else -> Triple("Offline", Color(0x33E57373), Color(0xFFE57373))
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(statusBg)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(statusText, color = statusFg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showProviderStatusDialog = false }) {
                        Text("Close", color = AppPalette)
                    }
                },
                containerColor = Color(0xFF141418),
                titleContentColor = Color.White,
                textContentColor = Color.White,
            )
        }

        if (showProviderOrderDialog) {
            AlertDialog(
                onDismissRequest = { showProviderOrderDialog = false },
                title = { Text("Audio Provider Priority", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Higher providers are attempted first when resolving high-res & lossless streams:", color = Color.Gray, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        providerOrder.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${index + 1}. ${item.displayName}", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                if (index > 0) {
                                    IconButton(onClick = {
                                        val mutable = providerOrder.toMutableList()
                                        val temp = mutable[index]
                                        mutable[index] = mutable[index - 1]
                                        mutable[index - 1] = temp
                                        providerOrder = mutable
                                        setAudioProviderOrder(context, mutable)
                                    }) {
                                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up", tint = AppPalette)
                                    }
                                }
                                if (index < providerOrder.size - 1) {
                                    IconButton(onClick = {
                                        val mutable = providerOrder.toMutableList()
                                        val temp = mutable[index]
                                        mutable[index] = mutable[index + 1]
                                        mutable[index + 1] = temp
                                        providerOrder = mutable
                                        setAudioProviderOrder(context, mutable)
                                    }) {
                                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down", tint = AppPalette)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showProviderOrderDialog = false }) {
                        Text("Done", color = AppPalette)
                    }
                },
                containerColor = Color(0xFF141418),
                titleContentColor = Color.White,
                textContentColor = Color.White,
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
    onDeezerLogin: (() -> Unit)? = null,
    onSelect: (StreamQuality) -> Unit
) {
    val context = LocalContext.current
    val deezerConnected = remember(selected) { com.music.spotui.data.preferences.getDeezerArl(context) != null }

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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x33FFB74D))
                            .border(1.dp, Color(0x66FFB74D), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "⚠️ Deezer Login Recommended for Lossless",
                            color = Color(0xFFFFB74D),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Deezer is the most reliable & successful provider for Lossless FLAC playback and downloads. Please log in to Deezer for optimal Lossless availability.",
                            color = Color(0xFFFFE0B2),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        if (deezerConnected) {
                            Text(
                                text = "✓ Deezer account connected (Lossless ready)",
                                color = Color(0xFF1ED760),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else if (onDeezerLogin != null) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(AppPalette)
                                    .clickable { onDeezerLogin() }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Log in to Deezer now",
                                    color = Color.Black,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}
