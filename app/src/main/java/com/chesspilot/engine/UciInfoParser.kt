package com.chesspilot.engine

data class UciInfo(
    val depth: Int?,
    val score: String?,
    val principalVariation: List<String>
)

/**
 * Defensive parser for the subset of UCI "info" fields displayed by ChessPilot.
 * Malformed recognized fields invalidate the line instead of being silently
 * converted into a partially valid analysis result.
 */
object UciInfoParser {
    private val movePattern = Regex("^[a-h][1-8][a-h][1-8][qrbn]?$")

    fun parseInfo(line: String): UciInfo? {
        if (!line.startsWith("info ")) return null

        val tokens = line.trim().split(Regex("\\s+"))
        if (tokens.firstOrNull() != "info") return null

        val depthIndex = tokens.indexOf("depth")
        val depth = if (depthIndex >= 0) {
            val parsed = tokens.getOrNull(depthIndex + 1)?.toIntOrNull()
            if (parsed == null || parsed <= 0) return null
            parsed
        } else {
            null
        }

        val scoreIndex = tokens.indexOf("score")
        val score = if (scoreIndex >= 0) {
            val kind = tokens.getOrNull(scoreIndex + 1)
            val value = tokens.getOrNull(scoreIndex + 2)?.toIntOrNull()
            if (kind !in setOf("cp", "mate") || value == null) return null
            "$kind $value"
        } else {
            null
        }

        val pvIndex = tokens.indexOf("pv")
        val pv = if (pvIndex >= 0) {
            val moves = tokens.drop(pvIndex + 1).takeWhile { movePattern.matches(it) }
            if (moves.isEmpty()) return null
            moves
        } else {
            emptyList()
        }

        if (depth == null && score == null && pv.isEmpty()) return null
        return UciInfo(depth, score, pv)
    }

    fun parseBestMove(line: String): Pair<String, String?>? {
        val tokens = line.trim().split(Regex("\\s+"))
        if (tokens.size < 2 || tokens[0] != "bestmove") return null

        val best = tokens[1]
        if (best != "(none)" && !movePattern.matches(best)) return null

        val ponderIndex = tokens.indexOf("ponder")
        val ponder = if (ponderIndex >= 0) {
            tokens.getOrNull(ponderIndex + 1)?.takeIf(movePattern::matches)
                ?: return null
        } else {
            null
        }
        return best to ponder
    }
}
