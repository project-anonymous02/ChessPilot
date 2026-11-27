package com.chesspilot.chess

/**
 * Strict structural FEN parser.
 *
 * It validates the six FEN fields and board consistency. It is not a complete
 * legal-position validator: checks, pins, repetition, and legal move generation
 * belong to the chess rules/engine layer.
 */
data class ChessPosition(
    /** 64 squares ordered from rank 8 to rank 1, file a to h; null means empty. */
    val squares: List<Char?>,
    val activeColor: Char,
    val castlingRights: String,
    val enPassantTarget: String?,
    val halfmoveClock: Int,
    val fullmoveNumber: Int
) {
    fun pieceAt(square: String): Char? {
        if (square.length != 2) return null
        val file = square[0] - 'a'
        val rank = square[1] - '1'
        if (file !in 0..7 || rank !in 0..7) return null
        return squares[(7 - rank) * 8 + file]
    }

    fun toFen(): String {
        val placement = (0..7).joinToString("/") { rankIndex ->
            val row = StringBuilder()
            var emptyCount = 0

            for (file in 0..7) {
                val piece = squares[rankIndex * 8 + file]
                if (piece == null) {
                    emptyCount++
                } else {
                    if (emptyCount > 0) {
                        row.append(emptyCount)
                        emptyCount = 0
                    }
                    row.append(piece)
                }
            }

            if (emptyCount > 0) row.append(emptyCount)
            row.toString()
        }

        return listOf(
            placement,
            activeColor.toString(),
            castlingRights,
            enPassantTarget ?: "-",
            halfmoveClock.toString(),
            fullmoveNumber.toString()
        ).joinToString(" ")
    }
}

sealed interface FenParseResult {
    data class Valid(val position: ChessPosition) : FenParseResult
    data class Invalid(val reason: String) : FenParseResult
}

object FenParser {
    private const val PIECES = "pnbrqkPNBRQK"
    private const val CASTLING_ORDER = "KQkq"

