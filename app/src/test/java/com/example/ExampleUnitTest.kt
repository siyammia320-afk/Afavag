package com.example

import com.example.engine.ExtensionMatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testExtensionMatcher_allUrls() {
        assertTrue(ExtensionMatcher.matchesPattern("https://example.com/page", "<all_urls>"))
        assertTrue(ExtensionMatcher.matchesPattern("http://google.com/", "*://*/*"))
    }

    @Test
    fun testExtensionMatcher_domainSpecific() {
        assertTrue(ExtensionMatcher.matchesPattern("https://sub.domain.com/path", "*://*.domain.com/*"))
        assertTrue(ExtensionMatcher.matchesPattern("https://domain.com/path", "*://*.domain.com/*"))
    }
}
