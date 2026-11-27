package com.chesspilot.chess

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.move.MoveList

/**
 * Replays standard-start PGN SAN movetext into a verified board history.
 *
 * This accepts SAN mainline movetext (for example, "1. e4 e5 2. Nf3"),
 * not arbitrary full PGN documents with headers, comments, or variations.
 * Those must be normalized by a dedicated PGN document parser first.
 */
data class PgnReplaySnapshot(
    val finalFen: String,
    val sanMoves: List<String>,
    val positionsAfterEachPly: List<String>
)

sealed interface PgnReplayResult {
    data class Valid(val snapshot: PgnReplaySnapshot) : PgnReplayResult
    data class Invalid(val reason: String) : PgnReplayResult
}

object PgnReplay {
    private const val MAX_MOVETEXT_LENGTH = 200_000
    private const val MAX_PLIES = 1_000

    fun replaySanMovetext(movetext: String): PgnReplayResult {
        val normalized = movetext.trim()
        if (normalized.isEmpty()) {
            return invalid("Notasi langkah masih kosong.")
        }
        if (normalized.length > MAX_MOVETEXT_LENGTH) {
            return invalid("Notasi langkah terlalu panjang.")
        }

        return try {
            val moveList = MoveList()
            moveList.loadFromSan(normalized)

            if (moveList.isEmpty()) {
                return invalid("Tidak ada langkah catur yang dapat diputar ulang.")
            }
            if (moveList.size > MAX_PLIES) {
                return invalid("Jumlah langkah melampaui batas impor.")
            }

            val board = Board()
            val sanMoves = ArrayList<String>(moveList.size)
            val positions = ArrayList<String>(moveList.size)

            for (move in moveList) {
                // Board.isMoveLegal validates board-state consistency, but it is
                // not the same as membership in the generated legal move list.
                // Compare against legalMoves() to reject SAN that resolves to
                // an impossible move from the current side-to-move position.
                val moveUci = move.toString().lowercase()
                val legalMoveUcis = board.legalMoves()
                    .map { legalMove -> legalMove.toString().lowercase() }
                    .toHashSet()

                if (moveUci !in legalMoveUcis) {
                    return invalid("Urutan langkah mengandung langkah ilegal: $moveUci.")
                }

                if (!board.doMove(move)) {
                    return invalid("Langkah tidak dapat diterapkan: $moveUci.")
                }

                sanMoves.add(moveList.toSanArray()[sanMoves.size])
                positions.add(board.getFen())
            }

            PgnReplayResult.Valid(
                PgnReplaySnapshot(
                    finalFen = board.getFen(),
                    sanMoves = sanMoves,
                    positionsAfterEachPly = positions
                )
            )
        } catch (exception: Exception) {
            invalid(exception.message ?: "Notasi langkah tidak dapat dibaca.")
        }
    }

    private fun invalid(reason: String) = PgnReplayResult.Invalid(reason)
}
