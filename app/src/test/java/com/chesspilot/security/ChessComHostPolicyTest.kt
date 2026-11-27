package com.chesspilot.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessComHostPolicyTest {
    @Test
    fun allowsApexAndRealSubdomainsOverHttps() {
        assertTrue(ChessComHostPolicy.isAllowed("https", "chess.com"))
        assertTrue(ChessComHostPolicy.isAllowed("https", "www.chess.com"))
        assertTrue(ChessComHostPolicy.isAllowed("HTTPS", "play.chess.com"))
        assertTrue(ChessComHostPolicy.isAllowed("https", "accounts.chess.com"))
    }

    @Test
    fun rejectsHttpAndNonChessDomains() {
        assertFalse(ChessComHostPolicy.isAllowed("http", "chess.com"))
        assertFalse(ChessComHostPolicy.isAllowed("https", "evilchess.com"))
        assertFalse(ChessComHostPolicy.isAllowed("https", "chess.com.evil.example"))
        assertFalse(ChessComHostPolicy.isAllowed("https", "example.com"))
    }

    @Test
    fun rejectsMissingAndBlankHosts() {
        assertFalse(ChessComHostPolicy.isAllowed("https", null))
        assertFalse(ChessComHostPolicy.isAllowed("https", ""))
        assertFalse(ChessComHostPolicy.isAllowed("https", "   "))
    }

    @Test
    fun normalizesCaseAndTrailingDot() {
        assertTrue(ChessComHostPolicy.isAllowed("https", "WWW.CHESS.COM"))
        assertTrue(ChessComHostPolicy.isAllowed("https", "chess.com."))
    }
}
