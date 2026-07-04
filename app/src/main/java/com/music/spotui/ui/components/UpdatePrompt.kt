package com.music.spotui.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.music.spotui.data.update.UpdateChecker

/**
 * Launch-time update prompt: checks the GitHub release once per app start and,
 * when a newer build exists, offers Upgrade / Dismiss / Don't show again
 * ("don't show again" is per release — the next release prompts again).
 */
@Composable
fun UpdatePrompt() {
    val context = LocalContext.current
    var update by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }

    LaunchedEffect(Unit) {
        update = UpdateChecker.check(context)
    }

    val info = update ?: return
    AlertDialog(
        onDismissRequest = { update = null },
        title = { Text("Update available") },
        text = { Text("spotui ${info.version} is available. You're on an older version — upgrade to get the latest fixes and features.") },
        confirmButton = {
            TextButton(onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl)))
                }
                update = null
            }) { Text("Upgrade") }
        },
        dismissButton = {
            TextButton(onClick = {
                UpdateChecker.skipRelease(context, info)
                update = null
            }) { Text("Don't show again") }
            TextButton(onClick = { update = null }) { Text("Dismiss") }
        },
    )
}
