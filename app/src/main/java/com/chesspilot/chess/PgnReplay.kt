package com.chesspilot.chess

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.move.MoveList

/**
 * Replays standard-start PGN SAN movetext into a verified board history.
 *
 * This intentionally accepts SAN movetext (for example, "1. e4 e5 2. Nf3"),
 * not arbitrary full PGN documents. Tags/comments/variations must be normalized
 * by a dedicated PGN document parser before this API is called.
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

    private val resultToken = setOf("1-0", "0-1", "1/2-1/2", "*")

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
            val positions = ArrayList<String>(moveList.size)

            for (move in moveList) {
                if (!board.isMoveLegal(move, true)) {
                    return invalid("Urutan langkah mengandung langkah ilegal.")
                }
                board.doMove(move)
                positions.add(board.getFen())
            }

            PgnReplayResult.Valid(
                PgnReplaySnapshot(
                    finalFen = board.getFen(),
                    sanMoves = moveList.toSanArray().toList(),
                    positionsAfterEachPly = positions
                )
            )
        } catch (exception: Exception) {
            invalid(exception.message ?: "Notasi langkah tidak dapat dibaca.")
        }
    }

    private fun invalid(reason: String) = PgnReplayResult.Invalid(reason)
}
