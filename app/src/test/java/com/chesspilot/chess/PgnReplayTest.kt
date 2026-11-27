package com.chesspilot.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PgnReplayTest {
    @Test
    fun replaysOpeningMainlineAndProducesFullFen() {
        val result = PgnReplay.replaySanMovetext(
            "1. e4 e5 2. Nf3 Nc6 3. Bb5 a6"
        )

        assertTrue("Expected valid replay, got $result", result is PgnReplayResult.Valid)
        val snapshot = (result as PgnReplayResult.Valid).snapshot

        assertEquals(6, snapshot.sanMoves.size)
        assertEquals(6, snapshot.positionsAfterEachPly.size)
        assertEquals(
            "r1bqkbnr/1ppp1ppp/p1n5/1B2p3/4P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 0 4",
            snapshot.finalFen
        )
    }

    @Test
    fun preservesCastlingRightsAndCountersFromMoveHistory() {
        val result = PgnReplay.replaySanMovetext("1. e4 e5 2. Ke2")
        assertTrue("Expected valid replay, got $result", result is PgnReplayResult.Valid)

        val finalFen = (result as PgnReplayResult.Valid).snapshot.finalFen
        assertTrue(finalFen.contains(" b kq - 1 2"))
    }

    @Test
    fun rejectsEmptyOrOversizedInput() {
        assertTrue(PgnReplay.replaySanMovetext("   ") is PgnReplayResult.Invalid)
        assertTrue(
            PgnReplay.replaySanMovetext("e4 ".repeat(70_000)) is PgnReplayResult.Invalid
        )
    }

    @Test
    fun rejectsInvalidSanSequenceAtTheStartPosition() {
        val result = PgnReplay.replaySanMovetext("1. e5")
        assertTrue("Expected invalid SAN to be rejected, got $result", result is PgnReplayResult.Invalid)
    }

    @Test
    fun rejectsAnIllegalMoveLaterInTheSequence() {
        val result = PgnReplay.replaySanMovetext("1. e4 e5 2. e5")
        assertTrue("Expected illegal continuation to be rejected, got $result", result is PgnReplayResult.Invalid)
    }

    @Test
    fun recordsEachIntermediatePositionIncludingEnPassantState() {
        val result = PgnReplay.replaySanMovetext("1. e4 e5 2. Nf3")
        assertTrue("Expected valid replay, got $result", result is PgnReplayResult.Valid)

        val positions = (result as PgnReplayResult.Valid).snapshot.positionsAfterEachPly
        assertEquals(3, positions.size)

        // FEN records the e3 target square after the white pawn's two-square
        // advance even when no black pawn can currently capture en-passant.
        assertTrue("Unexpected FEN after 1.e4: ${positions[0]}", positions[0].contains(" b KQkq e3 0 1"))
        assertTrue("Unexpected FEN after 2.Nf3: ${positions[2]}", positions[2].contains(" b KQkq - 1 2"))
    }
}
