package com.chesspilot.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FenParserTest {
    private fun valid(fen: String): ChessPosition {
        val result = FenParser.parse(fen)
        assertTrue("Expected valid FEN, got $result", result is FenParseResult.Valid)
        return (result as FenParseResult.Valid).position
    }

    private fun invalid(fen: String) {
        val result = FenParser.parse(fen)
        assertFalse("Expected invalid FEN, got $result", result is FenParseResult.Valid)
        assertTrue(result is FenParseResult.Invalid)
    }

    @Test
    fun parsesInitialPositionAndRoundTrips() {
        val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        val position = valid(fen)
        assertEquals('w', position.activeColor)
        assertEquals("KQkq", position.castlingRights)
        assertEquals('K', position.pieceAt("e1"))
        assertEquals('k', position.pieceAt("e8"))
        assertEquals(fen, position.toFen())
    }

    @Test
    fun parsesAValidPositionAfterOpeningMoves() {
        val fen = "rnbqkbnr/pp1ppppp/2p5/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2"
        assertEquals(fen, valid(fen).toFen())
    }

    @Test
    fun acceptsStructurallyConsistentEnPassantTarget() {
        val fen = "rnbqkbnr/1pp1pppp/p7/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 3"
        assertEquals("d6", valid(fen).enPassantTarget)
    }

    @Test
    fun rejectsWrongNumberOfFields() {
        invalid("8/8/8/8/8/8/8/8 w - - 0")
    }

    @Test
    fun rejectsWrongNumberOfRanks() {
        invalid("8/8/8/8/8/8/8 w - - 0 1")
    }

    @Test
    fun rejectsRankThatDoesNotContainEightSquares() {
        invalid("7/8/8/8/8/8/8/4K2k w - - 0 1")
    }

    @Test
    fun rejectsAdjacentEmptyRunDigits() {
        invalid("11111111/8/8/8/8/8/8/4K2k w - - 0 1")
    }

    @Test
    fun rejectsUnknownPieceSymbols() {
        invalid("8/8/8/8/8/8/8/4K2X w - - 0 1")
    }

    @Test
    fun requiresExactlyOneKingOfEachColor() {
        invalid("8/8/8/8/8/8/8/4K3 w - - 0 1")
        invalid("8/8/8/8/8/8/8/4KK1k w - - 0 1")
    }

    @Test
    fun rejectsPawnsOnFirstOrEighthRank() {
        invalid("P3k3/8/8/8/8/8/8/4K3 w - - 0 1")
        invalid("4k2p/8/8/8/8/8/8/4K3 w - - 0 1")
    }

    @Test
    fun rejectsMoreThanEightPawnsPerSide() {
        invalid("4k3/8/8/8/8/4P3/PPPPPPPP/4K3 w - - 0 1")
    }

    @Test
    fun rejectsInvalidActiveColor() {
        invalid("4k3/8/8/8/8/8/8/4K3 x - - 0 1")
    }

    @Test
    fun rejectsDuplicateOrInvalidCastlingRights() {
        invalid("r3k2r/8/8/8/8/8/8/R3K2R w KK - 0 1")
        invalid("r3k2r/8/8/8/8/8/8/R3K2R w KX - 0 1")
        invalid("r3k2r/8/8/8/8/8/8/R3K2R w qK - 0 1")
    }

    @Test
    fun rejectsCastlingRightWithoutTheRequiredRookAndKing() {
        invalid("r3k2r/8/8/8/8/8/8/4K3 w K - 0 1")
    }

    @Test
    fun rejectsEnPassantWithWrongRankOrMissingPawn() {
        invalid("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq e3 0 1")
        invalid("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq d6 0 1")
    }

    @Test
    fun rejectsEnPassantTargetThatIsOccupied() {
        invalid("rnbqkbnr/1pp1pppp/p2p4/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 3")
    }

    @Test
    fun rejectsNegativeOrInvalidMoveCounters() {
        invalid("4k3/8/8/8/8/8/8/4K3 w - - -1 1")
        invalid("4k3/8/8/8/8/8/8/4K3 w - - 0 0")
        invalid("4k3/8/8/8/8/8/8/4K3 w - - nope 1")
        invalid("4k3/8/8/8/8/8/8/4K3 w - - 0 +1")
    }

    @Test
    fun supportsSquareLookupsAndRejectsInvalidSquareNames() {
        val position = valid("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        assertEquals('P', position.pieceAt("a2"))
        assertEquals(null, position.pieceAt("a3"))
        assertEquals(null, position.pieceAt("z9"))
    }
}
