package com.chesspilot

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.SafeBrowsingResponse
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity

/**
 * Chess.com is the primary experience. The native FEN analyzer remains a separate
 * training/review tool; this browser shell deliberately does not inspect live games.
 */
class ChessComActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var status: TextView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(16, 24, 32))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "♞ ChessPilot"
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(48), 1f))

        val analyze = Button(this).apply {
            text = "Analisis latihan"
            setOnClickListener {
                Toast.makeText(
                    this@ChessComActivity,
                    "Gunakan analisis hanya untuk latihan, bot yang mengizinkan bantuan, atau review setelah pertandingan.",
                    Toast.LENGTH_LONG
                ).show()
                startActivity(Intent(this@ChessComActivity, MainActivity::class.java))
            }
        }
        header.addView(analyze)
        root.addView(header)

        status = TextView(this).apply {
            text = "Chess.com • Browser"
            textSize = 12f
            setTextColor(Color.rgb(190, 205, 218))
            setPadding(dp(14), dp(7), dp(14), dp(7))
        }
        root.addView(status)

        webView = WebView(this)
        webView.setBackgroundColor(Color.rgb(16, 24, 32))
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
            loadsImagesAutomatically = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.scheme != "https") return true
                val host = uri.host?.lowercase().orEmpty()
                val isChessSite = host == "chess.com" || host.endsWith(".chess.com")
                if (isChessSite) return false

                // Keep the in-app browser scoped to Chess.com. Other sites open externally.
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    true
                } catch (_: Exception) {
                    true
                }
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                status.text = "Memuat Chess.com…"
            }

            override fun onPageFinished(view: WebView, url: String?) {
                status.text = "Chess.com • Browser"
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        setContentView(root)
        if (savedInstanceState == null) {
            webView.loadUrl("https://www.chess.com/")
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
