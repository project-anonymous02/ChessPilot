package com.chesspilot

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.chesspilot.chess.ChessRulesValidator
import com.chesspilot.chess.ChessRulesResult
import com.chesspilot.engine.EngineResult
import com.chesspilot.engine.StockfishEngine
import com.chesspilot.security.ChessComHostPolicy

/**
 * Chess.com browser shell plus offline FEN analysis.
 *
 * Engine analysis is manual and intended only for training positions,
 * completed-game review, or permitted bot-game analysis. No live human-game
 * board is read and no move hints are injected into Chess.com.
 */
class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var statusText: TextView
    private lateinit var fenInput: EditText
    private lateinit var analyzeButton: Button
    private lateinit var resultText: TextView
    private lateinit var engine: StockfishEngine

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = StockfishEngine(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF101318.toInt())
        }

        val header = TextView(this).apply {
            text = "CHESSPILOT  •  ANDROID"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(18, 16, 18, 12)
        }
        root.addView(header)

        statusText = TextView(this).apply {
            text = "Menyiapkan browser Chess.com…"
            textSize = 13f
            setTextColor(0xFFE5E7EB.toInt())
        }

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.safeBrowsingEnabled = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val uri = request?.url ?: return true
                    return !isAllowedChessComUri(uri)
                }

                override fun onPageStarted(
                    view: WebView?,
                    url: String?,
                    favicon: android.graphics.Bitmap?
                ) {
                    statusText.text = "Membuka Chess.com…"
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    statusText.text = "Chess.com dimuat. Analisis FEN tersedia di panel bawah."
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        statusText.text = "Halaman gagal dimuat. Periksa koneksi lalu muat ulang."
                    }
                }
            }
        }

        root.addView(
            webView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
        )

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 10, 16, 14)
            setBackgroundColor(0xFF1A2029.toInt())
        }
        panel.addView(statusText)

        val notice = TextView(this).apply {
            text = "Analisis offline untuk latihan, partai selesai, atau partai bot yang diizinkan. Jangan gunakan rekomendasi engine saat melawan manusia."
            textSize = 11f
            setTextColor(0xFFFFD27D.toInt())
            setPadding(0, 6, 0, 8)
        }
        panel.addView(notice)

        fenInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            singleLine = false
            minLines = 2
            maxLines = 3
            textSize = 12f
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF9CA3AF.toInt())
            setHint("Masukkan FEN enam bagian")
            setText("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
            setPadding(10, 8, 10, 8)
            setBackgroundColor(0xFF252D39.toInt())
        }
        panel.addView(fenInput)

        analyzeButton = Button(this).apply {
            text = "Analisis posisi"
            setOnClickListener { analyzePosition() }
        }
        panel.addView(analyzeButton)

        resultText = TextView(this).apply {
            text = "Stockfish 19 siap setelah binary ARM64 tersedia."
            textSize = 12f
            setTextColor(0xFFE5E7EB.toInt())
            setPadding(0, 6, 0, 4)
        }
        panel.addView(resultText)

        val reload = Button(this).apply {
            text = "Muat ulang Chess.com"
            setOnClickListener { webView.reload() }
        }
        panel.addView(reload)
        root.addView(panel)

        setContentView(root)

        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(
                webView,
                "ChessPilotBridge",
                setOf(
                    "https://chess.com",
                    "https://www.chess.com",
                    "https://*.chess.com"
                )
            ) { _, _, sourceOrigin, isMainFrame, _ ->
                // No page-to-native commands are accepted in this phase.
                if (!isMainFrame || !ChessComHostPolicy.isAllowed(
                        sourceOrigin.scheme,
                        sourceOrigin.host
                    )
                ) {
                    return@addWebMessageListener
                }
            }
        } else {
            statusText.text =
                "Fitur komunikasi WebView aman tidak tersedia pada perangkat ini."
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )

        webView.loadUrl("https://www.chess.com/")
    }

    private fun analyzePosition() {
        val fen = fenInput.text.toString().trim()
        if (fen.isEmpty()) {
            resultText.text = "Masukkan FEN terlebih dahulu."
            return
        }

        when (val validation = ChessRulesValidator.inspect(fen)) {
            is ChessRulesResult.Invalid -> {
                resultText.text = "Posisi tidak valid: ${validation.reason}"
                return
            }
            is ChessRulesResult.Valid -> Unit
        }

        analyzeButton.isEnabled = false
        resultText.text = "Stockfish sedang menganalisis…"
        engine.analyze(fen, depth = 12) { result ->
            runOnUiThread {
                analyzeButton.isEnabled = true
                resultText.text = when (result) {
                    is EngineResult.Failure -> "Analisis gagal: ${result.reason}"
                    is EngineResult.Success -> {
                        val a = result.analysis
                        buildString {
                            append("Langkah terbaik (UCI): ${a.bestMoveUci}")
                            append("\nKedalaman: ${a.depth ?: "—"}")
                            append("\nEvaluasi: ${a.score ?: "—"}")
                            if (a.ponderMoveUci != null) {
                                append("\nPerkiraan balasan: ${a.ponderMoveUci}")
                            }
                            if (a.principalVariationUci.isNotEmpty()) {
                                append("\nVariasi: ${a.principalVariationUci.joinToString(" ")}")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun isAllowedChessComUri(uri: Uri): Boolean {
        return ChessComHostPolicy.isAllowed(uri.scheme, uri.host)
    }

    override fun onDestroy() {
        if (::engine.isInitialized) engine.close()
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.removeAllViews()
            webView.destroy()
        }
        super.onDestroy()
    }
}
