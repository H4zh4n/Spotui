package com.music.spotui.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.os.Message
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.metrolist.spotify.Spotify
import com.metrolist.spotify.SpotifyAuth
import com.music.spotui.data.api.SpotifySession
import com.music.spotui.ui.navigation.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

private const val USER_AGENT_DESKTOP =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

private const val SPOTIFY_GREEN = 0xFF1ED760

/**
 * Spotify authentication screen.
 * Displays Spotify's official responsive web login within a configured Android WebView,
 * captures the `sp_dc` cookie across all Spotify domains, and provides a manual `sp_dc`
 * paste option as fallback.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SpotifyLoginScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

    var isLoadingPage by remember { mutableStateOf(true) }
    var loadingProgress by remember { mutableFloatStateOf(0f) }
    var hasPageError by remember { mutableStateOf(false) }
    var pageErrorMessage by remember { mutableStateOf("") }
    var showManualCookieDialog by remember { mutableStateOf(false) }

    val pageReady = remember { AtomicBoolean(false) }
    val tokenFetchStarted = remember { AtomicBoolean(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val navigateToHome: () -> Unit = {
        // Reload hidden playback WebView with new session
        com.music.spotui.di.SpotifyWebPlayer.refreshLogin(context)
        navController.navigate(Routes.Home.route) {
            popUpTo(Routes.Login.route) { inclusive = true }
        }
    }

    // Enable WebView debugging for dev inspection
    LaunchedEffect(Unit) {
        WebView.setWebContentsDebuggingEnabled(true)
    }

    // Poll for the sp_dc cookie across Spotify domains
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            if (tokenFetchStarted.get()) continue
            val spDc = extractCookie("sp_dc")
            val spKey = extractCookie("sp_key") ?: ""
            if (!spDc.isNullOrBlank() && tokenFetchStarted.compareAndSet(false, true)) {
                finishLogin(
                    spDc = spDc,
                    spKey = spKey,
                    view = webViewRef,
                    activity = context as Activity,
                    scope = scope,
                    setProcessing = { isProcessing = it },
                    setStatus = { statusMessage = it },
                    setError = { hasError = it },
                    tokenFetchStarted = tokenFetchStarted,
                    onSuccess = navigateToHome,
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Main Spotify Web Login View
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = 48.dp),
            factory = { ctx ->
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)

                WebView(ctx).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    webViewRef = this
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    setBackgroundColor(android.graphics.Color.BLACK)

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        javaScriptCanOpenWindowsAutomatically = true
                        @Suppress("DEPRECATION")
                        setSupportMultipleWindows(false)
                        cacheMode = WebSettings.LOAD_DEFAULT
                        userAgentString = USER_AGENT_DESKTOP
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            loadingProgress = newProgress / 100f
                            if (newProgress >= 100) {
                                isLoadingPage = false
                            }
                        }

                        override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                            Timber.d("SpotifyLogin console: ${m.message()} @${m.sourceId()}:${m.lineNumber()}")
                            return true
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            pageReady.set(false)
                            isLoadingPage = true
                            hasPageError = false
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            pageReady.set(true)
                            isLoadingPage = false
                        }

                        override fun onReceivedSslError(
                            view: WebView?,
                            handler: android.webkit.SslErrorHandler?,
                            error: android.net.http.SslError?,
                        ) {
                            Timber.w("SpotifyLogin onReceivedSslError: $error")
                            handler?.proceed()
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?,
                        ) {
                            if (request?.isForMainFrame == true) {
                                val desc = error?.description?.toString() ?: "Connection error"
                                Timber.w("SpotifyLogin WebView error: $desc on ${request.url}")
                                pageErrorMessage = desc
                                hasPageError = true
                            }
                        }

                        @Suppress("OVERRIDE_DEPRECATION")
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            return false
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            return false
                        }
                    }

                    loadUrl(SpotifyAuth.LOGIN_URL)
                }
            },
        )

        // Error / Retry view if page failed to load
        if (hasPageError && !pageReady.get()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Unable to load Spotify login page",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                    )
                    if (pageErrorMessage.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = pageErrorMessage,
                            color = Color(0xFFB3B3B3),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = {
                            hasPageError = false
                            isLoadingPage = true
                            webViewRef?.loadUrl(SpotifyAuth.LOGIN_URL)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(SPOTIFY_GREEN),
                            contentColor = Color.Black,
                        ),
                        shape = RoundedCornerShape(50),
                    ) {
                        Text("Retry", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    TextButton(
                        onClick = { showManualCookieDialog = true },
                    ) {
                        Text("Or Log In with Cookie (sp_dc)", color = Color.White)
                    }
                }
            }
        }

        // Top App Bar with navigation, title/status, refresh, and manual cookie entry
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .background(Color.Black),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    if (navController.previousBackStackEntry != null) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                            )
                        }
                    }
                    Text(
                        text = if (isProcessing) {
                            statusMessage.ifBlank { "Signing in…" }
                        } else {
                            "Log in to Spotify"
                        },
                        color = if (hasError) Color(0xFFE22134) else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            color = Color(SPOTIFY_GREEN),
                            strokeWidth = 2.dp,
                            modifier = Modifier
                                .size(20.dp)
                                .padding(end = 8.dp),
                        )
                    } else {
                        IconButton(
                            onClick = { showManualCookieDialog = true },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "Manual cookie login",
                                tint = Color(0xFFB3B3B3),
                            )
                        }
                        IconButton(
                            onClick = {
                                hasPageError = false
                                isLoadingPage = true
                                webViewRef?.loadUrl(SpotifyAuth.LOGIN_URL)
                            },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reload page",
                                tint = Color(0xFFB3B3B3),
                            )
                        }
                    }
                }
            }

            // Visual loading progress bar
            if (isLoadingPage && !isProcessing) {
                LinearProgressIndicator(
                    progress = { loadingProgress },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = Color(SPOTIFY_GREEN),
                    trackColor = Color.Transparent,
                )
            }
        }

        // Manual sp_dc cookie entry dialog
        if (showManualCookieDialog) {
            ManualCookieDialog(
                onDismiss = { showManualCookieDialog = false },
                onSubmit = { pastedSpDc ->
                    showManualCookieDialog = false
                    if (pastedSpDc.isNotBlank()) {
                        tokenFetchStarted.set(true)
                        finishLogin(
                            spDc = pastedSpDc.trim(),
                            spKey = "",
                            view = webViewRef,
                            activity = context as Activity,
                            scope = scope,
                            setProcessing = { isProcessing = it },
                            setStatus = { statusMessage = it },
                            setError = { hasError = it },
                            tokenFetchStarted = tokenFetchStarted,
                            onSuccess = navigateToHome,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun ManualCookieDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    val context = LocalContext.current
    var spDcInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        title = {
            Text(
                "Manual Spotify Cookie Login",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
        },
        text = {
            Column {
                Text(
                    "Paste your Spotify 'sp_dc' cookie value from your web browser (inspect cookies on open.spotify.com), or open Spotify in your browser.",
                    color = Color(0xFFB3B3B3),
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        val browserIntent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(SpotifyAuth.LOGIN_URL)
                        )
                        context.startActivity(browserIntent)
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open Spotify in Browser", color = Color.White, fontSize = 13.sp)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = spDcInput,
                    onValueChange = { spDcInput = it },
                    label = { Text("sp_dc cookie value", color = Color(0xFF8A8A8A)) },
                    placeholder = { Text("AQB...", color = Color(0xFF555555)) },
                    singleLine = false,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(SPOTIFY_GREEN),
                        focusedBorderColor = Color(SPOTIFY_GREEN),
                        unfocusedBorderColor = Color(0xFF727272),
                        focusedContainerColor = Color(0xFF121212),
                        unfocusedContainerColor = Color(0xFF121212),
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(spDcInput) },
                enabled = spDcInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(SPOTIFY_GREEN),
                    contentColor = Color.Black,
                    disabledContainerColor = Color(0xFF12863B),
                ),
            ) {
                Text("Log In", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFFB3B3B3))
            }
        },
    )
}

/**
 * Searches the cookie jar across all Spotify domains for a cookie by [name].
 */
