package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.BrowserTab
import com.example.data.model.ConsoleLogItem
import com.example.data.model.ExtensionEntity
import com.example.data.model.LogLevel
import com.example.engine.ExtensionInjector
import com.example.engine.WebExtensionBridge

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserWebView(
    tab: BrowserTab,
    enabledExtensions: List<ExtensionEntity>,
    onUpdateTabState: (url: String?, title: String?, isLoading: Boolean?, progress: Int?, canGoBack: Boolean?, canGoForward: Boolean?) -> Unit,
    onAddConsoleLog: (ConsoleLogItem) -> Unit,
    modifier: Modifier = Modifier,
    onWebViewCreated: (WebView) -> Unit = {}
) {
    val context = LocalContext.current
    val webExtensionBridge = remember {
        WebExtensionBridge(context) { logItem ->
            onAddConsoleLog(logItem)
        }
    }

    val webView = remember(tab.id) {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
            }

            addJavascriptInterface(webExtensionBridge, "ExtenNativeBridge")

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    onUpdateTabState(
                        null,
                        null,
                        newProgress < 100,
                        newProgress,
                        view?.canGoBack(),
                        view?.canGoForward()
                    )
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    if (!title.isNullOrBlank()) {
                        onUpdateTabState(
                            null,
                            title,
                            null,
                            null,
                            view?.canGoBack(),
                            view?.canGoForward()
                        )
                    }
                }

                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    if (consoleMessage != null) {
                        val level = when (consoleMessage.messageLevel()) {
                            ConsoleMessage.MessageLevel.ERROR -> LogLevel.ERROR
                            ConsoleMessage.MessageLevel.WARNING -> LogLevel.WARN
                            else -> LogLevel.INFO
                        }
                        val source = consoleMessage.sourceId()?.substringAfterLast('/') ?: "Web"
                        onAddConsoleLog(
                            ConsoleLogItem(
                                level = level,
                                source = "$source:${consoleMessage.lineNumber()}",
                                message = consoleMessage.message() ?: ""
                            )
                        )
                    }
                    return super.onConsoleMessage(consoleMessage)
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    onUpdateTabState(
                        url,
                        null,
                        true,
                        0,
                        view?.canGoBack(),
                        view?.canGoForward()
                    )
                    // Inject polyfill early
                    view?.evaluateJavascript(ExtensionInjector.buildPolyfillScript(), null)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val currentUrl = url ?: ""
                    onUpdateTabState(
                        currentUrl,
                        view?.title,
                        false,
                        100,
                        view?.canGoBack(),
                        view?.canGoForward()
                    )

                    // Inject Polyfill + All matching enabled WebExtensions
                    view?.evaluateJavascript(ExtensionInjector.buildPolyfillScript()) {
                        if (view != null && currentUrl.isNotBlank()) {
                            for (ext in enabledExtensions) {
                                if (ext.isEnabled) {
                                    ExtensionInjector.injectExtension(view, ext, currentUrl)
                                }
                            }
                        }
                    }
                }

                @SuppressLint("WebViewClientOnReceivedSslError")
                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?
                ) {
                    // Proceed for demo/development flexibility
                    handler?.proceed()
                }

                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    onUpdateTabState(
                        url,
                        view?.title,
                        null,
                        null,
                        view?.canGoBack(),
                        view?.canGoForward()
                    )
                }
            }

            loadUrl(tab.url)
        }
    }

    // Handle desktop mode toggle
    LaunchedEffect(tab.isDesktopMode) {
        val currentSettings = webView.settings
        if (tab.isDesktopMode) {
            currentSettings.userAgentString = DESKTOP_USER_AGENT
            currentSettings.useWideViewPort = true
            currentSettings.loadWithOverviewMode = true
        } else {
            currentSettings.userAgentString = null
            currentSettings.useWideViewPort = true
            currentSettings.loadWithOverviewMode = true
        }
    }

    // Trigger URL change if tab's target URL changes externally
    LaunchedEffect(tab.url) {
        if (webView.url != tab.url && tab.url.isNotBlank()) {
            webView.loadUrl(tab.url)
        }
    }

    DisposableEffect(tab.id) {
        onWebViewCreated(webView)
        onDispose {
            webView.stopLoading()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier.fillMaxSize()
    )
}
