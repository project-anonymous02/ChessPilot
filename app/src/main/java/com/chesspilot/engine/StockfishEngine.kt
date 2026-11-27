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
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

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

/** Persistent UCI worker: one native process is reused across positions. */
class StockfishEngine(context: Context) : Closeable {
    private val appContext = context.applicationContext
    private val requestGeneration = AtomicLong(0)
    private val executor = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue())
    private val ioLock = Any()
    @Volatile private var process: Process? = null
    @Volatile private var writer: BufferedWriter? = null
    @Volatile private var reader: BufferedReader? = null // read by the serial worker; cleared during shutdown
    @Volatile private var closed = false

    fun analyze(fen: String, depth: Int = 12, callback: (EngineResult) -> Unit) {
        enqueue(fen, depth, validate = true, callback)
    }

    /** Use only when the caller has already validated this exact FEN. */
    fun analyzeValidated(fen: String, depth: Int = 12, callback: (EngineResult) -> Unit) {
        enqueue(fen, depth, validate = false, callback)
    }

    private fun enqueue(fen: String, depth: Int, validate: Boolean, callback: (EngineResult) -> Unit) {
        require(depth in 1..20) { "Depth harus berada di antara 1 dan 20." }
        if (closed) return
        val requestId = requestGeneration.incrementAndGet()
        executor.queue.clear()
        sendCommand("stop") // finish the previous search cleanly; keep the native process alive
        executor.execute {
            if (closed || requestId != requestGeneration.get()) return@execute
            val result = runAnalysis(fen, depth, requestId, validate)
            if (!closed && requestId == requestGeneration.get()) callback(result)
        }
    }

    @Synchronized
    private fun runAnalysis(fen: String, depth: Int, requestId: Long, validate: Boolean): EngineResult {
        if (closed) return EngineResult.Failure("Engine sudah ditutup.")
        if (validate) {
            when (val validation = ChessRulesValidator.inspect(fen)) {
                is ChessRulesResult.Invalid -> return EngineResult.Failure(validation.reason)
                is ChessRulesResult.Valid -> Unit
            }
        }
        if (closed || requestId != requestGeneration.get()) return EngineResult.Failure("Analisis digantikan posisi terbaru.")

        return try {
            ensureEngine()
            if (closed || requestId != requestGeneration.get()) return EngineResult.Failure("Analisis digantikan posisi terbaru.")
            sendCommand("position fen $fen")
            sendCommand("go depth $depth")

            var latestDepth: Int? = null
            var latestScore: String? = null
            var latestPv: List<String> = emptyList()
            var bestMove: String? = null
            var ponder: String? = null
            var lineCount = 0
            while (lineCount++ < 100_000) {
                val line = reader?.readLine() ?: throw IllegalStateException("Stockfish berhenti sebelum mengirim bestmove.")
                // Most UCI output is telemetry. Parse only depth lines that carry a PV.
                if (line.startsWith("info depth ") && line.contains(" pv ")) {
                    val info = UciInfoParser.parseInfo(line)
                    if (info != null && (info.depth ?: 0) >= (latestDepth ?: 0)) {
                        latestDepth = info.depth ?: latestDepth
                        latestScore = info.score ?: latestScore
                        if (info.principalVariation.isNotEmpty()) latestPv = info.principalVariation
                    }
                }
                val parsedBest = UciInfoParser.parseBestMove(line)
                if (parsedBest != null) {
                    bestMove = parsedBest.first
                    ponder = parsedBest.second
                    break
                }
                if (line.contains("CRITICAL ERROR", ignoreCase = true)) throw IllegalStateException(line)
            }
            val move = bestMove ?: throw IllegalStateException("Stockfish tidak menghasilkan langkah terbaik.")
            EngineResult.Success(EngineAnalysis(move, ponder, latestDepth, latestScore, latestPv))
        } catch (exception: Exception) {
            closeEngineProcess()
            EngineResult.Failure(exception.message ?: "Analisis Stockfish gagal.")
        }
    }

    private fun ensureEngine() {
        val current = process
        if (current?.isAlive == true && writer != null && reader != null) return
        closeEngineProcess()
        val binary = File(appContext.applicationInfo.nativeLibraryDir, "libstockfish.so")
        if (!binary.isFile || !binary.canExecute()) throw IllegalStateException("Stockfish ARM64 tidak ditemukan atau tidak dapat dijalankan.")
        val child = ProcessBuilder(binary.absolutePath).redirectErrorStream(true).start()
        process = child
        writer = BufferedWriter(OutputStreamWriter(child.outputStream))
        reader = BufferedReader(InputStreamReader(child.inputStream))
        sendCommand("uci")
        waitForToken("uciok")
        sendCommand("setoption name Threads value 1")
        sendCommand("setoption name Hash value 48")
        sendCommand("isready")
        waitForToken("readyok")
    }

    private fun waitForToken(token: String, maxLines: Int = 20_000) {
        repeat(maxLines) {
            val line = reader?.readLine() ?: throw IllegalStateException("Stockfish berhenti sebelum mengirim $token.")
            if (line.trim() == token) return
            if (line.contains("CRITICAL ERROR", ignoreCase = true)) throw IllegalStateException(line)
        }
        throw IllegalStateException("Respons Stockfish tidak valid: $token tidak ditemukan.")
    }

    private fun sendCommand(command: String) {
        synchronized(ioLock) {
            val out = writer ?: return
            out.write(command)
            out.newLine()
            out.flush()
        }
    }

    private fun closeEngineProcess() {
        synchronized(ioLock) {
            runCatching { writer?.close() }
            runCatching { reader?.close() }
            process?.destroyForcibly()
            writer = null
            reader = null
            process = null
        }
    }

    fun stop() {
        requestGeneration.incrementAndGet()
        executor.queue.clear()
        sendCommand("stop")
    }

    override fun close() {
        if (closed) return
        closed = true
        requestGeneration.incrementAndGet()
        executor.queue.clear()
        sendCommand("quit")
        // Destroy first so a worker blocked in readLine() is released before streams close.
        process?.destroyForcibly()
        executor.shutdownNow()
        closeEngineProcess()
    }
}