    fun parse(fen: String): FenParseResult {
        val fields = fen.trim().split(Regex("\\s+"))
        if (fields.size != 6) {
            return invalid("FEN harus memiliki tepat enam bagian.")
        }

        val squares = MutableList<Char?>(64) { null }
        val pieceCounts = mutableMapOf('w' to 0, 'b' to 0)
        val pawnCounts = mutableMapOf('w' to 0, 'b' to 0)
        val kingCounts = mutableMapOf('w' to 0, 'b' to 0)

        val ranks = fields[0].split("/")
        if (ranks.size != 8) {
            return invalid("Susunan papan harus memiliki delapan baris.")
        }

        for (rankIndex in ranks.indices) {
            val rankText = ranks[rankIndex]
            var fileIndex = 0
            var previousWasDigit = false

            for (symbol in rankText) {
                when {
                    symbol in '1'..'8' -> {
                        if (previousWasDigit) {
                            return invalid("Angka kotak kosong yang berurutan tidak valid dalam FEN.")
                        }
                        fileIndex += symbol.digitToInt()
                        previousWasDigit = true
                    }

                    symbol in PIECES -> {
                        if (fileIndex >= 8) {
                            return invalid("Baris papan melebihi delapan kotak.")
                        }
                        if ((rankIndex == 0 || rankIndex == 7) &&
                            symbol.lowercaseChar() == 'p'
                        ) {
                            return invalid("Pion tidak boleh berada di baris pertama atau kedelapan.")
                        }

                        squares[rankIndex * 8 + fileIndex] = symbol
                        fileIndex++
                        previousWasDigit = false

                        val color = if (symbol.isUpperCase()) 'w' else 'b'
                        pieceCounts[color] = pieceCounts.getValue(color) + 1

                        if (symbol.lowercaseChar() == 'p') {
                            pawnCounts[color] = pawnCounts.getValue(color) + 1
                        }
                        if (symbol.lowercaseChar() == 'k') {
                            kingCounts[color] = kingCounts.getValue(color) + 1
                        }
                    }

                    else -> {
                        return invalid("Karakter bidak tidak dikenal: '$symbol'.")
                    }
                }

                if (fileIndex > 8) {
                    return invalid("Baris papan melebihi delapan kotak.")
                }
            }

            if (fileIndex != 8) {
                return invalid("Setiap baris papan harus berjumlah tepat delapan kotak.")
            }
        }

        if (kingCounts.getValue('w') != 1 || kingCounts.getValue('b') != 1) {
            return invalid("Posisi harus memiliki tepat satu raja untuk setiap warna.")
        }
        if (pieceCounts.values.any { it > 16 }) {
            return invalid("Setiap warna tidak boleh memiliki lebih dari 16 bidak.")
        }
        if (pawnCounts.values.any { it > 8 }) {
            return invalid("Setiap warna tidak boleh memiliki lebih dari delapan pion.")
        }

        val activeColor = fields[1].singleOrNull()
            ?: return invalid("Giliran harus berupa 'w' atau 'b'.")
        if (activeColor != 'w' && activeColor != 'b') {
            return invalid("Giliran harus berupa 'w' atau 'b'.")
        }

        val castling = fields[2]
        if (castling != "-") {
            if (castling.any { it !in CASTLING_ORDER }) {
                return invalid("Hak rokade hanya boleh berisi K, Q, k, q, atau '-'.")
            }
            if (castling.toSet().size != castling.length) {
                return invalid("Hak rokade tidak boleh berisi karakter duplikat.")
            }

            // CASTLING_ORDER.filter returns a String; do not call joinToString on it.
            val canonicalCastling = CASTLING_ORDER.filter { it in castling }
            if (castling != canonicalCastling) {
                return invalid("Urutan hak rokade harus KQkq.")
            }

            if ('K' in castling && (squares[60] != 'K' || squares[63] != 'R')) {
                return invalid("Hak rokade putih sisi raja tidak cocok dengan posisi raja/benteng.")
            }
            if ('Q' in castling && (squares[60] != 'K' || squares[56] != 'R')) {
                return invalid("Hak rokade putih sisi ratu tidak cocok dengan posisi raja/benteng.")
            }
            if ('k' in castling && (squares[4] != 'k' || squares[7] != 'r')) {
                return invalid("Hak rokade hitam sisi raja tidak cocok dengan posisi raja/benteng.")
            }
            if ('q' in castling && (squares[4] != 'k' || squares[0] != 'r')) {
                return invalid("Hak rokade hitam sisi ratu tidak cocok dengan posisi raja/benteng.")
            }
        }

        val enPassant = if (fields[3] == "-") {
            null
        } else {
            val target = fields[3]
            if (!target.matches(Regex("[a-h][36]"))) {
                return invalid("Kotak en-passant harus berupa file a-h pada baris 3 atau 6.")
            }

            val expectedRank = if (activeColor == 'w') '6' else '3'
            if (target[1] != expectedRank) {
                return invalid("Baris en-passant tidak cocok dengan pihak yang mendapat giliran.")
            }

            val file = target[0] - 'a'
            val targetRankIndex = 8 - target[1].digitToInt()
            val targetIndex = targetRankIndex * 8 + file
            if (squares[targetIndex] != null) {
                return invalid("Kotak en-passant harus kosong.")
            }

            val pawnRankIndex = if (activeColor == 'w') 3 else 4
            val pawnIndex = pawnRankIndex * 8 + file
            val originRankIndex = if (activeColor == 'w') 1 else 6
            val originIndex = originRankIndex * 8 + file
            val expectedPawn = if (activeColor == 'w') 'p' else 'P'

            if (squares[pawnIndex] != expectedPawn || squares[originIndex] != null) {
                return invalid("En-passant tidak didukung oleh langkah pion sebelumnya.")
            }

            target
        }

        if (!fields[4].matches(Regex("[0-9]+"))) {
            return invalid("Halfmove clock harus bilangan bulat non-negatif.")
        }
        val halfmoveClock = fields[4].toIntOrNull()
            ?: return invalid("Halfmove clock berada di luar rentang.")
        if (halfmoveClock < 0) {
            return invalid("Halfmove clock tidak boleh negatif.")
        }

        if (!fields[5].matches(Regex("[0-9]+"))) {
            return invalid("Fullmove number harus bilangan bulat positif.")
        }
        val fullmoveNumber = fields[5].toIntOrNull()
            ?: return invalid("Fullmove number berada di luar rentang.")
        if (fullmoveNumber < 1) {
            return invalid("Fullmove number minimal 1.")
        }

        return FenParseResult.Valid(
            ChessPosition(
                squares = squares.toList(),
                activeColor = activeColor,
                castlingRights = castling,
                enPassantTarget = enPassant,
                halfmoveClock = halfmoveClock,
                fullmoveNumber = fullmoveNumber
            )
        )
    }

    private fun invalid(reason: String) = FenParseResult.Invalid(reason)
}
