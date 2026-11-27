# Phase 4 — Stockfish 19 ARM64 UCI integration

## Original milestone
This phase introduced offline FEN analysis. The original milestone was limited to the native FEN analysis workspace; live Chess.com board monitoring and the SVG overlay were added in later implementation work.

## Current runtime
- Official Stockfish 19 Android ARM64 universal release binary.
- CI pins the release asset URL and verifies the release archive SHA-256 before packaging.
- Binary is packaged as `libstockfish.so` under `arm64-v8a`; legacy JNI packaging makes it available through `nativeLibraryDir` for `ProcessBuilder`.
- One persistent UCI process is reused across positions. The single worker owns UCI output reads.
- Engine settings: `Threads=1`, `Hash=48 MB`, default depth 12, bounded depth 1–20.
- FEN validation and legal-move generation run on the background worker, not the Android UI thread.
- A newer request invalidates queued work and sends `stop` to an active search. Request replacement and the transition to `go` are synchronized to prevent a stale search from starting after its stop command.
- The Chess.com monitor prefers a complete six-field FEN from the game state or board attributes. The DOM-only fallback cannot recover move-history metadata; it remains available but its engine recommendation is explicitly labelled as an estimate.

## UCI contract
1. Start the ARM64 executable.
2. Send `uci`; wait for `uciok`.
3. Set Threads and Hash.
4. Send `isready`; wait for `readyok`.
5. Send `position fen <six-field-fen>`.
6. Send `go depth <bounded-depth>`.
7. Parse info depth, score cp/mate, PV and bestmove/ponder.
8. Send `quit` and release process resources during shutdown.

## Tests and verification
- UCI centipawn score and principal variation parsing.
- UCI mate score parsing.
- Ignore malformed/non-info lines.
- Bestmove and ponder parsing.
- Reject malformed bestmove.
- FEN, chess rules, SAN replay and host policy regression suite runs in CI.
- The Android CI workflow also downloads and verifies the ARM64 Stockfish archive and assembles a debug APK.

## Not yet proven
A successful Android build proves compilation and packaging, not that the native executable can run on every device ROM. Physical-device execution, thermal/battery behavior, and WebView overlay positioning still require device testing.
