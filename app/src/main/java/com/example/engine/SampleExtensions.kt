package com.example.engine

import android.content.Context
import com.example.data.model.ContentScriptConfig
import com.example.data.model.ExtensionEntity
import org.json.JSONObject
import java.io.File

object SampleExtensions {

    fun seedSampleExtensions(context: Context): List<ExtensionEntity> {
        val results = mutableListOf<ExtensionEntity>()
        val baseDir = File(context.filesDir, "extensions")
        if (!baseDir.exists()) baseDir.mkdirs()

        // 1. Universal Dark Reader Pro
        results.add(createDarkReaderExtension(baseDir))

        // 2. DOM Inspector & Element Remover
        results.add(createDomInspectorExtension(baseDir))

        // 3. Auto Scroll & Reading Assistant
        results.add(createAutoScrollExtension(baseDir))

        // 4. Page Analyzer & Stats HUD
        results.add(createPageAnalyzerExtension(baseDir))

        return results
    }

    private fun createDarkReaderExtension(baseDir: File): ExtensionEntity {
        val id = "builtin_dark_reader"
        val extDir = File(baseDir, id).apply { mkdirs() }

        val css = """
            /* Universal Smart Dark Theme */
            html {
                filter: invert(90%) hue-rotate(180deg) !important;
                background-color: #121212 !important;
            }
            img, picture, video, canvas, svg, [style*="background-image"] {
                filter: invert(100%) hue-rotate(180deg) !important;
            }
            iframe {
                filter: invert(100%) hue-rotate(180deg) !important;
            }
        """.trimIndent()

        val js = """
            (function() {
                console.log("[DarkReader] Initialized smart dark mode.");
                // Add a small floating indicator badge
                if (!document.getElementById('dark-reader-pill')) {
                    const pill = document.createElement('div');
                    pill.id = 'dark-reader-pill';
                    pill.innerHTML = '🌙 Dark Mode Active';
                    pill.style.cssText = 'position:fixed;bottom:12px;left:12px;background:rgba(15,23,42,0.85);color:#38bdf8;padding:6px 12px;border-radius:20px;font-size:12px;font-family:sans-serif;z-index:2147483640;backdrop-filter:blur(6px);border:1px solid #0284c7;pointer-events:none;transition:opacity 0.5s;';
                    document.body.appendChild(pill);
                    setTimeout(() => { pill.style.opacity = '0'; }, 3000);
                }
            })();
        """.trimIndent()

        val popupHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: sans-serif; background: #0f172a; color: #f8fafc; padding: 14px; margin: 0; min-width: 240px; }
                    h3 { margin-top: 0; color: #38bdf8; font-size: 16px; }
                    p { font-size: 13px; color: #94a3b8; line-height: 1.4; }
                    .tag { display: inline-block; background: #0369a1; color: white; padding: 2px 8px; border-radius: 12px; font-size: 11px; }
                    button { background: #38bdf8; color: #0f172a; border: none; padding: 8px 16px; border-radius: 8px; font-weight: bold; width: 100%; cursor: pointer; margin-top: 8px; }
                </style>
            </head>
            <body>
                <h3>🌙 Universal Dark Reader</h3>
                <span class="tag">Active Everywhere</span>
                <p>Applies high-contrast dark color grading and inverted media corrections to reduce eye strain.</p>
                <button onclick="alert('Dark mode rules are automatically applied to this webpage!')">Status: Active</button>
            </body>
            </html>
        """.trimIndent()

        File(extDir, "style.css").writeText(css)
        File(extDir, "content.js").writeText(js)
        File(extDir, "popup.html").writeText(popupHtml)

        val manifest = JSONObject().apply {
            put("manifest_version", 3)
            put("name", "Universal Dark Reader")
            put("version", "2.1.0")
            put("description", "High contrast dark mode styling and smart media inversion for all websites.")
            put("action", JSONObject().apply { put("default_popup", "popup.html") })
        }
        File(extDir, "manifest.json").writeText(manifest.toString(2))

        val contentScripts = listOf(
            ContentScriptConfig(
                matches = listOf("<all_urls>"),
                jsFiles = listOf("content.js"),
                cssFiles = listOf("style.css"),
                runAt = "document_idle"
            )
        )

        return ExtensionEntity(
            id = id,
            name = "Universal Dark Reader",
            version = "2.1.0",
            description = "High contrast dark mode styling and smart media inversion for all websites.",
            isEnabled = true,
            installPath = extDir.absolutePath,
            manifestVersion = 3,
            contentScriptsJson = ContentScriptConfig.serializeList(contentScripts),
            popupRelativePath = "popup.html",
            iconRelativePath = null,
            author = "ExtenBrowser Team",
            matchesSummary = "<all_urls>",
            isBuiltIn = true
        )
    }

    private fun createDomInspectorExtension(baseDir: File): ExtensionEntity {
        val id = "builtin_dom_inspector"
        val extDir = File(baseDir, id).apply { mkdirs() }

        val js = """
            (function() {
                if (window.__domInspectorLoaded) return;
                window.__domInspectorLoaded = true;
                
                let isInspecting = false;
                let hoveredEl = null;

                const btn = document.createElement('div');
                btn.id = 'ext-dom-inspector-btn';
                btn.innerHTML = '🔍 Zap Element';
                btn.style.cssText = 'position:fixed;bottom:64px;right:14px;background:#6366f1;color:white;padding:8px 14px;border-radius:24px;font-size:13px;font-weight:600;font-family:sans-serif;z-index:2147483647;box-shadow:0 4px 12px rgba(0,0,0,0.3);cursor:pointer;user-select:none;';
                
                document.body.appendChild(btn);

                function onMouseOver(e) {
                    if (!isInspecting) return;
                    if (e.target === btn) return;
                    if (hoveredEl) hoveredEl.style.outline = '';
                    hoveredEl = e.target;
                    hoveredEl.style.outline = '3px solid #ef4444';
                }

                function onClick(e) {
                    if (!isInspecting) return;
                    if (e.target === btn) return;
                    e.preventDefault();
                    e.stopPropagation();
                    const tag = e.target.tagName.toLowerCase();
                    const idName = e.target.id ? '#' + e.target.id : '';
                    e.target.remove();
                    console.log('[Inspector] Removed element <' + tag + idName + '>');
                    alert('Removed <' + tag + idName + '>');
                    toggleInspect();
                }

                function toggleInspect() {
                    isInspecting = !isInspecting;
                    if (isInspecting) {
                        btn.style.background = '#ef4444';
                        btn.innerHTML = '❌ Click element to remove';
                        document.addEventListener('mouseover', onMouseOver, true);
                        document.addEventListener('click', onClick, true);
                    } else {
                        btn.style.background = '#6366f1';
                        btn.innerHTML = '🔍 Zap Element';
                        if (hoveredEl) {
                            hoveredEl.style.outline = '';
                            hoveredEl = null;
                        }
                        document.removeEventListener('mouseover', onMouseOver, true);
                        document.removeEventListener('click', onClick, true);
                    }
                }

                btn.onclick = toggleInspect;
                console.log("[DOM Inspector] Tool ready.");
            })();
        """.trimIndent()

        File(extDir, "content.js").writeText(js)
        val manifest = JSONObject().apply {
            put("manifest_version", 3)
            put("name", "DOM Element Inspector & Zapper")
            put("version", "1.2.0")
            put("description", "Inspect and tap to remove intrusive popups, banners, or overlays from any webpage.")
        }
        File(extDir, "manifest.json").writeText(manifest.toString(2))

        val contentScripts = listOf(
            ContentScriptConfig(
                matches = listOf("<all_urls>"),
                jsFiles = listOf("content.js"),
                cssFiles = emptyList(),
                runAt = "document_idle"
            )
        )

        return ExtensionEntity(
            id = id,
            name = "DOM Inspector & Zapper",
            version = "1.2.0",
            description = "Inspect and tap to remove intrusive popups, banners, or overlays from any webpage.",
            isEnabled = true,
            installPath = extDir.absolutePath,
            manifestVersion = 3,
            contentScriptsJson = ContentScriptConfig.serializeList(contentScripts),
            popupRelativePath = null,
            iconRelativePath = null,
            author = "ExtenBrowser Team",
            matchesSummary = "<all_urls>",
            isBuiltIn = true
        )
    }

    private fun createAutoScrollExtension(baseDir: File): ExtensionEntity {
        val id = "builtin_auto_scroll"
        val extDir = File(baseDir, id).apply { mkdirs() }

        val js = """
            (function() {
                if (window.__autoScrollLoaded) return;
                window.__autoScrollLoaded = true;

                let scrollTimer = null;
                let speed = 2; // px per tick

                const container = document.createElement('div');
                container.id = 'ext-auto-scroll-box';
                container.style.cssText = 'position:fixed;bottom:116px;right:14px;background:#1e293b;border:1px solid #475569;color:white;padding:6px 10px;border-radius:18px;font-size:12px;font-family:sans-serif;z-index:2147483646;display:flex;align-items:center;gap:6px;box-shadow:0 4px 10px rgba(0,0,0,0.3);';

                container.innerHTML = `
                    <button id="ext-scroll-toggle" style="background:#10b981;border:none;color:white;padding:4px 8px;border-radius:10px;cursor:pointer;font-weight:bold;">▶ Scroll</button>
                    <button id="ext-scroll-speed" style="background:#334155;border:none;color:#cbd5e1;padding:4px 8px;border-radius:10px;cursor:pointer;">1x</button>
                    <button id="ext-scroll-top" style="background:#334155;border:none;color:#cbd5e1;padding:4px 6px;border-radius:10px;cursor:pointer;">▲</button>
                `;

                document.body.appendChild(container);

                const toggleBtn = container.querySelector('#ext-scroll-toggle');
                const speedBtn = container.querySelector('#ext-scroll-speed');
                const topBtn = container.querySelector('#ext-scroll-top');

                function step() {
                    window.scrollBy(0, speed);
                    scrollTimer = requestAnimationFrame(step);
                }

                toggleBtn.onclick = function() {
                    if (scrollTimer) {
                        cancelAnimationFrame(scrollTimer);
                        scrollTimer = null;
                        toggleBtn.textContent = '▶ Scroll';
                        toggleBtn.style.background = '#10b981';
                    } else {
                        scrollTimer = requestAnimationFrame(step);
                        toggleBtn.textContent = '⏸ Pause';
                        toggleBtn.style.background = '#f59e0b';
                    }
                };

                speedBtn.onclick = function() {
                    if (speed === 1) { speed = 2; speedBtn.textContent = '2x'; }
                    else if (speed === 2) { speed = 4; speedBtn.textContent = '3x'; }
                    else { speed = 1; speedBtn.textContent = '1x'; }
                };

                topBtn.onclick = function() {
                    window.scrollTo({ top: 0, behavior: 'smooth' });
                };

                console.log("[AutoScroll] Controller attached.");
            })();
        """.trimIndent()

        File(extDir, "content.js").writeText(js)
        val manifest = JSONObject().apply {
            put("manifest_version", 3)
            put("name", "Auto-Scroll Assistant")
            put("version", "1.0.0")
            put("description", "Floating smooth scrolling controller with multi-speed adjust and instant top jump.")
        }
        File(extDir, "manifest.json").writeText(manifest.toString(2))

        val contentScripts = listOf(
            ContentScriptConfig(
                matches = listOf("<all_urls>"),
                jsFiles = listOf("content.js"),
                cssFiles = emptyList(),
                runAt = "document_idle"
            )
        )

        return ExtensionEntity(
            id = id,
            name = "Auto-Scroll Assistant",
            version = "1.0.0",
            description = "Floating smooth scrolling controller with multi-speed adjust and instant top jump.",
            isEnabled = false,
            installPath = extDir.absolutePath,
            manifestVersion = 3,
            contentScriptsJson = ContentScriptConfig.serializeList(contentScripts),
            popupRelativePath = null,
            iconRelativePath = null,
            author = "ExtenBrowser Team",
            matchesSummary = "<all_urls>",
            isBuiltIn = true
        )
    }

    private fun createPageAnalyzerExtension(baseDir: File): ExtensionEntity {
        val id = "builtin_page_analyzer"
        val extDir = File(baseDir, id).apply { mkdirs() }

        val js = """
            (function() {
                const words = document.body ? document.body.innerText.trim().split(/\s+/).length : 0;
                const readingTime = Math.ceil(words / 200);
                const links = document.querySelectorAll('a').length;
                const images = document.querySelectorAll('img').length;
                const headings = document.querySelectorAll('h1, h2, h3').length;

                console.log("[PageAnalyzer] Stats -> Words: " + words + ", Est. Reading Time: " + readingTime + " min, Links: " + links + ", Images: " + images + ", Headings: " + headings);
            })();
        """.trimIndent()

        val popupHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: sans-serif; background: #0f172a; color: #f8fafc; padding: 14px; margin: 0; min-width: 240px; }
                    h3 { margin-top: 0; color: #22d3ee; font-size: 15px; }
                    .stat-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-top: 10px; }
                    .stat-card { background: #1e293b; padding: 8px; border-radius: 8px; border: 1px solid #334155; }
                    .stat-val { font-size: 18px; font-weight: bold; color: #38bdf8; }
                    .stat-label { font-size: 11px; color: #94a3b8; }
                </style>
            </head>
            <body>
                <h3>📊 Page Analyzer</h3>
                <div class="stat-grid">
                    <div class="stat-card">
                        <div class="stat-val" id="stat-words">-</div>
                        <div class="stat-label">Total Words</div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-val" id="stat-read">-</div>
                        <div class="stat-label">Read Time (min)</div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-val" id="stat-links">-</div>
                        <div class="stat-label">Hyperlinks</div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-val" id="stat-images">-</div>
                        <div class="stat-label">Images</div>
                    </div>
                </div>
                <script>
                    const words = document.body ? document.body.innerText.trim().split(/\s+/).length : 0;
                    document.getElementById('stat-words').textContent = words;
                    document.getElementById('stat-read').textContent = Math.ceil(words / 200);
                    document.getElementById('stat-links').textContent = document.querySelectorAll('a').length;
                    document.getElementById('stat-images').textContent = document.querySelectorAll('img').length;
                </script>
            </body>
            </html>
        """.trimIndent()

        File(extDir, "content.js").writeText(js)
        File(extDir, "popup.html").writeText(popupHtml)

        val manifest = JSONObject().apply {
            put("manifest_version", 3)
            put("name", "Page Analyzer & Word Counter")
            put("version", "1.0.0")
            put("description", "Inspect page content statistics: word count, estimated reading time, links, and media count.")
            put("action", JSONObject().apply { put("default_popup", "popup.html") })
        }
        File(extDir, "manifest.json").writeText(manifest.toString(2))

        val contentScripts = listOf(
            ContentScriptConfig(
                matches = listOf("<all_urls>"),
                jsFiles = listOf("content.js"),
                cssFiles = emptyList(),
                runAt = "document_idle"
            )
        )

        return ExtensionEntity(
            id = id,
            name = "Page Analyzer & Word Counter",
            version = "1.0.0",
            description = "Inspect page content statistics: word count, estimated reading time, links, and media count.",
            isEnabled = false,
            installPath = extDir.absolutePath,
            manifestVersion = 3,
            contentScriptsJson = ContentScriptConfig.serializeList(contentScripts),
            popupRelativePath = "popup.html",
            iconRelativePath = null,
            author = "ExtenBrowser Team",
            matchesSummary = "<all_urls>",
            isBuiltIn = true
        )
    }
}
