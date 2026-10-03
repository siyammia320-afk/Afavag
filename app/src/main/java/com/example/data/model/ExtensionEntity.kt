package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "extensions")
data class ExtensionEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val isEnabled: Boolean = true,
    val installPath: String, // Path in app filesDir
    val manifestVersion: Int = 3,
    val contentScriptsJson: String = "[]",
    val popupRelativePath: String? = null,
    val iconRelativePath: String? = null,
    val author: String = "Custom",
    val matchesSummary: String = "<all_urls>",
    val isBuiltIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class ContentScriptConfig(
    val matches: List<String>,
    val jsFiles: List<String>,
    val cssFiles: List<String>,
    val runAt: String = "document_idle", // "document_start", "document_end", "document_idle"
    val allFrames: Boolean = false
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        val matchesArray = JSONArray()
        matches.forEach { matchesArray.put(it) }
        obj.put("matches", matchesArray)

        val jsArray = JSONArray()
        jsFiles.forEach { jsArray.put(it) }
        obj.put("js", jsArray)

        val cssArray = JSONArray()
        cssFiles.forEach { cssArray.put(it) }
        obj.put("css", cssArray)

        obj.put("run_at", runAt)
        obj.put("all_frames", allFrames)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): ContentScriptConfig {
            val matchesList = mutableListOf<String>()
            val matchesArr = obj.optJSONArray("matches")
            if (matchesArr != null) {
                for (i in 0 until matchesArr.length()) {
                    matchesList.add(matchesArr.getString(i))
                }
            }

            val jsList = mutableListOf<String>()
            val jsArr = obj.optJSONArray("js")
            if (jsArr != null) {
                for (i in 0 until jsArr.length()) {
                    jsList.add(jsArr.getString(i))
                }
            }

            val cssList = mutableListOf<String>()
            val cssArr = obj.optJSONArray("css")
            if (cssArr != null) {
                for (i in 0 until cssArr.length()) {
                    cssList.add(cssArr.getString(i))
                }
            }

            return ContentScriptConfig(
                matches = if (matchesList.isEmpty()) listOf("<all_urls>") else matchesList,
                jsFiles = jsList,
                cssFiles = cssList,
                runAt = obj.optString("run_at", "document_idle"),
                allFrames = obj.optBoolean("all_frames", false)
            )
        }

        fun parseList(jsonStr: String): List<ContentScriptConfig> {
            val result = mutableListOf<ContentScriptConfig>()
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    result.add(fromJson(obj))
                }
            } catch (e: Exception) {}
            return result
        }

        fun serializeList(list: List<ContentScriptConfig>): String {
            val array = JSONArray()
            list.forEach { array.put(it.toJson()) }
            return array.toString()
        }
    }
}
