package com.chesspilot.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class ChessBoardPositionTest {
    private val initialFen =
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    @Test
    fun decodesInitialPositionWithCorrectBoardOrientation() {
        val position = ChessBoardPosition.parse(initialFen)
        assertNotNull(position)
        assertEquals('r', position?.pieceAt("a8"))
        assertEquals('k', position?.pieceAt("e8"))
        assertEquals('P', position?.pieceAt("a2"))
        assertEquals('K', position?.pieceAt("e1"))
        assertNull(position?.pieceAt("e4"))
    }

    @Test
    fun decodesEmptySquaresAndAllPieceSymbols() {
        val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        val position = ChessBoardPosition.parse(fen)
        assertNotNull(position)
        assertEquals('N', position?.pieceAt("b1"))
        assertEquals('n', position?.pieceAt("b8"))
    }

    @Test
    fun rejectsMalformedBoardPlacement() {
        assertNull(ChessBoardPosition.parse("8/8/8/8/8/8/8 w - - 0 1"))
        assertNull(ChessBoardPosition.parse("9/8/8/8/8/8/8/8 w - - 0 1"))
        assertNull(ChessBoardPosition.parse("9/8/8/8/8/8/8/7K w - - 0 1"))
        assertNull(ChessBoardPosition.parse("8/8/8/8/8/8/8/7X w - - 0 1"))
    }

    @Test
    fun rejectsIncorrectFieldCount() {
        assertNull(ChessBoardPosition.parse("8/8/8/8/8/8/8/8 w - - 0"))
    }
}
