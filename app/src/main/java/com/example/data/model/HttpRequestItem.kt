package com.example.data.model

import java.util.UUID

data class HttpRequestItem(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isForMainFrame: Boolean = false,
    val host: String = try {
        val uri = java.net.URI(url)
        uri.host ?: url
    } catch (e: Exception) { url }
)
