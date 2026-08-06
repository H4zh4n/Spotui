package com.metrolist.music.utils.potoken

import android.webkit.CookieManager
import com.metrolist.music.utils.cipher.CipherDeobfuscator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber

class PoTokenGenerator {
    private val TAG = "PoTokenGenerator"
    /** Max time to wait for WebView-based PoToken generation before giving up.
     *  In the background the WebView's evaluateJavascript() may never call back,
     *  so an indefinite wait would stall the entire stream resolution pipeline. */
    private companion object {
        const val POTOKEN_TIMEOUT_MS = 12_000L
    }

    private val webViewSupported by lazy { runCatching { CookieManager.getInstance() }.isSuccess }
    private var webViewBadImpl = false // whether the system has a bad WebView implementation

    private val webPoTokenGenLock = Mutex()
    private var webPoTokenSessionId: String? = null
    private var webPoTokenStreamingPot: String? = null
    private var webPoTokenGenerator: PoTokenWebView? = null

    fun getWebClientPoToken(videoId: String, sessionId: String): PoTokenResult? {
        Timber.tag(TAG).d("getWebClientPoToken called: videoId=$videoId, sessionId=$sessionId")
        Timber.tag(TAG).d("WebView state: supported=$webViewSupported, badImpl=$webViewBadImpl")
        if (!webViewSupported || webViewBadImpl) {
            Timber.tag(TAG).d("WebView not available: supported=$webViewSupported, badImpl=$webViewBadImpl")
            return null
        }

        return try {
            Timber.tag(TAG).d("Calling runBlocking to generate poToken...")
            runBlocking {
                withTimeout(POTOKEN_TIMEOUT_MS) {
                    getWebClientPoToken(videoId, sessionId, forceRecreate = false)
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "poToken generation exception: ${e.javaClass.simpleName}: ${e.message}")
            invalidateGenerator()
            when (e) {
                is BadWebViewException -> {
                    Timber.tag(TAG).e(e, "Could not obtain poToken because WebView is broken")
                    webViewBadImpl = true
                    null
                }
                is kotlinx.coroutines.TimeoutCancellationException -> {
                    Timber.tag(TAG).w("PoToken generation timed out for videoId=$videoId")
                    null
                }
                else -> null
            }
        }
    }

    fun reset() {
        runCatching {
            webPoTokenSessionId = null
            webPoTokenStreamingPot = null
            val gen = webPoTokenGenerator
            webPoTokenGenerator = null
            if (gen != null) {
                if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                    runCatching { gen.close() }
                } else {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        runCatching { gen.close() }
                    }
                }
            }
        }
    }

    private fun invalidateGenerator() {
        reset()
    }

    /**
     * @param forceRecreate whether to force the recreation of [webPoTokenGenerator], to be used in
     * case the current [webPoTokenGenerator] threw an error last time
     * [PoTokenWebView.generatePoToken] was called
     */
    private suspend fun getWebClientPoToken(videoId: String, sessionId: String, forceRecreate: Boolean): PoTokenResult {
        Timber.tag(TAG).d("Web poToken requested: videoId=$videoId, sessionId=$sessionId")

        val (poTokenGenerator, streamingPot, hasBeenRecreated) =
            webPoTokenGenLock.withLock {
                val shouldRecreate =
                    forceRecreate || webPoTokenGenerator == null || webPoTokenGenerator!!.isExpired || webPoTokenSessionId != sessionId

                if (shouldRecreate) {
                    Timber.tag(TAG).d("Creating new PoTokenWebView (forceRecreate=$forceRecreate)")
                    webPoTokenSessionId = sessionId

                    withContext(Dispatchers.Main) {
                        webPoTokenGenerator?.close()
                    }

                    // create a new webPoTokenGenerator
                    webPoTokenGenerator = PoTokenWebView.getNewPoTokenGenerator(CipherDeobfuscator.appContext)

                    // The streaming poToken needs to be generated exactly once before generating
                    // any other (player) tokens.
                    webPoTokenStreamingPot = webPoTokenGenerator!!.generatePoToken(webPoTokenSessionId!!)
                    Timber.tag(TAG).d("Streaming poToken generated for sessionId=${webPoTokenSessionId?.take(20)}...")
                }

                Triple(webPoTokenGenerator!!, webPoTokenStreamingPot!!, shouldRecreate)
            }

        val playerPot = try {
            poTokenGenerator.generatePoToken(videoId)
        } catch (throwable: Throwable) {
            if (hasBeenRecreated) {
                // the poTokenGenerator has just been recreated (and possibly this is already the
                // second time we try), so there is likely nothing we can do
                throw throwable
            } else {
                // retry, this time recreating the [webPoTokenGenerator] from scratch;
                // this might happen for example if the app goes in the background and the WebView
                // content is lost
                Timber.tag(TAG).e(throwable, "Failed to obtain poToken, retrying")
                return getWebClientPoToken(videoId = videoId, sessionId = sessionId, forceRecreate = true)
            }
        }

        Timber.tag(TAG).d("poToken generated successfully: player=${playerPot.take(20)}..., streaming=${streamingPot.take(20)}...")

        return PoTokenResult(playerPot, streamingPot)
    }
}
