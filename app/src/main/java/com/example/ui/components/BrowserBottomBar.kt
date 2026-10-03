package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.BrowserTab

@Composable
fun BrowserBottomBar(
    tab: BrowserTab?,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onGoHome: () -> Unit,
    onNewTab: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleDesktop: () -> Unit,
    onOpenHistoryBookmarks: () -> Unit,
    onOpenExtensionsHub: () -> Unit,
    onOpenConsoleLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back
            IconButton(
                onClick = onGoBack,
                enabled = tab?.canGoBack == true,
                modifier = Modifier.size(44.dp).testTag("btn_nav_back")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = if (tab?.canGoBack == true) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }

            // Forward
            IconButton(
                onClick = onGoForward,
                enabled = tab?.canGoForward == true,
                modifier = Modifier.size(44.dp).testTag("btn_nav_forward")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Forward",
                    tint = if (tab?.canGoForward == true) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }

            // Home
            IconButton(
                onClick = onGoHome,
                modifier = Modifier.size(44.dp).testTag("btn_nav_home")
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Home",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            // New Tab Quick Add
            IconButton(
                onClick = onNewTab,
                modifier = Modifier.size(44.dp).testTag("btn_nav_new_tab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Tab",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Bookmark
            IconButton(
                onClick = onToggleBookmark,
                modifier = Modifier.size(44.dp).testTag("btn_nav_bookmark")
            ) {
                Icon(
                    imageVector = if (tab?.isBookmarked == true) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = "Bookmark",
                    tint = if (tab?.isBookmarked == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }

            // Overflow Menu
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(44.dp).testTag("btn_nav_menu")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Menu",
                    tint = MaterialTheme.colorScheme.onSurface
                )

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (tab?.isDesktopMode == true) "Mobile Site" else "Desktop Site") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (tab?.isDesktopMode == true) Icons.Default.PhoneAndroid else Icons.Default.DesktopWindows,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            showMenu = false
                            onToggleDesktop()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Extensions & Scripts") },
                        leadingIcon = {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Bookmark,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            showMenu = false
                            onOpenExtensionsHub()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Bookmarks & History") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.BookmarkBorder,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            showMenu = false
                            onOpenHistoryBookmarks()
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("DevTools & Console") },
                        leadingIcon = {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Add,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            showMenu = false
                            onOpenConsoleLogs()
                        }
                    )
                }
            }
        }
    }
}
