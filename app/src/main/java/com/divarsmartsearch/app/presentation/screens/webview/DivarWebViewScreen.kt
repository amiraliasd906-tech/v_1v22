package com.divarsmartsearch.app.presentation.screens.webview

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.divarsmartsearch.app.presentation.components.LoadingState

private const val EXTRACTION_INTERVAL_MS = 3000L

// How often the lightweight, extraction-independent listing counter
// refreshes (see JsExtractionScripts.COUNT_SCRIPT). Runs on its own timer,
// separate from the bot's scroll+extract cycle, so the on-screen count
// keeps updating even while the bot is paused.
private const val COUNT_REFRESH_INTERVAL_MS = 5000L

// How long to wait after triggering auto-scroll before reading the page.
// Divar's infinite-scroll loads the next batch of cards asynchronously,
// so extraction has to wait for that network round-trip to finish
// rendering rather than reading the DOM immediately after scrolling.
private const val SCROLL_RENDER_DELAY_MS = 1200L

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DivarWebViewScreen(
    searchId: Int,
    viewModel: DivarWebViewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val onListingsExtracted = rememberUpdatedState { json: String -> viewModel.onListingsExtracted(json) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Live count of distinct listings currently rendered on the page,
    // refreshed every COUNT_REFRESH_INTERVAL_MS independent of the bot's
    // on/off state and of the (heavier, detail-fetching) extraction cycle.
    var pageListingCount by remember { mutableStateOf(0) }
    val onListingCount = rememberUpdatedState { count: Int -> pageListingCount = count }

    // Lets the user pause the bot (auto-scroll + extraction loop) so they can
    // manually edit something on the Divar page (e.g. price) without the bot
    // interfering, then resume it afterwards.
    var isBotActive by remember { mutableStateOf(true) }
    val isBotActiveState = rememberUpdatedState(isBotActive)

    LaunchedEffect(searchId) { viewModel.load(searchId) }
    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    // Divar's pages are a single-page app: content can change without a
    // full reload, so we periodically re-run extraction rather than
    // relying only on onPageFinished. Tied to this composable's
    // lifecycle so the loop stops when the user leaves the screen.
    //
    // Bug fix: this used to call AUTO_SCROLL_SCRIPT and then
    // EXTRACTION_SCRIPT back-to-back with no delay between them.
    // Scrolling to the bottom only *triggers* Divar's infinite-scroll
    // loading -- fetching and rendering the next batch of cards is an
    // async network round-trip -- so extraction was always reading the
    // page before the newly-loaded cards existed in the DOM. From the
    // outside this looked exactly like "the page keeps auto-scrolling
    // but never finds anything new": every cycle just re-extracted the
    // same on-screen cards, forever reporting 0 new listings. The
    // headless background scanner (HeadlessDivarScanner.runScrollCycle)
    // already waits between the scroll and the extraction for this
    // exact reason; this gives the foreground WebView the same delay.
    DisposableEffect(webViewRef) {
        val webView = webViewRef
        val handler = Handler(Looper.getMainLooper())
        var isActive = webView != null
        val runnable = object : Runnable {
            override fun run() {
                if (!isActive) return
                if (isBotActiveState.value) {
                    webView?.evaluateJavascript(JsExtractionScripts.AUTO_SCROLL_SCRIPT, null)
                    handler.postDelayed({
                        if (!isActive || !isBotActiveState.value) return@postDelayed
                        webView?.evaluateJavascript(JsExtractionScripts.EXTRACTION_SCRIPT, null)
                    }, SCROLL_RENDER_DELAY_MS)
                }
                handler.postDelayed(this, EXTRACTION_INTERVAL_MS)
            }
        }
        if (webView != null) handler.postDelayed(runnable, EXTRACTION_INTERVAL_MS)

        onDispose {
            isActive = false
            handler.removeCallbacksAndMessages(null)
        }
    }

    // Separate, always-on timer for the live listing count (see
    // COUNT_REFRESH_INTERVAL_MS) -- deliberately independent of
    // isBotActive so pausing the bot to edit something on the page
    // doesn't also freeze the counter at the top of the screen.
    DisposableEffect(webViewRef) {
        val webView = webViewRef
        val handler = Handler(Looper.getMainLooper())
        var isActive = webView != null
        val runnable = object : Runnable {
            override fun run() {
                if (!isActive) return
                webView?.evaluateJavascript(JsExtractionScripts.COUNT_SCRIPT, null)
                handler.postDelayed(this, COUNT_REFRESH_INTERVAL_MS)
            }
        }
        if (webView != null) {
            // Fire once immediately so the count isn't blank for the
            // first 5 seconds after the page loads.
            handler.post(runnable)
        }

        onDispose {
            isActive = false
            handler.removeCallbacksAndMessages(null)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (state.searchName.isNotBlank()) {
                                if (isBotActive) "در حال رصد: ${state.searchName}" else "متوقف: ${state.searchName}"
                            } else {
                                "دیوار"
                            }
                        )
                        Text(
                            text = "تعداد آگهی این صفحه: $pageListingCount",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { isBotActive = !isBotActive }) {
                        Icon(
                            imageVector = if (isBotActive) Icons.Outlined.PauseCircle else Icons.Outlined.PlayCircle,
                            contentDescription = if (isBotActive) "خاموش کردن بات" else "روشن کردن بات",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoadingSearch) {
            LoadingState(modifier = Modifier.fillMaxSize())
            return@Scaffold
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true

                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun onListingsExtracted(json: String) {
                                onListingsExtracted.value(json)
                            }

                            @JavascriptInterface
                            fun onListingCount(count: Int) {
                                onListingCount.value(count)
                            }
                        },
                        "AndroidBridge",
                    )

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (isBotActiveState.value) {
                                view?.evaluateJavascript(JsExtractionScripts.EXTRACTION_SCRIPT, null)
                            }
                            view?.evaluateJavascript(JsExtractionScripts.COUNT_SCRIPT, null)
                        }
                    }

                    loadUrl(state.startUrl)
                    webViewRef = this
                }
            },
        )
    }
}
