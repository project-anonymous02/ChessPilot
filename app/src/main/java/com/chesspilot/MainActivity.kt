package com.chesspilot

import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.chesspilot.chess.ChessRulesResult
import com.chesspilot.chess.ChessRulesValidator
import com.chesspilot.engine.EngineResult
import com.chesspilot.engine.StockfishEngine

/**
 * ChessPilot's native training and review board.
 *
 * Automatic engine arrows are intentionally restricted to user-supplied
 * training positions, completed-game review, and permitted bot-game analysis.
 * This activity does not inspect or overlay live human games on Chess.com.
 */
class MainActivity : ComponentActivity() {
    private lateinit var engine: StockfishEngine
    private lateinit var boardView: ChessBoardView
    private lateinit var fenInput: EditText
    private lateinit var statusText: TextView
    private lateinit var moveText: TextView
    private lateinit var detailText: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var analysisGeneration = 0
    private var pendingAnalysis: Runnable? = null

    private val initialFen =
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = StockfishEngine(this)
        buildInterface()
        showFairPlayNoticeIfNeeded()
        scheduleAnalysis(initialFen)
    }

    private fun buildInterface() {
        val scroll = ScrollView(this).apply {
            setFillViewport(true)
            setBackgroundColor(0xFF10151D.toInt())
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(24))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val logo = TextView(this).apply {
            text = "♞"
            textSize = 38f
            setTextColor(0xFFF0D9B5.toInt())
            typeface = Typeface.create("serif", Typeface.BOLD)
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(48), dp(52)))
        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        titleBlock.addView(label("ChessPilot", 23f, 0xFFFFFFFF.toInt(), true))
        titleBlock.addView(label("Papan latihan • analisis otomatis", 12f, 0xFF9CA3AF.toInt()))
        header.addView(titleBlock)
        root.addView(header)

        val notice = TextView(this).apply {
            text = "PERINGATAN FAIR PLAY\nAnalisis engine saat pertandingan melawan manusia dapat melanggar aturan Fair Play Chess.com dan berisiko menyebabkan sanksi akun. Pahami konsekuensinya sebelum menggunakan fitur ini."
            textSize = 12f
            setTextColor(0xFFFFD58A.toInt())
            setPadding(dp(12), dp(11), dp(12), dp(11))
            setBackgroundColor(0xFF302719.toInt())
        }
        val noticeParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(14); bottomMargin = dp(14) }
        root.addView(notice, noticeParams)

        boardView = ChessBoardView(this).apply {
            setPosition(initialFen)
            contentDescription = "Papan catur dengan panah langkah terbaik"
        }
        val boardParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(340)
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
        root.addView(boardView, boardParams)

        statusText = label("Memulai Stockfish 19…", 13f, 0xFFB8C3D1.toInt())
        statusText.setPadding(0, dp(14), 0, dp(5))
        root.addView(statusText)

        moveText = label("Menunggu analisis posisi…", 18f, 0xFFFFFFFF.toInt(), true)
        root.addView(moveText)

        detailText = label("Panah akan muncul otomatis setelah analisis selesai.", 12f, 0xFF9CA3AF.toInt())
        detailText.setPadding(0, dp(4), 0, dp(12))
        root.addView(detailText)

        root.addView(label("FEN posisi latihan", 13f, 0xFFE5E7EB.toInt(), true))
        fenInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setSingleLine(false)
            minLines = 2
            maxLines = 3
            textSize = 12f
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF8792A2.toInt())
            setHint("Tempel FEN posisi latihan atau review")
            setText(initialFen)
            setPadding(dp(10), dp(9), dp(10), dp(9))
            setBackgroundColor(0xFF202A36.toInt())
        }
        val inputParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(7) }
        root.addView(fenInput, inputParams)

        val reset = Button(this).apply {
            text = "Kembali ke posisi awal"
            setOnClickListener { fenInput.setText(initialFen) }
        }
        root.addView(reset)

        root.addView(label(
            "Cara kerja: ubah FEN, ChessPilot memvalidasi posisi, menjalankan Stockfish, lalu menggambar panah dari kotak asal ke tujuan. Tidak perlu menekan tombol analisis.",
            11f,
            0xFF8F9AAA.toInt()
        ).apply { setPadding(0, dp(8), 0, 0) })

        scroll.addView(root)
        setContentView(scroll)

        fenInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                scheduleAnalysis(s?.toString()?.trim().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun scheduleAnalysis(fen: String) {
        val generation = ++analysisGeneration
        pendingAnalysis?.let(handler::removeCallbacks)
        engine.stop()
        boardView.setPosition(fen)

        if (fen.isBlank()) {
            statusText.text = "Masukkan FEN posisi latihan."
            moveText.text = "Belum ada posisi"
            detailText.text = ""
            return
        }

        when (val validation = ChessRulesValidator.inspect(fen)) {
            is ChessRulesResult.Invalid -> {
                statusText.text = "FEN belum valid: ${validation.reason}"
                moveText.text = "Menunggu posisi valid"
                detailText.text = ""
                return
            }
            is ChessRulesResult.Valid -> {
                boardView.setPosition(validation.snapshot.normalizedFen)
            }
        }

        statusText.text = "Posisi valid. Analisis otomatis dimulai setelah input berhenti…"
        moveText.text = "Menganalisis posisi"
        detailText.text = "Stockfish 19 • depth 12"

        val task = Runnable {
            statusText.text = "Stockfish sedang menghitung langkah terbaik…"
            engine.analyzeValidated(fen, depth = 12) { result ->
                runOnUiThread {
                    if (generation != analysisGeneration || isFinishing || isDestroyed) return@runOnUiThread
                    when (result) {
                        is EngineResult.Failure -> {
                            statusText.text = "Analisis gagal"
                            moveText.text = result.reason
                            detailText.text = "Periksa FEN dan ketersediaan binary Stockfish ARM64."
                            boardView.setPosition(fen)
                        }
                        is EngineResult.Success -> {
                            val analysis = result.analysis
                            boardView.setPosition(fen, analysis.bestMoveUci)
                            statusText.text = "Analisis selesai • kedalaman ${analysis.depth ?: "—"}"
                            moveText.text = describeMove(analysis.bestMoveUci)
                            detailText.text = buildString {
                                append("Evaluasi: ${analysis.score ?: "—"}")
                                if (analysis.ponderMoveUci != null) {
                                    append("  •  Perkiraan balasan: ${analysis.ponderMoveUci}")
                                }
                                if (analysis.principalVariationUci.isNotEmpty()) {
                                    append("\nVariasi utama: ${analysis.principalVariationUci.joinToString(" ")}")
                                }
                            }
                        }
                    }
                }
            }
        }
        pendingAnalysis = task
        handler.postDelayed(task, 700L)
    }

    private fun describeMove(uci: String): String {
        if (uci == "(none)" || uci.length < 4) return "Tidak ada langkah legal"
        val piece = boardView.pieceAtSquare(uci.substring(0, 2))
        val pieceName = when (piece?.lowercaseChar()) {
            'p' -> "Pion"
            'n' -> "Kuda"
            'b' -> "Gajah"
            'r' -> "Benteng"
            'q' -> "Ratu"
            'k' -> "Raja"
            else -> "Bidak"
        }
        return "Langkah terbaik: $pieceName  •  ${uci.substring(0, 2)} → ${uci.substring(2, 4)}"
    }

    private fun showFairPlayNoticeIfNeeded() {
        val preferences = getSharedPreferences("chesspilot_preferences", MODE_PRIVATE)
        if (preferences.getBoolean("fair_play_notice_acknowledged", false)) return
        AlertDialog.Builder(this)
            .setTitle("Peringatan Fair Play")
            .setMessage(
                "ChessPilot dapat menganalisis posisi dengan Stockfish. Penggunaan bantuan engine saat pertandingan melawan manusia dapat melanggar aturan Fair Play Chess.com dan berisiko menyebabkan sanksi akun. Pahami aturan dan konsekuensinya sebelum menggunakan fitur ini."
            )
            .setCancelable(true)
            .setPositiveButton("Saya mengerti") { dialog, _ ->
                preferences.edit().putBoolean("fair_play_notice_acknowledged", true).apply()
                dialog.dismiss()
            }
            .setNegativeButton("Nanti", null)
            .show()
    }

    private fun label(
        text: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        pendingAnalysis?.let(handler::removeCallbacks)
        analysisGeneration++
        if (::engine.isInitialized) engine.close()
        super.onDestroy()
    }
}
