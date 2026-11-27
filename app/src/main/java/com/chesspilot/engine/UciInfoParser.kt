package com.chesspilot.engine

data class UciInfo(
    val depth: Int?,
    val score: String?,
    val principalVariation: List<String>
)

/** Small, defensive parser for the UCI fields ChessPilot displays. */
object UciInfoParser {
    private val movePattern = Regex("^[a-h][1-8][a-h][1-8][qrbn]?$")

    fun parseInfo(line: String): UciInfo? {
        if (!line.startsWith("info ")) return null
        val tokens = line.trim().split(Regex("\\s+"))
        val depthIndex = tokens.indexOf("depth")
        val depth = tokens.getOrNull(depthIndex + 1)?.toIntOrNull()

        val scoreIndex = tokens.indexOf("score")
        val score = if (scoreIndex >= 0) {
            val kind = tokens.getOrNull(scoreIndex + 1)
            val value = tokens.getOrNull(scoreIndex + 2)?.toIntOrNull()
            if (kind in setOf("cp", "mate") && value != null) "$kind $value" else null
        } else null

        val pvIndex = tokens.indexOf("pv")
        val pv = if (pvIndex >= 0) {
            tokens.drop(pvIndex + 1).takeWhile { movePattern.matches(it) }
        } else emptyList()

        return UciInfo(depth, score, pv)
    }

    fun parseBestMove(line: String): Pair<String, String?>? {
        val tokens = line.trim().split(Regex("\\s+"))
        if (tokens.size < 2 || tokens[0] != "bestmove") return null
        val best = tokens[1]
        if (best != "(none)" && !movePattern.matches(best)) return null
        val ponderIndex = tokens.indexOf("ponder")
        val ponder = tokens.getOrNull(ponderIndex + 1)?.takeIf(movePattern::matches)
        return best to ponder
    }
}
