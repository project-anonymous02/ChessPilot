package com.chesspilot.security

import java.util.Locale

/**
 * Strict navigation-origin policy for the embedded Chess.com browser.
 *
 * Exact apex host and true subdomains are allowed. Suffix lookalikes,
 * HTTP, missing hosts, and unrelated origins are rejected.
 */
object ChessComHostPolicy {
    fun isAllowed(scheme: String?, host: String?): Boolean {
        if (!scheme.equals("https", ignoreCase = true)) return false

        val normalizedHost = host
            ?.trim()
            ?.trimEnd('.')
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotEmpty() }
            ?: return false

        return normalizedHost == "chess.com" ||
            normalizedHost.endsWith(".chess.com")
    }
}
