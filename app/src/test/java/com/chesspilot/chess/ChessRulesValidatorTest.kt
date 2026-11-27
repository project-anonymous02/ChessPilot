package com.chesspilot.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessRulesValidatorTest {
    private val startFen =
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    @Test
    fun initialPositionHasTwentyLegalMoves() {
        val result = ChessRulesValidator.inspect(startFen)
        assertTrue("Expected valid position, got $result", result is ChessRulesResult.Valid)

        val snapshot = (result as ChessRulesResult.Valid).snapshot
        assertEquals(20, snapshot.legalMovesUci.size)
        assertTrue(snapshot.legalMovesUci.contains("e2e4"))
        assertTrue(snapshot.legalMovesUci.contains("g1f3"))
    }

    @Test
    fun acceptsLegalOpeningMoveAndRejectsIllegalOne() {
        assertTrue(ChessRulesValidator.isLegalMove(startFen, "e2e4"))
        assertTrue(ChessRulesValidator.isLegalMove(startFen, "g1f3"))
        assertFalse(ChessRulesValidator.isLegalMove(startFen, "e2e5"))
        assertFalse(ChessRulesValidator.isLegalMove(startFen, "e2e4q"))
    }

    @Test
    fun rejectsMalformedUciNotation() {
        assertFalse(ChessRulesValidator.isLegalMove(startFen, "Nf3"))
        assertFalse(ChessRulesValidator.isLegalMove(startFen, "e9e4"))
        assertFalse(ChessRulesValidator.isLegalMove(startFen, ""))
    }

    @Test
    fun rejectsStructurallyInvalidFenBeforeCallingRulesLibrary() {
        val result = ChessRulesValidator.inspect("8/8/8/8/8/8/8/8 w - - 0 1")
        assertTrue(result is ChessRulesResult.Invalid)
    }

    @Test
    fun handlesPositionAfterE4() {
        val afterE4 =
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"
        val result = ChessRulesValidator.inspect(afterE4)
        assertTrue("Expected valid position, got $result", result is ChessRulesResult.Valid)

        val moves = (result as ChessRulesResult.Valid).snapshot.legalMovesUci
        assertEquals(20, moves.size)
        assertTrue(moves.contains("e7e5"))
        assertFalse(moves.contains("e2e4"))
    }
}
