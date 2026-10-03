package com.example.engine

import java.util.regex.Pattern

object ExtensionMatcher {

    /**
     * Checks if a URL matches a list of extension match patterns.
     */
    fun matchesAny(url: String, patterns: List<String>): Boolean {
        if (patterns.isEmpty()) return true
        for (pattern in patterns) {
            if (matchesPattern(url, pattern)) {
                return true
            }
        }
        return false
    }

    fun matchesPattern(url: String, pattern: String): Boolean {
        val trimmed = pattern.trim()
        if (trimmed == "<all_urls>" || trimmed == "*://*/*" || trimmed == "*") {
            return true
        }

        return try {
            val regex = patternToRegex(trimmed)
            Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(url).matches()
        } catch (e: Exception) {
            val simplified = trimmed.replace("*", ".*")
            try {
                Pattern.compile(simplified, Pattern.CASE_INSENSITIVE).matcher(url).find()
            } catch (ex: Exception) {
                false
            }
        }
    }

    private fun patternToRegex(pattern: String): String {
        val schemeSplit = pattern.split("://", limit = 2)
        if (schemeSplit.size < 2) {
            return ".*" + Regex.escape(pattern).replace("\\*", ".*") + ".*"
        }

        val scheme = schemeSplit[0]
        val rest = schemeSplit[1]

        val schemeRegex = when (scheme) {
            "*" -> "(http|https)"
            else -> Regex.escape(scheme)
        }

        val slashIdx = rest.indexOf('/')
        val host: String
        val path: String
        if (slashIdx != -1) {
            host = rest.substring(0, slashIdx)
            path = rest.substring(slashIdx)
        } else {
            host = rest
            path = "/*"
        }

        val hostRegex = if (host == "*") {
            "[^/]+"
        } else if (host.startsWith("*.")) {
            "([^/]+\\.)?" + Regex.escape(host.substring(2))
        } else {
            Regex.escape(host)
        }

        val pathRegex = Regex.escape(path).replace("\\*", ".*")

        return "^$schemeRegex://$hostRegex$pathRegex$"
    }
}
