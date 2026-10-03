package com.example.ui.screens

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.BrowserTab
import com.example.ui.components.BrowserBottomBar
import com.example.ui.components.BrowserTopBar
import com.example.ui.components.BrowserWebView
import com.example.ui.dialogs.ExtensionCodeEditorDialog
import com.example.ui.dialogs.ExtensionPopupDialog
import com.example.ui.viewmodel.BrowserViewModel

enum class CurrentScreen {
    BROWSER,
    TABS,
    EXTENSIONS,
    HISTORY_BOOKMARKS
}

@Composable
fun BrowserMainScreen(
    viewModel: BrowserViewModel = viewModel()
) {
    val tabs by viewModel.tabs.collectAsState()
    val currentTabId by viewModel.currentTabId.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val urlInput by viewModel.urlInput.collectAsState()
    val extensions by viewModel.extensions.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val history by viewModel.history.collectAsState()
    val consoleLogs by viewModel.consoleLogs.collectAsState()
    val activePopupExtension by viewModel.activePopupExtension.collectAsState()
    val editingExtension by viewModel.editingExtension.collectAsState()
    val uiMessage by viewModel.uiMessage.collectAsState()

    var currentScreen by remember { mutableStateOf(CurrentScreen.BROWSER) }
    var showConsoleSheet by remember { mutableStateOf(false) }
    var activeWebView by remember { mutableStateOf<WebView?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiMessage) {
        uiMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUiMessage()
        }
    }

    // Back button handling
    BackHandler(enabled = true) {
        if (showConsoleSheet) {
            showConsoleSheet = false
        } else if (currentScreen != CurrentScreen.BROWSER) {
            currentScreen = CurrentScreen.BROWSER
        } else if (activeWebView != null && activeWebView?.canGoBack() == true) {
            activeWebView?.goBack()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "ScreenTransition"
            ) { screen ->
                when (screen) {
                    CurrentScreen.BROWSER -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                        ) {
                            // Top Bar
                            BrowserTopBar(
                                tab = currentTab,
                                urlInputText = urlInput,
                                onUrlInputChange = { viewModel.setUrlInput(it) },
                                onSubmitUrl = { formatted ->
                                    val url = viewModel.formatUrl(formatted)
                                    viewModel.updateCurrentTabState(url = url)
                                },
                                onReload = {
                                    if (currentTab?.isLoading == true) {
                                        activeWebView?.stopLoading()
                                    } else {
                                        activeWebView?.reload()
                                    }
                                },
                                onOpenExtensionsHub = { currentScreen = CurrentScreen.EXTENSIONS },
                                onOpenConsole = { showConsoleSheet = true },
                                onOpenTabsOverview = { currentScreen = CurrentScreen.TABS },
                                tabCount = tabs.size,
                                activeExtensionsCount = currentTab?.activeExtensionsCount ?: 0
                            )

                            // Main Web View
                            Box(modifier = Modifier.weight(1f)) {
                                currentTab?.let { activeTab ->
                                    BrowserWebView(
                                        tab = activeTab,
                                        enabledExtensions = extensions.filter { it.isEnabled },
                                        onUpdateTabState = { url, title, isLoading, progress, canGoBack, canGoForward ->
                                            viewModel.updateCurrentTabState(
                                                url = url,
                                                title = title,
                                                isLoading = isLoading,
                                                progress = progress,
                                                canGoBack = canGoBack,
                                                canGoForward = canGoForward
                                            )
                                        },
                                        onAddConsoleLog = { logItem ->
                                            viewModel.addConsoleLog(logItem)
                                        },
                                        onWebViewCreated = { webView ->
                                            activeWebView = webView
                                        }
                                    )
                                }
                            }

                            // Bottom Navigation Bar
                            BrowserBottomBar(
                                tab = currentTab,
                                onGoBack = { activeWebView?.goBack() },
                                onGoForward = { activeWebView?.goForward() },
                                onGoHome = {
                                    viewModel.updateCurrentTabState(url = "https://www.google.com")
                                },
                                onNewTab = { viewModel.openNewTab() },
                                onToggleBookmark = { viewModel.toggleBookmarkCurrentPage() },
                                onToggleDesktop = { viewModel.toggleDesktopMode() },
                                onOpenHistoryBookmarks = { currentScreen = CurrentScreen.HISTORY_BOOKMARKS },
                                onOpenExtensionsHub = { currentScreen = CurrentScreen.EXTENSIONS },
                                onOpenConsoleLogs = { showConsoleSheet = true }
                            )
                        }
                    }

                    CurrentScreen.TABS -> {
                        TabsOverviewScreen(
                            tabs = tabs,
                            currentTabId = currentTabId,
                            onSelectTab = { tabId ->
                                viewModel.selectTab(tabId)
                                currentScreen = CurrentScreen.BROWSER
                            },
                            onCloseTab = { tabId ->
                                viewModel.closeTab(tabId)
                            },
                            onNewTab = {
                                viewModel.openNewTab()
                                currentScreen = CurrentScreen.BROWSER
                            },
                            onNavigateBack = { currentScreen = CurrentScreen.BROWSER }
                        )
                    }

                    CurrentScreen.EXTENSIONS -> {
                        ExtensionsScreen(
                            extensions = extensions,
                            onToggleExtension = { id, isEnabled ->
                                viewModel.toggleExtension(id, isEnabled)
                            },
                            onDeleteExtension = { ext ->
                                viewModel.deleteExtension(ext)
                            },
                            onImportZip = { uri ->
                                viewModel.importExtensionZip(uri)
                            },
                            onCreateCustomExtension = { name, desc, match, js, css, popup ->
                                viewModel.createCustomExtension(name, desc, match, js, css, popup)
                            },
                            onOpenPopup = { ext ->
                                viewModel.openExtensionPopup(ext)
                            },
                            onOpenEditor = { ext ->
                                viewModel.openExtensionEditor(ext)
                            },
                            onRestoreSampleGallery = {
                                viewModel.restoreBuiltInExtensions()
                            },
                            onNavigateBack = { currentScreen = CurrentScreen.BROWSER }
                        )
                    }

                    CurrentScreen.HISTORY_BOOKMARKS -> {
                        HistoryBookmarksScreen(
                            bookmarks = bookmarks,
                            history = history,
                            onOpenUrl = { url ->
                                viewModel.updateCurrentTabState(url = url)
                                currentScreen = CurrentScreen.BROWSER
                            },
                            onDeleteBookmark = { bookmark ->
                                viewModel.deleteBookmark(bookmark)
                            },
                            onDeleteHistoryItem = { id ->
                                viewModel.deleteHistoryItem(id)
                            },
                            onClearHistory = { viewModel.clearHistory() },
                            onNavigateBack = { currentScreen = CurrentScreen.BROWSER }
                        )
                    }
                }
            }

            // DevTools Console Bottom Sheet
            if (showConsoleSheet) {
                DevToolsConsoleSheet(
                    logs = consoleLogs,
                    onClearLogs = { viewModel.clearConsoleLogs() },
                    onEvalJs = { expression ->
                        activeWebView?.evaluateJavascript(expression) { result ->
                            viewModel.addConsoleLog(
                                com.example.data.model.ConsoleLogItem(
                                    level = com.example.data.model.LogLevel.INFO,
                                    source = "Eval",
                                    message = "=> $result"
                                )
                            )
                        }
                    },
                    onDismiss = { showConsoleSheet = false }
                )
            }

            // Extension Action Popup Dialog
            activePopupExtension?.let { ext ->
                ExtensionPopupDialog(
                    extension = ext,
                    onDismiss = { viewModel.closeExtensionPopup() }
                )
            }

            // Extension Code Editor Dialog
            editingExtension?.let { ext ->
                ExtensionCodeEditorDialog(
                    extension = ext,
                    onSaveFile = { extId, fileName, newContent ->
                        viewModel.saveExtensionFile(extId, fileName, newContent)
                    },
                    onDismiss = { viewModel.closeExtensionEditor() }
                )
            }
        }
    }
}
