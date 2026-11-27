package com.chesspilot.chess

/**
 * Immutable board projection for rendering. Squares are ordered from a8 to h1,
 * matching FEN rank order and keeping UI rendering independent from chesslib.
 */
data class ChessBoardPosition private constructor(
    private val squares: List<Char?>
) {
    fun pieceAt(square: String): Char? {
        if (square.length != 2) return null
        val file = square[0] - 'a'
        val rank = square[1] - '1'
        if (file !in 0..7 || rank !in 0..7) return null
        return squares[(7 - rank) * 8 + file]
    }

    fun pieceAtIndex(index: Int): Char? = squares.getOrNull(index)

    companion object {
        private const val PIECES = "pnbrqkPNBRQK"

        fun parse(fen: String): ChessBoardPosition? {
            val fields = fen.trim().split(Regex("\\s+"))
            if (fields.size != 6) return null
            val ranks = fields[0].split("/")
            if (ranks.size != 8) return null

            val board = ArrayList<Char?>(64)
            for (rank in ranks) {
                var fileCount = 0
                for (symbol in rank) {
                    when {
                        symbol in '1'..'8' -> {
                            val empty = symbol.digitToInt()
                            if (fileCount + empty > 8) return null
                            repeat(empty) { board.add(null) }
                            fileCount += empty
                        }
                        symbol in PIECES -> {
                            if (fileCount >= 8) return null
                            board.add(symbol)
                            fileCount++
                        }
                        else -> return null
                    }
                }
                if (fileCount != 8) return null
            }
            if (board.size != 64) return null
            return ChessBoardPosition(board.toList())
        }
    }
}
