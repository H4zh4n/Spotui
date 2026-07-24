package com.music.spotui.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.music.spotui.util.DefaultLinkHelper
import com.music.spotui.util.HandlerAppInfo

private val SpotifyGreen = Color(0xFF1DB954)
private val SpotifyDarkBg = Color(0xFF121212)
private val SpotifyCardBg = Color(0xFF181818)
private val SpotifySubtleBorder = Color(0xFF282828)
private val SpotifyTextSecondary = Color(0xFFA7A7A7)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultAppPrompt(
    forceShow: Boolean = false,
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    var showDialog by remember(forceShow) {
        mutableStateOf(forceShow || DefaultLinkHelper.shouldShowPrompt(context))
    }
    var currentHandlerApp by remember { mutableStateOf(DefaultLinkHelper.getCurrentDefaultHandlerApp(context)) }
    val officialSpotifyInstalled = remember(context) { DefaultLinkHelper.isOfficialSpotifyInstalled(context) }

    fun dismiss() {
        showDialog = false
        onDismiss()
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, forceShow) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (DefaultLinkHelper.isAppDefaultLinkHandler(context)) {
                    dismiss()
                } else {
                    val shouldShow = forceShow || DefaultLinkHelper.shouldShowPrompt(context)
                    showDialog = shouldShow
                    if (shouldShow) {
                        currentHandlerApp = DefaultLinkHelper.getCurrentDefaultHandlerApp(context)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (!showDialog) return

    val handlerApp = currentHandlerApp

    // Official Spotify app always takes precedence as the primary target for clearing defaults
    val conflictingAppLabel = when {
        officialSpotifyInstalled -> "Official Spotify"
        handlerApp != null -> handlerApp.label
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> "Official Spotify"
        else -> null
    }

    val conflictingAppPackage = when {
        officialSpotifyInstalled -> "com.spotify.music"
        handlerApp != null -> handlerApp.packageName
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> "com.spotify.music"
        else -> null
    }

    BasicAlertDialog(
        onDismissRequest = { dismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, SpotifySubtleBorder, RoundedCornerShape(24.dp)),
            color = SpotifyDarkBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon Badge
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(SpotifyGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Header Title
                Text(
                    text = "Open Links in Spotui",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Set Spotui as default to play Spotify links directly.",
                    fontSize = 14.sp,
                    color = SpotifyTextSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Supported Domain Chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DomainChip(domain = "open.spotify.com")
                    DomainChip(domain = "spotify.link")
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (conflictingAppPackage != null && conflictingAppLabel != null) {
                    // Warning Banner explaining Android's lock restriction
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF2E2200))
                            .border(1.dp, Color(0xFFFFB300), RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFFB300),
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Android blocks adding links to Spotui until you clear defaults from $conflictingAppLabel first in Step 1.",
                                fontSize = 13.sp,
                                color = Color(0xFFFFE082),
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Step 1: Clear defaults from the conflicting app (Official Spotify, Chrome, etc.)
                    StepCard(
                        stepNumber = "1",
                        title = "STEP 1 (REQUIRED): Clear $conflictingAppLabel Link Defaults",
                        description = "Tap below to open $conflictingAppLabel's 'Open by default' screen. Turn OFF 'Open supported links' (or select 'In browser' / 'Clear defaults').",
                        buttonText = "1. Open $conflictingAppLabel Link Settings",
                        isPrimary = false,
                        onClick = { DefaultLinkHelper.openAppDetailsSettings(context, conflictingAppPackage) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Step 2: Enable Spotui Default Card
                    StepCard(
                        stepNumber = "2",
                        title = "STEP 2: Enable default links for Spotui",
                        description = "After completing Step 1, tap below to open Spotui settings -> Tap 'Open by default' -> Tap '+ Add link' -> Check open.spotify.com.",
                        buttonText = "2. Enable Defaults for Spotui",
                        isPrimary = true,
                        onClick = { DefaultLinkHelper.openSpotuiDefaultSettings(context) }
                    )
                } else {
                    // Single Step Card for when no conflicting app is installed
                    StepCard(
                        stepNumber = "1",
                        title = "Set Spotui as default handler",
                        description = "Tap below to open Spotui settings -> Tap 'Open by default' -> Tap '+ Add link' -> Check open.spotify.com.",
                        buttonText = "Enable Defaults for Spotui",
                        isPrimary = true,
                        onClick = { DefaultLinkHelper.openSpotuiDefaultSettings(context) }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Bottom Dismiss / Don't Ask Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            DefaultLinkHelper.setDontShowAgain(context)
                            dismiss()
                        }
                    ) {
                        Text(
                            text = "Don't ask again",
                            color = SpotifyTextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    TextButton(
                        onClick = { dismiss() }
                    ) {
                        Text(
                            text = "Later",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DomainChip(domain: String) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(SpotifyCardBg)
            .border(1.dp, SpotifySubtleBorder, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = SpotifyGreen,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = domain,
                fontSize = 12.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun StepCard(
    stepNumber: String,
    title: String,
    description: String,
    buttonText: String,
    isPrimary: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SpotifyCardBg)
            .border(
                1.dp,
                if (isPrimary) SpotifyGreen.copy(alpha = 0.6f) else SpotifySubtleBorder,
                RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Step Badge
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isPrimary) SpotifyGreen else Color(0xFF282828)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stepNumber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPrimary) Color.Black else Color.White
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                fontSize = 13.sp,
                color = SpotifyTextSecondary,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPrimary) SpotifyGreen else Color(0xFF282828)
                ),
                shape = CircleShape
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = buttonText,
                        color = if (isPrimary) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        tint = if (isPrimary) Color.Black else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
