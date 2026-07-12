package com.music.spotui.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.music.spotui.data.update.UpdateChecker

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
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = Color(0xFF1A1A1A),
        titleContentColor = Color.White,
        title = {
            Text("Update available — ${info.version}")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (info.releaseBody.isNotBlank()) {
                    RenderMarkdown(info.releaseBody)
                } else {
                    Text(
                        "A new version of Spotui is available.",
                        color = Color(0xFFB3B3B3),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl))
                    )
                }
                update = null
            }) {
                Text("Update", color = Color(0xFF1DB954))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                UpdateChecker.skipRelease(context, info)
                update = null
            }) {
                Text("Don't show again", color = Color(0xFFB3B3B3))
            }
            TextButton(onClick = { update = null }) {
                Text("Dismiss", color = Color.White)
            }
        },
    )
}

private val HEADER_RE = Regex("""^(#{1,6})\s+(.*)""")

@Composable
private fun RenderMarkdown(markdown: String) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val linkColor = Color(0xFF1DB954)
    val headingColor = Color.White
    val bodyColor = Color(0xFFB3B3B3)

    val lines = markdown.lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        when {
            trimmed.isEmpty() -> {
                Spacer(modifier = Modifier.height(6.dp))
            }
            trimmed == "---" || trimmed == "***" || trimmed == "___"
                || (trimmed.length >= 3 && trimmed.all { it == '_' || it == '-' || it == '*' }) -> {
                HorizontalDivider(
                    color = Color(0xFF2A2A2A),
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            HEADER_RE.matches(trimmed) -> {
                val match = HEADER_RE.find(trimmed)!!
                val level = match.groupValues[1].length
                val (size, weight) = when (level) {
                    1 -> 18.sp to FontWeight.ExtraBold
                    2 -> 16.sp to FontWeight.Bold
                    3 -> 15.sp to FontWeight.SemiBold
                    4 -> 14.sp to FontWeight.SemiBold
                    5 -> 13.sp to FontWeight.SemiBold
                    else -> 13.sp to FontWeight.Bold
                }
                Text(
                    text = match.groupValues[2],
                    color = headingColor,
                    fontSize = size,
                    fontWeight = weight,
                    modifier = Modifier.padding(top = if (i > 0) 10.dp else 0.dp),
                )
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                val item = trimmed.removePrefix("- ").removePrefix("* ")
                MarkdownInline(
                    text = item,
                    prefix = "\u2022 ",
                    linkColor = linkColor,
                    bodyColor = bodyColor,
                    uriHandler = uriHandler,
                )
            }
            Regex("""^\d+\.\s""").containsMatchIn(trimmed) -> {
                val numMatch = Regex("""^(\d+\.\s)(.*)""").find(trimmed)!!
                MarkdownInline(
                    text = numMatch.groupValues[2],
                    prefix = numMatch.groupValues[1],
                    linkColor = linkColor,
                    bodyColor = bodyColor,
                    uriHandler = uriHandler,
                )
            }
            else -> {
                MarkdownInline(
                    text = trimmed,
                    prefix = "",
                    linkColor = linkColor,
                    bodyColor = bodyColor,
                    uriHandler = uriHandler,
                )
            }
        }
        i++
    }
}

@Composable
private fun MarkdownInline(
    text: String,
    prefix: String,
    linkColor: Color,
    bodyColor: Color,
    uriHandler: androidx.compose.ui.platform.UriHandler,
) {
    val patterns = listOf(
        Triple(Regex("""\*\*(.+?)\*\*"""), "bold", null as String?),
        Triple(Regex("""__(.+?)__"""), "underline", null),
        Triple(Regex("""_(.+?)_"""), "underline", null),
        Triple(Regex("""~~(.+?)~~"""), "strikethrough", null),
        Triple(Regex("""`([^`]+)`"""), "code", null),
        Triple(Regex("""\[([^\]]+)\]\(([^)]+)\)"""), "link", null),
    )

    val annotated = buildAnnotatedString {
        if (prefix.isNotEmpty()) {
            pushStyle(SpanStyle(color = bodyColor))
            append(prefix)
            pop()
        }

        data class Segment(val start: Int, val end: Int, val type: String, val content: String, val url: String? = null)

        val segments = mutableListOf<Segment>()
        for ((regex, type, _) in patterns) {
            for (m in regex.findAll(text)) {
                segments.add(
                    when (type) {
                        "link" -> Segment(m.range.first, m.range.last + 1, type, m.groupValues[1], m.groupValues[2])
                        else -> Segment(m.range.first, m.range.last + 1, type, m.groupValues[1])
                    }
                )
            }
        }

        val sorted = segments.sortedBy { it.start }.distinctBy { it.start }

        var cursor = 0
        for (seg in sorted) {
            if (seg.start < cursor) continue
            if (seg.start > cursor) {
                pushStyle(SpanStyle(color = bodyColor))
                append(text.substring(cursor, seg.start))
                pop()
            }
            when (seg.type) {
                "bold" -> {
                    pushStyle(SpanStyle(color = bodyColor, fontWeight = FontWeight.Bold))
                    append(seg.content)
                    pop()
                }
                "underline" -> {
                    pushStyle(SpanStyle(color = bodyColor, textDecoration = TextDecoration.Underline))
                    append(seg.content)
                    pop()
                }
                "strikethrough" -> {
                    pushStyle(SpanStyle(color = bodyColor, textDecoration = TextDecoration.LineThrough))
                    append(seg.content)
                    pop()
                }
                "code" -> {
                    pushStyle(SpanStyle(
                        color = Color(0xFFE0E0E0),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                    ))
                    append(seg.content)
                    pop()
                }
                "link" -> {
                    pushStringAnnotation(tag = "URL", annotation = seg.url ?: "")
                    pushStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                    append(seg.content)
                    pop()
                    pop()
                }
            }
            cursor = seg.end
        }

        if (cursor < text.length) {
            pushStyle(SpanStyle(color = bodyColor))
            append(text.substring(cursor))
            pop()
        }
    }

    ClickableText(
        text = annotated,
        style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        modifier = Modifier.padding(vertical = 2.dp),
        onClick = { offset ->
            annotated.getStringAnnotations("URL", offset, offset)
                .firstOrNull()?.let { uriHandler.openUri(it.item) }
        },
    )
}