private fun extractCookie(name: String): String? {
    val cookieManager = CookieManager.getInstance()
    val domains = listOf(
        "https://open.spotify.com",
        "https://accounts.spotify.com",
        "https://spotify.com",
    )
    for (domain in domains) {
        val allCookies = cookieManager.getCookie(domain) ?: continue
        val match = allCookies.split(";")
            .mapNotNull {
                val parts = it.trim().split("=", limit = 2)
                if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
            }
            .firstOrNull { it.first == name && it.second.isNotBlank() }
            ?.second
        if (!match.isNullOrBlank()) {
            return match
        }
    }
    return null
}

private fun finishLogin(
    spDc: String,
    spKey: String,
    view: WebView?,
    activity: Activity,
    scope: kotlinx.coroutines.CoroutineScope,
    setProcessing: (Boolean) -> Unit,
    setStatus: (String) -> Unit,
    setError: (Boolean) -> Unit,
    tokenFetchStarted: AtomicBoolean,
    onSuccess: () -> Unit,
) {
    if (spDc.isBlank()) {
        setProcessing(true)
        setError(true)
        setStatus("Couldn't read login cookie. Make sure you completed the Spotify login, then try again.")
        tokenFetchStarted.set(false)
        return
    }

    setProcessing(true)
    setError(false)
    setStatus("Connecting…")
    view?.stopLoading()
    view?.loadUrl("about:blank")

    scope.launch(Dispatchers.IO) {
        SpotifySession.setSpDc(activity, spDc)
        var lastError: Throwable? = null
        // The community TOTP/gist fetch is occasionally flaky — retry a couple times.
        repeat(3) { attempt ->
            val result = SpotifyAuth.fetchAccessToken(spDc, spKey)
            result.onSuccess { token ->
                Spotify.accessToken = token.accessToken
                withContext(Dispatchers.Main) { setStatus("Success!") }
                delay(300)
                withContext(Dispatchers.Main) { onSuccess() }
                return@launch
            }.onFailure { e ->
                lastError = e
                Timber.e(e, "Spotify token fetch failed (attempt ${attempt + 1})")
                if (attempt < 2) delay(800)
            }
        }
        withContext(Dispatchers.Main) {
            setStatus("Login failed: ${lastError?.message ?: "unknown error"}")
            setError(true)
        }
        tokenFetchStarted.set(false)
    }
}
