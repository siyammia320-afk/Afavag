package com.example.engine

import android.util.Base64
import android.webkit.WebView
import com.example.data.model.ContentScriptConfig
import com.example.data.model.ExtensionEntity
import java.io.File

object ExtensionInjector {

    /**
     * Builds the Chrome WebExtension compatibility layer, console interceptor,
     * and network HTTP request capture hook.
     */
    fun buildPolyfillScript(): String {
        return """
            (function() {
                if (window.__extenPolyfillLoaded) return;
                window.__extenPolyfillLoaded = true;

                // Console interceptor
                const origLog = console.log;
                const origWarn = console.warn;
                const origErr = console.error;

                console.log = function(...args) {
                    origLog.apply(console, args);
                    try {
                        if (window.ExtenNativeBridge) {
                            window.ExtenNativeBridge.postLog('INFO', 'Console', args.map(a => typeof a === 'object' ? JSON.stringify(a) : String(a)).join(' '));
                        }
                    } catch(e) {}
                };

                console.warn = function(...args) {
                    origWarn.apply(console, args);
                    try {
                        if (window.ExtenNativeBridge) {
                            window.ExtenNativeBridge.postLog('WARN', 'Console', args.map(a => typeof a === 'object' ? JSON.stringify(a) : String(a)).join(' '));
                        }
                    } catch(e) {}
                };

                console.error = function(...args) {
                    origErr.apply(console, args);
                    try {
                        if (window.ExtenNativeBridge) {
                            window.ExtenNativeBridge.postLog('ERROR', 'Console', args.map(a => typeof a === 'object' ? JSON.stringify(a) : String(a)).join(' '));
                        }
                    } catch(e) {}
                };

                // HTTP Fetch & XHR Network Logger Interceptor
                try {
                    const origFetch = window.fetch;
                    window.fetch = async function(...args) {
                        const url = (typeof args[0] === 'string') ? args[0] : (args[0] && args[0].url) || 'unknown';
                        const options = args[1] || {};
                        const method = (options.method || 'GET').toUpperCase();
                        if (window.ExtenNativeBridge) {
                            window.ExtenNativeBridge.postNetworkRequest(method, url, JSON.stringify(options.headers || {}));
                        }
                        try {
                            const resp = await origFetch.apply(this, args);
                            if (window.ExtenNativeBridge) {
                                window.ExtenNativeBridge.postLog('NETWORK', 'Fetch (' + resp.status + ')', method + ' ' + url);
                            }
                            return resp;
                        } catch(err) {
                            if (window.ExtenNativeBridge) {
                                window.ExtenNativeBridge.postLog('ERROR', 'Fetch Failed', method + ' ' + url + ' - ' + err.message);
                            }
                            throw err;
                        }
                    };

                    const origXhrOpen = XMLHttpRequest.prototype.open;
                    const origXhrSend = XMLHttpRequest.prototype.send;
                    XMLHttpRequest.prototype.open = function(method, url) {
                        this._reqMethod = method;
                        this._reqUrl = url;
                        return origXhrOpen.apply(this, arguments);
                    };
                    XMLHttpRequest.prototype.send = function(body) {
                        if (window.ExtenNativeBridge && this._reqUrl) {
                            window.ExtenNativeBridge.postNetworkRequest(this._reqMethod || 'GET', this._reqUrl, '');
                        }
                        this.addEventListener('load', function() {
                            if (window.ExtenNativeBridge) {
                                window.ExtenNativeBridge.postLog('NETWORK', 'XHR (' + this.status + ')', (this._reqMethod || 'GET') + ' ' + this._reqUrl);
                            }
                        });
                        return origXhrSend.apply(this, arguments);
                    };
                } catch(e) {}

                // WebExtension chrome/browser API shim
                window.chrome = window.chrome || {};
                window.browser = window.browser || window.chrome;

                window.chrome.runtime = window.chrome.runtime || {
                    id: 'exten-browser-runtime',
                    sendMessage: function(msg, cb) {
                        if (cb) cb({ status: 'ok' });
                    },
                    onMessage: {
                        addListener: function(fn) {}
                    }
                };

                window.chrome.storage = window.chrome.storage || {
                    local: {
                        get: function(keys, cb) {
                            const result = {};
                            if (window.ExtenNativeBridge) {
                                const extId = window.__currentExtensionId || 'default';
                                if (typeof keys === 'string') {
                                    const val = window.ExtenNativeBridge.getStorage(extId, keys);
                                    try { result[keys] = JSON.parse(val); } catch(_) { result[keys] = val; }
                                } else if (Array.isArray(keys)) {
                                    keys.forEach(k => {
                                        const val = window.ExtenNativeBridge.getStorage(extId, k);
                                        try { result[k] = JSON.parse(val); } catch(_) { result[k] = val; }
                                    });
                                }
                            }
                            if (cb) cb(result);
                            return Promise.resolve(result);
                        },
                        set: function(items, cb) {
                            if (window.ExtenNativeBridge && items) {
                                const extId = window.__currentExtensionId || 'default';
                                Object.keys(items).forEach(k => {
                                    const valStr = typeof items[k] === 'string' ? items[k] : JSON.stringify(items[k]);
                                    window.ExtenNativeBridge.setStorage(extId, k, valStr);
                                });
                            }
                            if (cb) cb();
                            return Promise.resolve();
                        },
                        remove: function(keys, cb) {
                            if (window.ExtenNativeBridge && keys) {
                                const extId = window.__currentExtensionId || 'default';
                                const keyList = Array.isArray(keys) ? keys : [keys];
                                keyList.forEach(k => window.ExtenNativeBridge.removeStorage(extId, k));
                            }
                            if (cb) cb();
                            return Promise.resolve();
                        },
                        clear: function(cb) {
                            if (window.ExtenNativeBridge) {
                                const extId = window.__currentExtensionId || 'default';
                                window.ExtenNativeBridge.clearStorage(extId);
                            }
                            if (cb) cb();
                            return Promise.resolve();
                        }
                    }
                };
            })();
        """.trimIndent()
    }

