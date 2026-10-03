package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConsoleLogItem
import com.example.data.model.HttpRequestItem
import com.example.data.model.LogLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevToolsConsoleSheet(
    logs: List<ConsoleLogItem>,
    httpRequests: List<HttpRequestItem>,
    onClearLogs: () -> Unit,
    onClearHttpRequests: () -> Unit,
    onEvalJs: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedFilter by remember { mutableStateOf<LogLevel?>(null) }
    var networkSearchQuery by remember { mutableStateOf("") }
    var jsInput by remember { mutableStateOf("") }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(580.dp)
                .padding(horizontal = 16.dp)
        ) {
            // Header Tabs
            SecondaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Console (${logs.size})")
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Http, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("HTTP Capture (${httpRequests.size})")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (selectedTab == 0) {
                // Console Tab
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedFilter == null,
                        onClick = { selectedFilter = null },
                        label = { Text("All (${logs.size})") }
                    )
                    FilterChip(
                        selected = selectedFilter == LogLevel.EXTENSION,
                        onClick = { selectedFilter = LogLevel.EXTENSION },
                        label = { Text("Extensions") }
                    )
                    FilterChip(
                        selected = selectedFilter == LogLevel.ERROR,
                        onClick = { selectedFilter = LogLevel.ERROR },
                        label = { Text("Errors") }
                    )
                    FilterChip(
                        selected = selectedFilter == LogLevel.WARN,
                        onClick = { selectedFilter = LogLevel.WARN },
                        label = { Text("Warnings") }
                    )
                    FilterChip(
                        selected = selectedFilter == LogLevel.INFO,
                        onClick = { selectedFilter = LogLevel.INFO },
                        label = { Text("Info") }
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onClearLogs, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear logs",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val filteredLogs = if (selectedFilter == null) logs else logs.filter { it.level == selectedFilter }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    if (filteredLogs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No console output recorded.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredLogs, key = { it.id }) { item ->
                                ConsoleLogRow(item = item, timeFormat = timeFormat)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // JS Interactive Runner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = jsInput,
                        onValueChange = { jsInput = it },
                        placeholder = { Text("Evaluate JavaScript expression...") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (jsInput.isNotBlank()) {
                                    onEvalJs(jsInput)
                                    jsInput = ""
                                }
                            }
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (jsInput.isNotBlank()) {
                                onEvalJs(jsInput)
                                jsInput = ""
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Run JS",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            } else {
                // HTTP / Network Request Capture Tab
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = networkSearchQuery,
                        onValueChange = { networkSearchQuery = it },
                        placeholder = { Text("Filter HTTP requests by URL / Host...") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onClearHttpRequests, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear network requests",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }

                val filteredRequests = if (networkSearchQuery.isBlank()) {
                    httpRequests
                } else {
                    httpRequests.filter { it.url.contains(networkSearchQuery, ignoreCase = true) || it.method.contains(networkSearchQuery, ignoreCase = true) }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(bottom = 16.dp)
                ) {
                    if (filteredRequests.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No HTTP requests captured yet.\nBrowse websites to capture requests live.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredRequests, key = { it.id }) { req ->
                                HttpRequestRow(
                                    item = req,
                                    timeFormat = timeFormat,
                                    onCopyUrl = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("URL", req.url))
                                        Toast.makeText(context, "Copied URL to clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HttpRequestRow(
    item: HttpRequestItem,
    timeFormat: SimpleDateFormat,
    onCopyUrl: () -> Unit
) {
    val methodColor = when (item.method) {
        "GET" -> Color(0xFF10B981)
        "POST" -> Color(0xFF3B82F6)
        "PUT" -> Color(0xFFF59E0B)
        "DELETE" -> Color(0xFFEF4444)
        else -> Color(0xFF8B5CF6)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCopyUrl),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.method,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                ),
                color = methodColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(methodColor.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.host,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.url,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = timeFormat.format(Date(item.timestamp)),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            IconButton(onClick = onCopyUrl, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ConsoleLogRow(
    item: ConsoleLogItem,
    timeFormat: SimpleDateFormat
) {
    val levelColor = when (item.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.WARN -> Color(0xFFF59E0B)
        LogLevel.EXTENSION -> MaterialTheme.colorScheme.primary
        LogLevel.NETWORK -> Color(0xFF06B6D4)
        LogLevel.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val badgeText = when (item.level) {
        LogLevel.ERROR -> "ERR"
        LogLevel.WARN -> "WARN"
        LogLevel.EXTENSION -> "EXT"
        LogLevel.NETWORK -> "NET"
        LogLevel.INFO -> "LOG"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                ),
                color = levelColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(levelColor.copy(alpha = 0.15f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.source,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = timeFormat.format(Date(item.timestamp)),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.message,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
