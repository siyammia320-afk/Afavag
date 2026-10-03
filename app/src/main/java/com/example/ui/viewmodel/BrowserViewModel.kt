package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.webkit.URLUtil
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ExtenBrowserApp
import com.example.data.model.BookmarkEntity
import com.example.data.model.BrowserTab
import com.example.data.model.ConsoleLogItem
import com.example.data.model.ContentScriptConfig
import com.example.data.model.ExtensionEntity
import com.example.data.model.HistoryEntity
import com.example.data.model.LogLevel
import com.example.engine.ExtensionMatcher
import com.example.engine.ExtensionParser
import com.example.engine.SampleExtensions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val db = (application as ExtenBrowserApp).database
    private val extensionDao = db.extensionDao()
    private val bookmarkDao = db.bookmarkDao()
    private val historyDao = db.historyDao()

    val extensions: StateFlow<List<ExtensionEntity>> = extensionDao.getAllExtensions()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val bookmarks: StateFlow<List<BookmarkEntity>> = bookmarkDao.getAllBookmarks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val history: StateFlow<List<HistoryEntity>> = historyDao.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _tabs = MutableStateFlow<List<BrowserTab>>(
        listOf(
            BrowserTab(
                id = "default_tab",
                url = "https://www.google.com",
                title = "Google",
                isLoading = false
            )
        )
    )
    val tabs: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()

    private val _currentTabId = MutableStateFlow("default_tab")
    val currentTabId: StateFlow<String> = _currentTabId.asStateFlow()

    val currentTab: StateFlow<BrowserTab?> = combine(_tabs, _currentTabId) { tabList, currentId ->
        tabList.find { it.id == currentId } ?: tabList.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Lazily, _tabs.value.firstOrNull())

    private val _urlInput = MutableStateFlow("https://www.google.com")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _searchEngine = MutableStateFlow("Google")
    val searchEngine: StateFlow<String> = _searchEngine.asStateFlow()

    private val _consoleLogs = MutableStateFlow<List<ConsoleLogItem>>(emptyList())
    val consoleLogs: StateFlow<List<ConsoleLogItem>> = _consoleLogs.asStateFlow()

    private val _activePopupExtension = MutableStateFlow<ExtensionEntity?>(null)
    val activePopupExtension: StateFlow<ExtensionEntity?> = _activePopupExtension.asStateFlow()

    private val _editingExtension = MutableStateFlow<ExtensionEntity?>(null)
    val editingExtension: StateFlow<ExtensionEntity?> = _editingExtension.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    fun clearUiMessage() {
        _uiMessage.value = null
    }

    fun setUrlInput(value: String) {
        _urlInput.value = value
    }

    fun openNewTab(url: String = "https://www.google.com") {
        val formattedUrl = formatUrl(url)
        val newTab = BrowserTab(
            url = formattedUrl,
            title = if (formattedUrl.contains("google.com")) "Google" else "New Tab"
        )
        _tabs.value = _tabs.value + newTab
        _currentTabId.value = newTab.id
        _urlInput.value = formattedUrl
    }

    fun selectTab(tabId: String) {
        _currentTabId.value = tabId
        val tab = _tabs.value.find { it.id == tabId }
        if (tab != null) {
            _urlInput.value = tab.url
        }
    }

    fun closeTab(tabId: String) {
        val currentList = _tabs.value
        if (currentList.size <= 1) {
            // Keep at least one tab
            val resetTab = BrowserTab(
                url = "https://www.google.com",
                title = "Google"
            )
            _tabs.value = listOf(resetTab)
            _currentTabId.value = resetTab.id
            _urlInput.value = resetTab.url
            return
        }

        val filtered = currentList.filter { it.id != tabId }
        _tabs.value = filtered
        if (_currentTabId.value == tabId) {
            val nextTab = filtered.last()
            _currentTabId.value = nextTab.id
            _urlInput.value = nextTab.url
        }
    }

    fun updateCurrentTabState(
        url: String? = null,
        title: String? = null,
        isLoading: Boolean? = null,
        progress: Int? = null,
        canGoBack: Boolean? = null,
        canGoForward: Boolean? = null
    ) {
        val currentId = _currentTabId.value
        _tabs.value = _tabs.value.map { tab ->
            if (tab.id == currentId) {
                val newUrl = url ?: tab.url
                if (url != null) {
                    _urlInput.value = url
                    // Record history
                    recordHistory(newUrl, title ?: tab.title)
                }
                tab.copy(
                    url = newUrl,
                    title = title ?: tab.title,
                    isLoading = isLoading ?: tab.isLoading,
                    progress = progress ?: tab.progress,
                    canGoBack = canGoBack ?: tab.canGoBack,
                    canGoForward = canGoForward ?: tab.canGoForward,
                    activeExtensionsCount = calculateActiveExtensionsCount(newUrl)
                )
            } else {
                tab
            }
        }
    }

    private fun calculateActiveExtensionsCount(url: String): Int {
        val activeExts = extensions.value.filter { it.isEnabled }
        return activeExts.count { ext ->
            val configs = ContentScriptConfig.parseList(ext.contentScriptsJson)
            configs.any { ExtensionMatcher.matchesAny(url, it.matches) }
        }
    }

    fun formatUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return "https://www.google.com"

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("file://") || trimmed.startsWith("about:")) {
            return trimmed
        }

        // Check if it looks like a domain name
        if (trimmed.contains(".") && !trimmed.contains(" ") && trimmed.indexOf('.') < trimmed.length - 1) {
            return "https://$trimmed"
        }

        // Otherwise perform search
        val query = URLEncoder.encode(trimmed, StandardCharsets.UTF_8.toString())
        return when (_searchEngine.value) {
            "DuckDuckGo" -> "https://duckduckgo.com/?q=$query"
            "Bing" -> "https://www.bing.com/search?q=$query"
            "Brave" -> "https://search.brave.com/search?q=$query"
            else -> "https://www.google.com/search?q=$query"
        }
    }

    fun setSearchEngine(engine: String) {
        _searchEngine.value = engine
    }

    fun toggleDesktopMode() {
        val currentId = _currentTabId.value
        _tabs.value = _tabs.value.map { tab ->
            if (tab.id == currentId) {
                tab.copy(isDesktopMode = !tab.isDesktopMode)
            } else tab
        }
    }

    fun toggleBookmarkCurrentPage() {
        val tab = currentTab.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val isBookmarked = bookmarkDao.isBookmarked(tab.url)
            if (isBookmarked) {
                bookmarkDao.deleteByUrl(tab.url)
                _uiMessage.value = "Bookmark removed"
            } else {
                bookmarkDao.insertBookmark(
                    BookmarkEntity(
                        title = tab.title.ifBlank { tab.url },
                        url = tab.url
                    )
                )
                _uiMessage.value = "Saved to Bookmarks"
            }
        }
    }

    private fun recordHistory(url: String, title: String) {
        if (url.isBlank() || url.startsWith("about:") || url.startsWith("data:")) return
        viewModelScope.launch(Dispatchers.IO) {
            historyDao.insertHistory(
                HistoryEntity(
                    title = title.ifBlank { url },
                    url = url
                )
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            historyDao.clearAll()
            _uiMessage.value = "Browsing history cleared"
        }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            historyDao.deleteById(id)
        }
    }

    fun deleteBookmark(bookmark: BookmarkEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            bookmarkDao.delete(bookmark)
            _uiMessage.value = "Bookmark deleted"
        }
    }

    // --- Extension Management ---

    fun toggleExtension(id: String, isEnabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            extensionDao.setExtensionEnabled(id, isEnabled)
            val ext = extensionDao.getExtensionById(id)
            val msg = if (isEnabled) "${ext?.name ?: "Extension"} enabled" else "${ext?.name ?: "Extension"} disabled"
            _uiMessage.value = msg
            // Update active count for current tab
            currentTab.value?.let { tab ->
                updateCurrentTabState(url = tab.url)
            }
        }
    }

    fun deleteExtension(extension: ExtensionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Delete physical files
                val dir = File(extension.installPath)
                if (dir.exists()) {
                    dir.deleteRecursively()
                }
                extensionDao.delete(extension)
                _uiMessage.value = "Removed ${extension.name}"
            } catch (e: Exception) {
                _uiMessage.value = "Error removing extension: ${e.message}"
            }
        }
    }

    fun importExtensionZip(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<ExtenBrowserApp>()
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    _uiMessage.value = "Could not open selected ZIP file"
                    return@launch
                }

                val ext = ExtensionParser.installFromZip(context, inputStream)
                extensionDao.insertOrUpdate(ext)
                _uiMessage.value = "Successfully installed '${ext.name}' (v${ext.version})"
            } catch (e: Exception) {
                e.printStackTrace()
                _uiMessage.value = "Failed to import ZIP: ${e.localizedMessage ?: "Invalid format"}"
            }
        }
    }

    fun createCustomExtension(
        name: String,
        description: String,
        matchPattern: String,
        jsCode: String,
        cssCode: String,
        popupHtml: String? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<ExtenBrowserApp>()
                val ext = ExtensionParser.createCustomExtension(
                    context = context,
                    name = name.ifBlank { "Custom Script" },
                    description = description.ifBlank { "User created extension" },
                    matchPattern = matchPattern.ifBlank { "<all_urls>" },
                    jsCode = jsCode,
                    cssCode = cssCode,
                    popupHtml = popupHtml
                )
                extensionDao.insertOrUpdate(ext)
                _uiMessage.value = "Created extension '${ext.name}'"
            } catch (e: Exception) {
                _uiMessage.value = "Failed to create extension: ${e.message}"
            }
        }
    }

    fun restoreBuiltInExtensions() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<ExtenBrowserApp>()
                val samples = SampleExtensions.seedSampleExtensions(context)
                for (s in samples) {
                    extensionDao.insertOrUpdate(s)
                }
                _uiMessage.value = "Sample extensions loaded"
            } catch (e: Exception) {
                _uiMessage.value = "Error loading samples: ${e.message}"
            }
        }
    }

    fun openExtensionPopup(ext: ExtensionEntity) {
        _activePopupExtension.value = ext
    }

    fun closeExtensionPopup() {
        _activePopupExtension.value = null
    }

    fun openExtensionEditor(ext: ExtensionEntity) {
        _editingExtension.value = ext
    }

    fun closeExtensionEditor() {
        _editingExtension.value = null
    }

    fun saveExtensionFile(extensionId: String, fileName: String, newContent: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ext = extensionDao.getExtensionById(extensionId) ?: return@launch
                val file = File(ext.installPath, fileName)
                file.writeText(newContent)
                _uiMessage.value = "Saved $fileName"
            } catch (e: Exception) {
                _uiMessage.value = "Error saving $fileName: ${e.message}"
            }
        }
    }

    // --- DevTools Console Logs ---

    fun addConsoleLog(item: ConsoleLogItem) {
        val current = _consoleLogs.value.toMutableList()
        current.add(0, item) // newest first
        if (current.size > 400) {
            _consoleLogs.value = current.take(400)
        } else {
            _consoleLogs.value = current
        }
    }

    fun clearConsoleLogs() {
        _consoleLogs.value = emptyList()
        _uiMessage.value = "Console logs cleared"
    }
}