    /**
     * Injects matching CSS and JS files for an extension on a web page.
     */
    fun injectExtension(webView: WebView, extension: ExtensionEntity, currentUrl: String) {
        val rootDir = File(extension.installPath)
        if (!rootDir.exists()) return

        val configs = ContentScriptConfig.parseList(extension.contentScriptsJson)

        for (config in configs) {
            if (!ExtensionMatcher.matchesAny(currentUrl, config.matches)) {
                continue
            }

            // 1. Inject CSS files
            for (cssFileName in config.cssFiles) {
                val cssFile = File(rootDir, cssFileName)
                if (cssFile.exists()) {
                    val cssContent = cssFile.readText()
                    injectCss(webView, extension.id, cssFileName, cssContent)
                }
            }

            // 2. Inject JS files
            for (jsFileName in config.jsFiles) {
                val jsFile = File(rootDir, jsFileName)
                if (jsFile.exists()) {
                    val jsContent = jsFile.readText()
                    injectJs(webView, extension.id, extension.name, jsFileName, jsContent)
                }
            }
        }
    }

    private fun injectCss(webView: WebView, extId: String, fileName: String, css: String) {
        val encodedCss = Base64.encodeToString(css.toByteArray(), Base64.NO_WRAP)
        val jsSnippet = """
            (function() {
                const styleId = 'ext-style-${extId}-${fileName.hashCode()}';
                let style = document.getElementById(styleId);
                if (!style) {
                    style = document.createElement('style');
                    style.id = styleId;
                    document.head.appendChild(style);
                }
                style.textContent = decodeURIComponent(escape(window.atob('$encodedCss')));
            })();
        """.trimIndent()

        webView.evaluateJavascript(jsSnippet, null)
    }

    private fun injectJs(
        webView: WebView,
        extId: String,
        extName: String,
        fileName: String,
        jsCode: String
    ) {
        val encodedJs = Base64.encodeToString(jsCode.toByteArray(), Base64.NO_WRAP)
        val scriptWrapper = """
            (function() {
                window.__currentExtensionId = '$extId';
                try {
                    const code = decodeURIComponent(escape(window.atob('$encodedJs')));
                    const fn = new Function(code);
                    fn();
                    if (window.ExtenNativeBridge) {
                        window.ExtenNativeBridge.notifyScriptInjected('$extName', '$fileName');
                    }
                } catch(e) {
                    console.error('Error in extension [$extName] ($fileName):', e.message);
                    if (window.ExtenNativeBridge) {
                        window.ExtenNativeBridge.postLog('ERROR', '$extName', 'Runtime Error in $fileName: ' + e.message);
                    }
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(scriptWrapper, null)
    }
}
