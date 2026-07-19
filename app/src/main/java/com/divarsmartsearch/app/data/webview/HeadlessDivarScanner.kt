package com.divarsmartsearch.app.data.webview

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.divarsmartsearch.app.presentation.screens.webview.JsExtractionScripts
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Runs the exact same extraction logic as [com.divarsmartsearch.app.presentation.screens.webview.DivarWebViewScreen],
 * but against a WebView that is never attached to any visible UI and
 * scrolls itself — this is what lets a saved search be checked
 * automatically from [com.divarsmartsearch.app.service.BackgroundScanService]
 * without the person ever having to open the app and scroll a page by hand.
 *
 * Must be driven from the main thread (WebView requirement); callers on a
 * background dispatcher are automatically switched to Dispatchers.Main.
 */
@Singleton
class HeadlessDivarScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Loads [url], then repeatedly auto-scrolls and re-runs extraction
     * [scrollCycles] times (waiting [scrollDelayMs] between each cycle so
     * Divar's own lazy-loading has time to render), then returns every
     * de-duplicated listing seen across all cycles.
     *
     * Best-effort and time-boxed: if the page never loads, or extraction
     * never fires, this returns an empty list after [overallTimeoutMs]
     * instead of hanging the background scan forever.
     */
    suspend fun scan(
        url: String,
        scrollCycles: Int = 10,
        scrollDelayMs: Long = 1500L,
        overallTimeoutMs: Long = 90_000L,
    ): List<ExtractedListing> = withContext(Dispatchers.Main) {
        withTimeoutOrNull(overallTimeoutMs) { runScan(url, scrollCycles, scrollDelayMs) } ?: emptyList()
    }

    private suspend fun runScan(
        url: String,
        scrollCycles: Int,
        scrollDelayMs: Long,
    ): List<ExtractedListing> = suspendCancellableCoroutine { continuation ->
        val handler = Handler(Looper.getMainLooper())
        val collected = LinkedHashMap<String, ExtractedListing>()
        var webView: WebView? = null
        var finished = false

        fun finish() {
            if (finished) return
            finished = true
            handler.removeCallbacksAndMessages(null)
            val toDestroy = webView
            webView = null
            // Destroying immediately from inside a WebView callback can
            // crash on some OEM WebView builds, so post it instead.
            handler.post {
                try {
                    toDestroy?.stopLoading()
                    toDestroy?.destroy()
                } catch (e: Exception) {
                    // Nothing more to do; the WebView is being torn down anyway.
                }
            }
            if (continuation.isActive) continuation.resume(collected.values.toList())
        }

        try {
            webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true

                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onListingsExtracted(rawJson: String) {
                            try {
                                val items = json.decodeFromString<List<ExtractedListing>>(rawJson)
                                for (item in items) collected[item.divarToken] = item
                            } catch (e: Exception) {
                                // Malformed batch from the page; keep whatever we already have.
                            }
                        }
                    },
                    "AndroidBridge",
                )

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                        super.onPageFinished(view, loadedUrl)
                        view?.evaluateJavascript(JsExtractionScripts.EXTRACTION_SCRIPT, null)
                        runScrollCycle(view, remainingCycles = scrollCycles, delayMs = scrollDelayMs, handler = handler) {
                            finish()
                        }
                    }
                }

                loadUrl(url)
            }
        } catch (e: Exception) {
            finish()
        }

        continuation.invokeOnCancellation {
            try {
                webView?.stopLoading()
                webView?.destroy()
            } catch (ignored: Exception) {
            }
        }
    }

    /**
     * Loads a single listing's detail page in its own headless WebView and
     * returns its real title/description/phone straight from the rendered
     * DOM (via [JsExtractionScripts.EXTRACTION_SCRIPT]'s detail-page branch).
     *
     * Bug fix: this replaces an earlier plain-HTTP (OkHttp + regex over raw
     * HTML) detail fetch. Divar's detail page fills in the actual
     * description and contact number with its own JavaScript after the
     * initial page load, so a request without a real JS engine only ever
     * saw the empty page shell and got back nulls. Ingestion then had
     * nothing to filter against except the short (and sometimes
     * over-broad) list-card preview text, which is what let the
     * "دفتر"/"مشاور"/"املاک" hard-exclude filters wrongly catch genuine,
     * non-agency listings and send them to "رد شد" instead of "نتیجه". A
     * real WebView renders JavaScript exactly like the in-app browser tab
     * does, so this sees the same content a person actually would.
     *
     * One extraction right after the page reports finished, plus one more
     * after [extraRenderDelayMs] — whichever arrives first resolves this
     * call — covers content that hydrates a moment after `onPageFinished`.
     */
    suspend fun fetchDetail(
        url: String,
        extraRenderDelayMs: Long = 1500L,
        overallTimeoutMs: Long = 25_000L,
    ): ExtractedListing? = withContext(Dispatchers.Main) {
        withTimeoutOrNull(overallTimeoutMs) { runDetailFetch(url, extraRenderDelayMs) }
    }

    private suspend fun runDetailFetch(
        url: String,
        extraRenderDelayMs: Long,
    ): ExtractedListing? = suspendCancellableCoroutine { continuation ->
        val handler = Handler(Looper.getMainLooper())
        var webView: WebView? = null
        var finished = false

        fun finish(result: ExtractedListing?) {
            if (finished) return
            finished = true
            handler.removeCallbacksAndMessages(null)
            val toDestroy = webView
            webView = null
            handler.post {
                try {
                    toDestroy?.stopLoading()
                    toDestroy?.destroy()
                } catch (e: Exception) {
                    // Nothing more to do; the WebView is being torn down anyway.
                }
            }
            if (continuation.isActive) continuation.resume(result)
        }

        try {
            webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true

                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onListingsExtracted(rawJson: String) {
                            try {
                                val items = json.decodeFromString<List<ExtractedListing>>(rawJson)
                                items.firstOrNull()?.let { finish(it) }
                            } catch (e: Exception) {
                                // Malformed payload from this pass; the second, delayed
                                // extraction (or the timeout) will still resolve the call.
                            }
                        }
                    },
                    "AndroidBridge",
                )

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                        super.onPageFinished(view, loadedUrl)
                        view?.evaluateJavascript(JsExtractionScripts.EXTRACTION_SCRIPT, null)
                        handler.postDelayed(
                            { view?.evaluateJavascript(JsExtractionScripts.EXTRACTION_SCRIPT, null) },
                            extraRenderDelayMs,
                        )
                    }
                }

                loadUrl(url)
            }
        } catch (e: Exception) {
            finish(null)
        }

        continuation.invokeOnCancellation {
            try {
                webView?.stopLoading()
                webView?.destroy()
            } catch (ignored: Exception) {
            }
        }
    }

    /** Recursively scrolls, waits, and re-extracts, counting down until [remainingCycles] hits zero. */
    private fun runScrollCycle(
        view: WebView?,
        remainingCycles: Int,
        delayMs: Long,
        handler: Handler,
        onDone: () -> Unit,
    ) {
        if (view == null || remainingCycles <= 0) {
            onDone()
            return
        }
        handler.postDelayed(
            {
                view.evaluateJavascript(JsExtractionScripts.AUTO_SCROLL_SCRIPT, null)
                handler.postDelayed(
                    {
                        view.evaluateJavascript(JsExtractionScripts.EXTRACTION_SCRIPT, null)
                        runScrollCycle(view, remainingCycles - 1, delayMs, handler, onDone)
                    },
                    delayMs / 2,
                )
            },
            delayMs / 2,
        )
    }
}
