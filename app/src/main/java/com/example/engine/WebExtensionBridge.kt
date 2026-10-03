package com.example.engine

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.example.data.model.ConsoleLogItem
import com.example.data.model.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class WebExtensionBridge(
    private val context: Context,
    private val onLogReceived: (ConsoleLogItem) -> Unit
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ext_storage_prefs", Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun postLog(levelStr: String, source: String, message: String) {
        val level = when (levelStr.uppercase()) {
            "WARN", "WARNING" -> LogLevel.WARN
            "ERROR" -> LogLevel.ERROR
            "EXT", "EXTENSION" -> LogLevel.EXTENSION
            "NETWORK" -> LogLevel.NETWORK
            else -> LogLevel.INFO
        }
        val item = ConsoleLogItem(
            level = level,
            source = source.ifBlank { "Page" },
            message = message
        )
        onLogReceived(item)
    }

    @JavascriptInterface
    fun getStorage(extId: String, key: String): String {
        return prefs.getString("${extId}_$key", "") ?: ""
    }

    @JavascriptInterface
    fun setStorage(extId: String, key: String, value: String) {
        prefs.edit().putString("${extId}_$key", value).apply()
    }

    @JavascriptInterface
    fun removeStorage(extId: String, key: String) {
        prefs.edit().remove("${extId}_$key").apply()
    }

    @JavascriptInterface
    fun clearStorage(extId: String) {
        val editor = prefs.edit()
        val prefix = "${extId}_"
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach {
            editor.remove(it)
        }
        editor.apply()
    }

    @JavascriptInterface
    fun notifyScriptInjected(extName: String, scriptName: String) {
        postLog("EXT", extName, "Injected script '$scriptName' successfully.")
    }

    @JavascriptInterface
    fun showToast(msg: String) {
        mainHandler.post {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
