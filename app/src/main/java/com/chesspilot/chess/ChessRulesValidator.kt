package com.chesspilot.chess

import com.github.bhlangonijr.chesslib.Board

/**
 * Uses Chesslib for legal move generation after our stricter FEN structure gate.
 *
 * This reports moves that Chesslib considers legal from the parsed board.
 * It does not establish that a position was reached through an authentic game
 * history; that requires replaying and validating the PGN move sequence.
 */
data class ChessRulesSnapshot(
    val normalizedFen: String,
    /** Standard-chess UCI moves, for example "e2e4" or "e7e8q". */
    val legalMovesUci: List<String>
)

sealed interface ChessRulesResult {
    data class Valid(val snapshot: ChessRulesSnapshot) : ChessRulesResult
    data class Invalid(val reason: String) : ChessRulesResult
}

object ChessRulesValidator {
    private val uciMovePattern =
        Regex("^[a-h][1-8][a-h][1-8][qrbn]?$", RegexOption.IGNORE_CASE)

    fun inspect(fen: String): ChessRulesResult {
        when (val structural = FenParser.parse(fen)) {
            is FenParseResult.Invalid -> return ChessRulesResult.Invalid(structural.reason)
            is FenParseResult.Valid -> Unit
        }

        return try {
            val board = Board()
            board.loadFromFen(fen)

            val legalMoves = board.legalMoves()
                .map { move -> move.toString().lowercase() }
                .sorted()

            ChessRulesResult.Valid(
                ChessRulesSnapshot(
                    normalizedFen = board.getFen(),
                    legalMovesUci = legalMoves
                )
            )
        } catch (exception: Exception) {
            ChessRulesResult.Invalid(
                exception.message ?: "Chesslib tidak dapat memuat posisi FEN."
            )
        }
    }

    fun isLegalMove(fen: String, uciMove: String): Boolean {
        if (!uciMovePattern.matches(uciMove)) return false

        val result = inspect(fen)
        val snapshot = (result as? ChessRulesResult.Valid)?.snapshot ?: return false
        return uciMove.lowercase() in snapshot.legalMovesUci
    }
}
