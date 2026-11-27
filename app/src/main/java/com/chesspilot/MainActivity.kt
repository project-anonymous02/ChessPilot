package com.chesspilot

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.chesspilot.security.ChessComHostPolicy

/**
 * First Android shell: Chess.com browser plus a native ChessPilot status panel.
 *
 * Board recognition and engine recommendations are intentionally not enabled
 * until game mode and a complete, validated position can be established.
 */
class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var statusText: TextView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF101318.toInt())
        }

        val header = TextView(this).apply {
            text = "CHESSPILOT  •  ANDROID PREVIEW"
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
                    statusText.text =
                        "Chess.com dimuat. Pengenalan posisi belum diaktifkan."
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        statusText.text =
                            "Halaman gagal dimuat. Periksa koneksi lalu muat ulang."
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
            setPadding(18, 12, 18, 16)
            setBackgroundColor(0xFF1A2029.toInt())
        }

        panel.addView(statusText)

        val detail = TextView(this).apply {
            text = "Analisis akan tersedia setelah posisi dan jenis permainan terverifikasi."
            textSize = 12f
            setTextColor(0xFF9CA3AF.toInt())
            setPadding(0, 8, 0, 10)
        }
        panel.addView(detail)

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
                // Future messages must be schema-validated and mode-gated.
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

    private fun isAllowedChessComUri(uri: Uri): Boolean {
        return ChessComHostPolicy.isAllowed(uri.scheme, uri.host)
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.removeAllViews()
            webView.destroy()
        }
        super.onDestroy()
    }
}
