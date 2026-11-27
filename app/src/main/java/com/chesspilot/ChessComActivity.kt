package com.chesspilot

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.chesspilot.chess.ChessRulesResult
import com.chesspilot.chess.ChessRulesValidator
import com.chesspilot.engine.EngineResult
import com.chesspilot.engine.StockfishEngine
import org.json.JSONObject

/** Chess.com browser with read-only DOM recognition and local Stockfish overlay. */
class ChessComActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var status: TextView
    private lateinit var engine: StockfishEngine
    private var lastFen = ""
    private var generation = 0
    private val bridgeToken = java.util.UUID.randomUUID().toString()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = StockfishEngine(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(16, 24, 32))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "♞ ChessPilot"
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        header.addView(Button(this).apply {
            text = "Analisis FEN"
            setOnClickListener { startActivity(Intent(this@ChessComActivity, MainActivity::class.java)) }
        })
        root.addView(header)

        status = TextView(this).apply {
            text = "Chess.com • Menunggu papan"
            textSize = 12f
            setTextColor(Color.rgb(190, 205, 218))
            setPadding(dp(14), dp(7), dp(14), dp(7))
        }
        root.addView(status)
        root.addView(TextView(this).apply {
            text = "PERINGATAN FAIR PLAY: bantuan engine saat pertandingan melawan manusia dapat melanggar aturan Chess.com dan berisiko menyebabkan sanksi akun. Pahami aturan sebelum menggunakan fitur ini."
            textSize = 11f
            setTextColor(Color.rgb(255, 213, 138))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.rgb(48, 39, 25))
        })

        webView = WebView(this)
        webView.setBackgroundColor(Color.rgb(16, 24, 32))
        webView.addJavascriptInterface(BoardBridge(), "ChessPilotNative")
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
        }
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.scheme != "https") return true
                val host = uri.host?.lowercase().orEmpty()
                if (host == "chess.com" || host.endsWith(".chess.com")) return false
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    true
                } catch (_: Exception) { true }
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                status.text = "Memuat Chess.com…"
            }

            override fun onPageFinished(view: WebView, url: String?) {
                val host = runCatching { android.net.Uri.parse(url).host.orEmpty() }.getOrDefault("")
                if (host == "chess.com" || host.endsWith(".chess.com")) {
                    status.text = "Chess.com • DOM monitor aktif"
                    view.evaluateJavascript(BOARD_MONITOR_SCRIPT.replace("__CP_TOKEN__", bridgeToken), null)
                }
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        if (savedInstanceState == null) webView.loadUrl("https://www.chess.com/")
        else webView.restoreState(savedInstanceState)
        showFairPlayNotice()
    }

    inner class BoardBridge {
        @JavascriptInterface
        fun onBoardState(payload: String, token: String) {
            if (token != bridgeToken || payload.length > 4096) return
            runOnUiThread {
                if (!isFinishing && !isDestroyed) handleBoardState(payload)
            }
        }

        @JavascriptInterface
        fun onMonitorStatus(message: String, token: String) {
            if (token != bridgeToken) return
            runOnUiThread {
                if (!isFinishing && !isDestroyed && lastFen.isBlank()) status.text = message
            }
        }
    }

    private fun handleBoardState(payload: String) {
        val data = runCatching { JSONObject(payload) }.getOrNull() ?: return
        val fen = data.optString("fen").trim()
        val source = data.optString("source", "DOM")
        if (fen.isBlank() || fen == lastFen) return

        when (val validation = ChessRulesValidator.inspect(fen)) {
            is ChessRulesResult.Invalid -> {
                status.text = "Posisi belum valid: " + validation.reason
                return
            }
            is ChessRulesResult.Valid -> lastFen = validation.snapshot.normalizedFen
        }

        val requestId = ++generation
        engine.stop()
        status.text = "Papan valid • " + source + " • Stockfish menganalisis…"
        engine.analyze(lastFen, depth = 10) { result ->
            runOnUiThread {
                if (requestId != generation || isFinishing || isDestroyed) return@runOnUiThread
                when (result) {
                    is EngineResult.Failure -> status.text = "Stockfish gagal: " + result.reason
                    is EngineResult.Success -> {
                        val move = result.analysis.bestMoveUci
                        if (move.length >= 4 && move != "(none)") {
                            val from = JSONObject.quote(move.substring(0, 2))
                            val to = JSONObject.quote(move.substring(2, 4))
                            webView.evaluateJavascript(
                                "window.ChessPilotOverlay && window.ChessPilotOverlay.draw(" + from + "," + to + ")",
                                null
                            )
                            status.text = "Papan " + source + " • " + move.substring(0, 2) +
                                " → " + move.substring(2, 4) + " • depth " +
                                (result.analysis.depth?.toString() ?: "—") + " • " +
                                (result.analysis.score ?: "—")
                        } else status.text = "Tidak ada langkah legal."
                    }
                }
            }
        }
    }

    private fun showFairPlayNotice() {
        val prefs = getSharedPreferences("chesspilot_preferences", MODE_PRIVATE)
        if (prefs.getBoolean("live_analysis_notice_acknowledged", false)) return
        android.app.AlertDialog.Builder(this)
            .setTitle("Peringatan Fair Play")
            .setMessage("ChessPilot membaca posisi papan Chess.com dan menampilkan analisis Stockfish. Bantuan engine dalam pertandingan melawan manusia dapat melanggar aturan Fair Play Chess.com dan berisiko menyebabkan sanksi akun. Pahami aturan dan konsekuensinya sebelum melanjutkan.")
            .setCancelable(false)
            .setPositiveButton("Saya mengerti") { dialog, _ ->
                prefs.edit().putBoolean("live_analysis_notice_acknowledged", true).apply()
                dialog.dismiss()
            }
            .show()
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
        generation++
        if (::engine.isInitialized) engine.close()
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.removeJavascriptInterface("ChessPilotNative")
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        private const val BOARD_MONITOR_SCRIPT = """
(function () {
  if (window.__chessPilotMonitorInstalled) return;
  window.__chessPilotMonitorInstalled = true;
  const TOKEN = "__CP_TOKEN__";
  const notify = (s) => { try { window.ChessPilotNative.onMonitorStatus(s, TOKEN); } catch (_) {} };

  function boardElement() {
    return Array.from(document.querySelectorAll('wc-chess-board')).find((b) => {
      const r = b.getBoundingClientRect();
      return r.width > 100 && r.height > 100 && Math.abs(r.width-r.height) < 40;
    }) || null;
  }

  function getFullFen(board) {
    try {
      const game = board.game;
      if (game && typeof game.getFEN === 'function') return game.getFEN();
      if (game && typeof game.getFen === 'function') return game.getFen();
      const attr = board.getAttribute('game-fen') || board.getAttribute('fen');
      if (attr && attr.trim().split(/\s+/).length >= 4) return attr.trim();
    } catch (_) {}
    return null;
  }

  function getDomFen(board) {
    const scope = board.shadowRoot || board;
    const pieces = Array.from(scope.querySelectorAll('.piece'));
    const squares = new Map();
    for (const node of pieces) {
      const c = String(node.className || '');
      const p = c.match(/\b([wb])([pnbrqk])\b/i);
      const s = c.match(/\bsquare-([1-8])([1-8])\b/);
      if (!p || !s) continue;
      const key = s[1] + ',' + s[2];
      if (squares.has(key)) return null;
      const type = p[2].toLowerCase();
      squares.set(key, p[1].toLowerCase() === 'w' ? type.toUpperCase() : type);
    }
    if (squares.size < 2 || squares.size > 32) return null;
    let kingsW = 0, kingsB = 0;
    for (const p of squares.values()) { if (p === 'K') kingsW++; if (p === 'k') kingsB++; }
    if (kingsW !== 1 || kingsB !== 1) return null;

    let placement = '';
    for (let rank=8; rank>=1; rank--) {
      let empty=0;
      for (let file=1; file<=8; file++) {
        const p=squares.get(file+','+rank);
        if (!p) empty++;
        else { if (empty) placement+=empty; empty=0; placement+=p; }
      }
      if (empty) placement+=empty;
      if (rank>1) placement+='/';
    }
    const turn = board.getAttribute('data-turn') ||
      (board.game && typeof board.game.getTurn === 'function' ? board.game.getTurn() : '') ||
      (board.game && typeof board.game.turn === 'function' ? board.game.turn() : '');
    if (turn !== 'w' && turn !== 'b') return null;
    // DOM-only fallback cannot recover castling/en-passant history, so it fails closed for those rights.
    return placement + ' ' + turn + ' - - 0 1';
  }

  function stateFor(board) {
    const fen = getFullFen(board);
    if (fen && fen.trim().split(/\s+/).length >= 4) return {fen:fen.trim(),source:'Chess.com game state'};
    const fallback = getDomFen(board);
    return fallback ? {fen:fallback,source:'DOM fallback'} : null;
  }

  function ensureOverlay() {
    let svg=document.getElementById('chesspilot-live-overlay');
    if (svg) return svg;
    svg=document.createElementNS('http://www.w3.org/2000/svg','svg');
    svg.id='chesspilot-live-overlay';
    svg.setAttribute('style','position:fixed;inset:0;width:100vw;height:100vh;pointer-events:none;z-index:2147483646;overflow:visible');
    svg.innerHTML='<defs><marker id="cp-arrow-head" markerWidth="7" markerHeight="7" refX="5" refY="3" orient="auto"><path d="M0,0 L6,3 L0,6 Z" fill="#20c878"/></marker></defs><path id="cp-arrow" d="" stroke="#20c878" stroke-width="7" stroke-linecap="round" marker-end="url(#cp-arrow-head)" opacity=".92" fill="none"/>';
    document.documentElement.appendChild(svg);
    return svg;
  }

  window.ChessPilotOverlay = {
    draw(from,to) {
      const b=boardElement();
      if (!b || !/^[a-h][1-8]$/.test(from) || !/^[a-h][1-8]$/.test(to)) return;
      const r=b.getBoundingClientRect(), flipped=b.classList.contains('flipped');
      const center=(s) => {
        let f=s.charCodeAt(0)-97, rank=Number(s[1])-1;
        if (flipped) { f=7-f; rank=7-rank; }
        const t=r.width/8;
        return [r.left+(f+.5)*t,r.top+(7-rank+.5)*t];
      };
      const a=center(from),z=center(to),svg=ensureOverlay();
      svg.querySelector('#cp-arrow').setAttribute('d','M'+a[0]+' '+a[1]+' L'+z[0]+' '+z[1]);
      this.last={from,to};
    },
    clear() {
      const p=document.querySelector('#chesspilot-live-overlay #cp-arrow');
      if (p) p.setAttribute('d','');
      this.last=null;
    }
  };

  let previous='', stable=0, sent='';
  function scan() {
    const b=boardElement();
    if (!b) { notify('Chess.com • papan belum ditemukan'); return; }
    const state=stateFor(b);
    if (!state) { stable=0; notify('Papan ditemukan • menunggu FEN dan giliran'); return; }
    if (state.fen===previous) stable++; else { previous=state.fen; stable=1; }
    if (stable<2 || sent===state.fen) return;
    sent=state.fen;
    // Keep the previous recommendation visible until Stockfish returns the next one.
    try { window.ChessPilotNative.onBoardState(JSON.stringify(state), TOKEN); } catch (_) {}
  }
  let scheduled=false;
  function scheduleScan(){ if(scheduled) return; scheduled=true; setTimeout(()=>{scheduled=false;scan();},500); }
  const observer=new MutationObserver(scheduleScan);
  observer.observe(document.documentElement,{subtree:true,childList:true});
  const redraw=()=>{ if(window.ChessPilotOverlay.last) window.ChessPilotOverlay.draw(window.ChessPilotOverlay.last.from,window.ChessPilotOverlay.last.to); };
  window.addEventListener('resize',redraw,{passive:true});
  window.addEventListener('scroll',redraw,{passive:true});
  setInterval(scan,1000);
  scan();
  notify('Chess.com • DOM monitor aktif');
})();
"""
    }
}
