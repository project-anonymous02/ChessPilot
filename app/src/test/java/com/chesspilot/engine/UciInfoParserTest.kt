package com.chesspilot.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UciInfoParserTest {
    @Test
    fun parsesCentipawnInfoAndPrincipalVariation() {
        val info = UciInfoParser.parseInfo(
            "info depth 14 seldepth 20 multipv 1 score cp 36 nodes 12345 pv e2e4 e7e5 g1f3"
        )
        assertEquals(14, info?.depth)
        assertEquals("cp 36", info?.score)
        assertEquals(listOf("e2e4", "e7e5", "g1f3"), info?.principalVariation)
    }

    @Test
    fun parsesMateScore() {
        val info = UciInfoParser.parseInfo("info depth 9 score mate -3 pv e2e4")
        assertEquals("mate -3", info?.score)
    }

    @Test
    fun ignoresMalformedInfo() {
        assertNull(UciInfoParser.parseInfo("readyok"))
        assertNull(UciInfoParser.parseInfo("info depth nope score cp x"))
    }

    @Test
    fun parsesBestMoveAndPonder() {
        assertEquals(
            "e2e4" to "e7e5",
            UciInfoParser.parseBestMove("bestmove e2e4 ponder e7e5")
        )
    }

    @Test
    fun rejectsMalformedBestMove() {
        assertNull(UciInfoParser.parseBestMove("bestmove not-a-move"))
        assertTrue(UciInfoParser.parseBestMove("bestmove (none)") != null)
    }
}
