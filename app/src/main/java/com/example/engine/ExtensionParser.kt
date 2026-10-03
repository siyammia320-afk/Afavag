package com.example.engine

import android.content.Context
import android.net.Uri
import com.example.data.model.ContentScriptConfig
import com.example.data.model.ExtensionEntity
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

object ExtensionParser {

    /**
     * Unzips an extension from a given InputStream into an internal storage directory
     * and extracts manifest information with robust ZipException handling.
     */
    fun installFromZip(context: Context, inputStream: InputStream, customName: String? = null): ExtensionEntity {
        val extensionId = UUID.randomUUID().toString()
        val extensionsDir = File(context.filesDir, "extensions")
        if (!extensionsDir.exists()) extensionsDir.mkdirs()

        val targetDir = File(extensionsDir, extensionId)
        if (!targetDir.exists()) targetDir.mkdirs()

        val canonicalDestDirPath = targetDir.canonicalPath
        try {
            ZipInputStream(inputStream).use { zis ->
                var entry: ZipEntry? = try { zis.nextEntry } catch (e: Exception) { null }
                while (entry != null) {
                    try {
                        val entryName = entry.name.replace("\\", "/")
                        if (!entryName.contains("__MACOSX") && !entryName.startsWith(".")) {
                            val destFile = File(targetDir, entryName)
                            val canonicalDestFile = destFile.canonicalPath
                            if (canonicalDestFile.startsWith(canonicalDestDirPath + File.separator) || canonicalDestFile == canonicalDestDirPath) {
                                if (entry.isDirectory) {
                                    destFile.mkdirs()
                                } else {
                                    destFile.parentFile?.mkdirs()
                                    FileOutputStream(destFile).use { fos ->
                                        zis.copyTo(fos)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Skip corrupted/invalid entry safely
                    }
                    try {
                        zis.closeEntry()
                    } catch (e: Exception) {}
                    entry = try { zis.nextEntry } catch (e: Exception) { null }
                }
            }
        } catch (e: ZipException) {
            throw IllegalArgumentException("Invalid or corrupted ZIP archive (invalid stored block lengths)", e)
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to extract archive: ${e.localizedMessage}", e)
        }

        // Find manifest.json (could be in root or a single nested root folder)
        var manifestFile = File(targetDir, "manifest.json")
        var effectiveRootDir = targetDir

        if (!manifestFile.exists()) {
            val subFiles = targetDir.listFiles()
            if (subFiles != null && subFiles.size == 1 && subFiles[0].isDirectory) {
                val nestedManifest = File(subFiles[0], "manifest.json")
                if (nestedManifest.exists()) {
                    manifestFile = nestedManifest
                    effectiveRootDir = subFiles[0]
                }
            } else if (subFiles != null) {
                for (sub in subFiles) {
                    if (sub.isDirectory) {
                        val nested = File(sub, "manifest.json")
                        if (nested.exists()) {
                            manifestFile = nested
                            effectiveRootDir = sub
                            break
                        }
                    }
                }
            }
        }

        if (!manifestFile.exists()) {
            return generateFallbackExtension(context, effectiveRootDir, extensionId, customName)
        }

        val manifestJson = try {
            manifestFile.readText()
        } catch (e: Exception) {
            "{}"
        }
        return parseManifest(effectiveRootDir, extensionId, manifestJson, customName)
    }

    fun parseManifest(
        rootDir: File,
        extensionId: String,
        manifestJson: String,
        overrideName: String? = null
    ): ExtensionEntity {
        val json = try { JSONObject(manifestJson) } catch (e: Exception) { JSONObject() }
        val manifestVersion = json.optInt("manifest_version", 3)
        val name = overrideName ?: json.optString("name", "Untitled Extension")
        val version = json.optString("version", "1.0.0")
        val description = json.optString("description", "Imported Web Extension")
        val author = json.optString("author", "WebExtension Developer")

        val contentScriptsList = mutableListOf<ContentScriptConfig>()
        val contentScriptsArray = json.optJSONArray("content_scripts")
        if (contentScriptsArray != null) {
            for (i in 0 until contentScriptsArray.length()) {
                try {
                    val scriptObj = contentScriptsArray.getJSONObject(i)
                    contentScriptsList.add(ContentScriptConfig.fromJson(scriptObj))
                } catch (e: Exception) {}
            }
        }

        var popupPath: String? = null
        if (json.has("action")) {
            val actionObj = json.optJSONObject("action")
            popupPath = actionObj?.optString("default_popup", null)
        }
        if (popupPath == null && json.has("browser_action")) {
            val browserActionObj = json.optJSONObject("browser_action")
            popupPath = browserActionObj?.optString("default_popup", null)
        }

        var iconPath: String? = null
        val iconsObj = json.optJSONObject("icons")
        if (iconsObj != null) {
            iconPath = iconsObj.optString("128", null)
                ?: iconsObj.optString("48", null)
                ?: iconsObj.optString("32", null)
                ?: iconsObj.optString("16", null)
        }

        val matchesSummary = if (contentScriptsList.isNotEmpty()) {
            contentScriptsList.flatMap { it.matches }.distinct().take(3).joinToString(", ")
        } else {
            "<all_urls>"
        }

        return ExtensionEntity(
            id = extensionId,
            name = name,
            version = version,
            description = description,
            isEnabled = true,
            installPath = rootDir.absolutePath,
            manifestVersion = manifestVersion,
            contentScriptsJson = ContentScriptConfig.serializeList(contentScriptsList),
            popupRelativePath = popupPath,
            iconRelativePath = iconPath,
            author = author,
            matchesSummary = matchesSummary,
            isBuiltIn = false
        )
    }

    private fun generateFallbackExtension(
        context: Context,
        rootDir: File,
        extensionId: String,
        customName: String?
    ): ExtensionEntity {
        val jsFiles = mutableListOf<String>()
        val cssFiles = mutableListOf<String>()
        try {
            rootDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val relPath = file.relativeTo(rootDir).path
                if (file.extension.equals("js", ignoreCase = true)) {
                    jsFiles.add(relPath)
                } else if (file.extension.equals("css", ignoreCase = true)) {
                    cssFiles.add(relPath)
                }
            }
        } catch (e: Exception) {}

        val contentScripts = listOf(
            ContentScriptConfig(
                matches = listOf("<all_urls>"),
                jsFiles = jsFiles,
                cssFiles = cssFiles,
                runAt = "document_idle"
            )
        )

        val manifestObj = JSONObject()
        try {
            manifestObj.put("manifest_version", 3)
            manifestObj.put("name", customName ?: "Imported Script Package")
            manifestObj.put("version", "1.0.0")
            manifestObj.put("description", "Auto-generated manifest from uploaded files")
            File(rootDir, "manifest.json").writeText(manifestObj.toString(2))
        } catch (e: Exception) {}

        return ExtensionEntity(
            id = extensionId,
            name = customName ?: "Imported Script Package",
            version = "1.0.0",
            description = "Auto-generated manifest for ${jsFiles.size} scripts & ${cssFiles.size} styles",
            isEnabled = true,
            installPath = rootDir.absolutePath,
            manifestVersion = 3,
            contentScriptsJson = ContentScriptConfig.serializeList(contentScripts),
            popupRelativePath = null,
            iconRelativePath = null,
            author = "User",
            matchesSummary = "<all_urls>",
            isBuiltIn = false
        )
    }

    fun createCustomExtension(
        context: Context,
        name: String,
        description: String,
        matchPattern: String,
        jsCode: String,
        cssCode: String,
        popupHtml: String? = null
    ): ExtensionEntity {
        val extensionId = UUID.randomUUID().toString()
        val extensionsDir = File(context.filesDir, "extensions")
        if (!extensionsDir.exists()) extensionsDir.mkdirs()

        val targetDir = File(extensionsDir, extensionId)
        targetDir.mkdirs()

        File(targetDir, "content.js").writeText(jsCode)
        File(targetDir, "style.css").writeText(cssCode)

        var popupPath: String? = null
        if (!popupHtml.isNullOrBlank()) {
            popupPath = "popup.html"
            File(targetDir, "popup.html").writeText(popupHtml)
        }

        val contentScripts = listOf(
            ContentScriptConfig(
                matches = listOf(if (matchPattern.isBlank()) "<all_urls>" else matchPattern),
                jsFiles = listOf("content.js"),
                cssFiles = if (cssCode.isNotBlank()) listOf("style.css") else emptyList(),
                runAt = "document_idle"
            )
        )

        val manifestObj = JSONObject().apply {
            put("manifest_version", 3)
            put("name", name)
            put("version", "1.0.0")
            put("description", description)
            if (popupPath != null) {
                val actionObj = JSONObject().apply {
                    put("default_popup", popupPath)
                }
                put("action", actionObj)
            }
        }
        File(targetDir, "manifest.json").writeText(manifestObj.toString(2))

        return ExtensionEntity(
            id = extensionId,
            name = name,
            version = "1.0.0",
            description = description,
            isEnabled = true,
            installPath = targetDir.absolutePath,
            manifestVersion = 3,
            contentScriptsJson = ContentScriptConfig.serializeList(contentScripts),
            popupRelativePath = popupPath,
            iconRelativePath = null,
            author = "Local User",
            matchesSummary = matchPattern.ifBlank { "<all_urls>" },
            isBuiltIn = false
        )
    }
}
