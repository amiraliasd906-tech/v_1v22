package com.divarsmartsearch.app.presentation.screens.webview

import android.annotation.SuppressLint
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** Divar's own search page for the city we start filter-picking from. */
private const val FILTER_PICKER_START_URL = "https://divar.ir/s/mashhad"

/**
 * A plain, fully-interactive WebView pointed at Divar's real search page.
 *
 * Unlike [DivarWebViewScreen] (which drives an auto-scroll + extraction loop
 * to passively scan an already-saved search), this screen does nothing on
 * its own: no auto-scroll, no JS extraction, no pause/resume bot toggle.
 * Every tap goes straight to the page, exactly like a normal browser, so the
 * user can freely tap through Divar's real filter UI (price, area,
 * neighborhood, etc.) themselves.
 *
 * A single button below the page reads whatever URL the WebView is
 * currently on -- which is Divar's own URL, already updated with whatever
 * filters the user picked -- and hands it back to the caller via [onDone].
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterPickerWebViewScreen(
    onDone: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(FILTER_PICKER_START_URL) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("انتخاب فیلتر از دیوار") })
        },
        bottomBar = {
            Button(
                onClick = { onDone(webViewRef?.url ?: currentUrl) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                Text(text = "  ثبت این لینک و بازگشت")
            }
        },
    ) { padding ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(padding),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true

                    // Without these, Divar's mobile layout renders as if on a
                    // desktop-width viewport, which shifts some tap targets
                    // (icon buttons in particular) away from where they're
                    // actually drawn.
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true

                    // Some icons on Divar's real page (e.g. "نزدیک من" on the
                    // map picker, opening a listing/photo in a new tab, the
                    // share sheet) rely on window.open()/target="_blank" or on
                    // browser permission prompts (geolocation). A bare
                    // WebViewClient has no way to satisfy those, so taps on
                    // those specific icons looked like nothing happened. A
                    // WebChromeClient is required to open same-window
                    // "popups" and to grant/deny permission prompts.
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    settings.setSupportMultipleWindows(true)

                    webChromeClient = object : WebChromeClient() {
                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: android.os.Message?,
                        ): Boolean {
                            // Instead of spawning a real second WebView, just
                            // navigate the same one to whatever URL the popup
                            // wanted to open -- keeps the user on one screen.
                            val transport = resultMsg?.obj as? WebView.WebViewTransport
                            val popup = WebView(context)
                            popup.webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    url: String?,
                                ): Boolean {
                                    if (url != null) view?.let { this@apply.loadUrl(url) }
                                    return true
                                }
                            }
                            transport?.webView = popup
                            resultMsg?.sendToTarget()
                            return true
                        }

                        override fun onGeolocationPermissionsShowPrompt(
                            origin: String?,
                            callback: GeolocationPermissions.Callback?,
                        ) {
                            callback?.invoke(origin, true, false)
                        }

                        override fun onPermissionRequest(request: PermissionRequest?) {
                            request?.grant(request.resources)
                        }
                    }

                    // No custom bridge, no auto-scroll, no extraction timer:
                    // this WebView just behaves like a normal browser so the
                    // user can tap anywhere on Divar's real filter UI.
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            currentUrl = url ?: currentUrl
                        }
                    }

                    loadUrl(FILTER_PICKER_START_URL)
                    webViewRef = this
                }
            },
        )
    }
}
