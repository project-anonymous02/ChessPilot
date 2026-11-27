package com.chesspilot.engine

import android.content.Context
import com.chesspilot.chess.ChessRulesResult
import com.chesspilot.chess.ChessRulesValidator
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class EngineAnalysis(
    val bestMoveUci: String,
    val ponderMoveUci: String?,
    val depth: Int?,
    val score: String?,
    val principalVariationUci: List<String>
)

sealed interface EngineResult {
    data class Success(val analysis: EngineAnalysis) : EngineResult
    data class Failure(val reason: String) : EngineResult
}

/**
 * Runs the pinned Stockfish Android executable through its UCI text protocol.
 * Calls are serialized; the native process never runs on the UI thread.
 */
class StockfishEngine(context: Context) : Closeable {
    private val appContext = context.applicationContext
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    @Volatile private var process: Process? = null
    @Volatile private var closed = false

    fun analyze(
        fen: String,
        depth: Int = 12,
        callback: (EngineResult) -> Unit
    ) {
        require(depth in 1..20) { "Depth harus berada di antara 1 dan 20." }
        executor.execute {
            callback(runAnalysis(fen, depth))
        }
    }

    @Synchronized
    private fun runAnalysis(fen: String, depth: Int): EngineResult {
        if (closed) return EngineResult.Failure("Engine sudah ditutup.")
        when (val validation = ChessRulesValidator.inspect(fen)) {
            is ChessRulesResult.Invalid -> return EngineResult.Failure(validation.reason)
            is ChessRulesResult.Valid -> Unit
        }

        val binary = File(appContext.applicationInfo.nativeLibraryDir, "libstockfish.so")
        if (!binary.isFile || !binary.canExecute()) {
            return EngineResult.Failure("Stockfish ARM64 tidak ditemukan atau tidak dapat dijalankan.")
        }

        var child: Process? = null
        return try {
            child = ProcessBuilder(binary.absolutePath)
                .redirectErrorStream(true)
                .start()
            process = child

            val writer = BufferedWriter(OutputStreamWriter(child.outputStream))
            val reader = BufferedReader(InputStreamReader(child.inputStream))

            fun send(command: String) {
                writer.write(command)
                writer.newLine()
                writer.flush()
            }

            fun waitForToken(token: String, maxLines: Int = 20_000) {
                repeat(maxLines) {
                    val line = reader.readLine()
                        ?: throw IllegalStateException("Stockfish berhenti sebelum mengirim $token.")
                    if (line.trim() == token) return
                    if (line.contains("CRITICAL ERROR", ignoreCase = true)) {
                        throw IllegalStateException(line)
                    }
                }
                throw IllegalStateException("Respons Stockfish tidak valid: $token tidak ditemukan.")
            }

            send("uci")
            waitForToken("uciok")
            send("setoption name Threads value 2")
            send("setoption name Hash value 48")
            send("isready")
            waitForToken("readyok")
            send("position fen $fen")
            send("go depth $depth")

            var latestDepth: Int? = null
            var latestScore: String? = null
            var latestPv: List<String> = emptyList()
            var bestMove: String? = null
            var ponder: String? = null

            var lineCount = 0
            while (lineCount++ < 100_000) {
                val line = reader.readLine()
                    ?: throw IllegalStateException("Stockfish berhenti sebelum mengirim bestmove.")
                val info = UciInfoParser.parseInfo(line)
                if (info != null && (info.depth ?: 0) >= (latestDepth ?: 0)) {
                    latestDepth = info.depth ?: latestDepth
                    latestScore = info.score ?: latestScore
                    if (info.principalVariation.isNotEmpty()) {
                        latestPv = info.principalVariation
                    }
                }

                val parsedBest = UciInfoParser.parseBestMove(line)
                if (parsedBest != null) {
                    bestMove = parsedBest.first
                    ponder = parsedBest.second
                    break
                }
                if (line.contains("CRITICAL ERROR", ignoreCase = true)) {
                    throw IllegalStateException(line)
                }
            }

            val move = bestMove
                ?: throw IllegalStateException("Stockfish tidak menghasilkan langkah terbaik.")
            send("quit")
            child.waitFor(1, TimeUnit.SECONDS)

            EngineResult.Success(
                EngineAnalysis(
                    bestMoveUci = move,
                    ponderMoveUci = ponder,
                    depth = latestDepth,
                    score = latestScore,
                    principalVariationUci = latestPv
                )
            )
        } catch (exception: Exception) {
            EngineResult.Failure(exception.message ?: "Analisis Stockfish gagal.")
        } finally {
            child?.destroy()
            if (child?.isAlive == true) child.destroyForcibly()
            if (process === child) process = null
        }
    }

    fun stop() {
        process?.destroyForcibly()
        process = null
    }

    override fun close() {
        closed = true
        stop()
        executor.shutdownNow()
    }
}
