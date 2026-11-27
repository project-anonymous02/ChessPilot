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
import com.chesspilot.engine.EngineResult
import com.chesspilot.engine.StockfishEngine
import org.json.JSONObject

/** Chess.com browser with read-only DOM recognition and local Stockfish overlay. */
class ChessComActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var status: TextView
    private lateinit var engine: StockfishEngine
    private var lastPositionKey = ""
    private var pendingPositionKey = ""
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
                // A navigation creates a fresh JavaScript document and overlay. Reset
                // native deduplication so an identical FEN on the new page is analyzed again.
                generation++
                lastPositionKey = ""
                pendingPositionKey = ""
                if (::engine.isInitialized) engine.stop()
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
                if (!isFinishing && !isDestroyed && lastPositionKey.isBlank()) status.text = message
            }
        }
    }

    private fun handleBoardState(payload: String) {
        val data = runCatching { JSONObject(payload) }.getOrNull() ?: return
        val fen = data.optString("fen").trim()
        val source = data.optString("source", "DOM")
        val metadataComplete = data.optBoolean("metadataComplete", true)
        val fallbackKey = fen + if (metadataComplete) "|complete" else "|incomplete"
        val positionKey = data.optString("key").ifBlank { fallbackKey }
        if (fen.isBlank() || positionKey == pendingPositionKey ||
            (positionKey == lastPositionKey && pendingPositionKey.isBlank())
        ) return

        // Do not mark a position as processed until validation and analysis succeed.
        // A failed request is acknowledged as retryable by the JavaScript monitor.
        pendingPositionKey = positionKey
        val requestId = ++generation
        status.text = if (metadataComplete) {
            "Memvalidasi posisi • " + source + "…"
        } else {
            "Memvalidasi posisi • DOM fallback (metadata riwayat tidak lengkap)…"
        }
        engine.analyze(fen, depth = 12) { result ->
            runOnUiThread {
                if (requestId != generation || isFinishing || isDestroyed) return@runOnUiThread
                pendingPositionKey = ""
                when (result) {
                    is EngineResult.Failure -> {
                        status.text = "Posisi/engine gagal: " + result.reason
                        acknowledgeMonitor(positionKey, accepted = false)
                    }
                    is EngineResult.Success -> {
                        lastPositionKey = positionKey
                        acknowledgeMonitor(positionKey, accepted = true)
                        val move = result.analysis.bestMoveUci
                        if (move.length >= 4 && move != "(none)") {
                            val from = JSONObject.quote(move.substring(0, 2))
                            val to = JSONObject.quote(move.substring(2, 4))
                            webView.evaluateJavascript(
                                "window.ChessPilotOverlay && window.ChessPilotOverlay.draw(" + from + "," + to + ")",
                                null
                            )
                            val accuracyNote = if (metadataComplete) "" else
                                " • ESTIMASI: metadata rokade/en-passant/clock tidak diketahui"
                            status.text = "Papan " + source + accuracyNote + " • " + move.substring(0, 2) +
                                " → " + move.substring(2, 4) + " • depth " +
                                (result.analysis.depth?.toString() ?: "—") + " • " +
                                (result.analysis.score ?: "—")
                        } else status.text = "Tidak ada langkah legal."
                    }
                }
            }
        }
    }

    private fun acknowledgeMonitor(positionKey: String, accepted: Boolean) {
        if (!::webView.isInitialized || isFinishing || isDestroyed) return
        val key = JSONObject.quote(positionKey)
        webView.evaluateJavascript(
            "window.ChessPilotMonitor && window.ChessPilotMonitor.acknowledge(" + key + "," + accepted + ")",
            null
        )
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

  let cachedBoard=null;
  function boardElement() {
    if (cachedBoard && !cachedBoard.isConnected) cachedBoard=null;
    if (cachedBoard) {
      const current=cachedBoard.getBoundingClientRect();
      if (current.width<=100 || current.height<=100 || Math.abs(current.width-current.height)>=40) cachedBoard=null;
    }
    if (!cachedBoard) {
      const candidates=document.querySelectorAll('wc-chess-board');
      for (const candidate of candidates) {
        const r=candidate.getBoundingClientRect();
        if (r.width>100 && r.height>100 && Math.abs(r.width-r.height)<40) {
          cachedBoard=candidate;
          break;
        }
      }
    }
    if (!cachedBoard) return null;
    const r=cachedBoard.getBoundingClientRect();
    return r.width>100 && r.height>100 && Math.abs(r.width-r.height)<40 ? cachedBoard : null;
  }

  function normalizeFullFen(value) {
    if (typeof value !== 'string') return null;
    const fields = value.trim().split(/\s+/);
    return fields.length === 6 ? fields.join(' ') : null;
  }

  function getFullFen(board) {
    const candidates = [];
    try {
      const game = board.game;
      if (game && typeof game.getFEN === 'function') candidates.push(game.getFEN());
    } catch (_) {}
    try {
      const game = board.game;
      if (game && typeof game.getFen === 'function') candidates.push(game.getFen());
    } catch (_) {}
    try { candidates.push(board.getAttribute('game-fen')); } catch (_) {}
    try { candidates.push(board.getAttribute('fen')); } catch (_) {}
    for (const candidate of candidates) {
      const complete = normalizeFullFen(candidate);
      if (complete) return complete;
    }
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
    // Approximation only: board placement cannot recover castling/en-passant history
    // or move counters. stateFor() marks this result as metadata-incomplete.
    return placement + ' ' + turn + ' - - 0 1';
  }

  function stateFor(board) {
    const fen = getFullFen(board);
    if (fen) return {fen,source:'Chess.com game state',metadataComplete:true};
    const fallback = getDomFen(board);
    // DOM placement alone cannot recover move-history metadata. Keep the fallback
    // available, but label its analysis as an estimate instead of a complete FEN.
    return fallback ? {fen:fallback,source:'DOM fallback',metadataComplete:false} : null;
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

  let previousKey='', stable=0, sentKey='', pendingKey='', retryCount=0, retryAfter=0;
  window.ChessPilotMonitor = {
    acknowledge(key, accepted) {
      if (pendingKey !== key) return;
      pendingKey = '';
      if (accepted) {
        sentKey = key;
        retryCount = 0;
        retryAfter = 0;
      } else {
        retryCount = Math.min(retryCount + 1, 4);
        retryAfter = Date.now() + Math.min(1000 * Math.pow(2, retryCount - 1), 8000);
      }
    }
  };

  function scan(boardHint) {
    const b=boardHint || boardElement();
    if (!b) { notify('Chess.com • papan belum ditemukan'); return; }
    const state=stateFor(b);
    if (!state) { stable=0; notify('Papan ditemukan • menunggu FEN lengkap dan giliran'); return; }
    const key = state.fen + (state.metadataComplete ? '|complete' : '|incomplete');
    if (key===previousKey) stable++; else {
      previousKey=key;
      stable=1;
      retryCount=0;
      retryAfter=0;
    }
    // A different in-flight request must be superseded even when this position
    // matches the last successful one (for example A → B → A before B completes).
    if (stable<2 || pendingKey===key || (sentKey===key && !pendingKey) || Date.now()<retryAfter) return;
    pendingKey=key;
    // Keep the previous recommendation visible until Stockfish returns the next one.
    try {
      state.key=key;
      window.ChessPilotNative.onBoardState(JSON.stringify(state), TOKEN);
    } catch (_) {
      if (pendingKey===key) pendingKey='';
      retryCount=Math.min(retryCount+1,4);
      retryAfter=Date.now()+Math.min(1000*Math.pow(2,retryCount-1),8000);
    }
  }
  let scanScheduled=false, observedBoard=null;
  const boardObserver=new MutationObserver(()=>{
    if(scanScheduled) return;
    scanScheduled=true;
    setTimeout(()=>{scanScheduled=false;scan(observedBoard);},250);
  });
  function bindBoard(board) {
    if(board===observedBoard) return;
    boardObserver.disconnect();
    observedBoard=board;
    if(!board) return;
    const options={subtree:true,childList:true,attributes:true,attributeFilter:['class','data-turn','game-fen','fen']};
    boardObserver.observe(board,options);
    if(board.shadowRoot) boardObserver.observe(board.shadowRoot,options);
  }
  const redraw=()=>{
    if(!window.ChessPilotOverlay.last || redrawScheduled) return;
    redrawScheduled=true;
    requestAnimationFrame(()=>{
      redrawScheduled=false;
      if(window.ChessPilotOverlay.last) window.ChessPilotOverlay.draw(window.ChessPilotOverlay.last.from,window.ChessPilotOverlay.last.to);
    });
  };
  let redrawScheduled=false;
  setInterval(()=>{ const board=boardElement(); bindBoard(board); scan(board); },1000);
  window.addEventListener('resize',redraw,{passive:true});
  window.addEventListener('scroll',redraw,{passive:true});
  scan();
  notify('Chess.com • DOM monitor aktif');
})();
"""
    }
}
