# Phase 4 — Stockfish 19 ARM64 UCI integration

## Scope
This phase adds an offline FEN analysis engine. It does not inspect a live Chess.com game, inject recommendations into a live board, or provide move assistance during human-vs-human games.

## Runtime
- Official Stockfish 19 Android ARM64 universal release binary.
- CI pins the release asset URL and verifies the release asset SHA-256 before packaging.
- Binary is packaged as app native library `libstockfish.so` under `arm64-v8a`, with legacy packaging so Android extracts it to nativeLibraryDir.
- ProcessBuilder starts the executable; UCI communication uses stdin/stdout.
- Engine runs on a single background executor, not the UI thread.
- Threads=2, Hash=48 MB, maximum depth=20 (default 12).
- Input FEN is validated before process startup.
- Engine process is destroyed after search and on explicit stop/close.

## UCI contract
1. Send `uci`; wait for `uciok`.
2. Set Threads and Hash.
3. Send `isready`; wait for `readyok`.
4. Send `position fen <six-field-fen>`.
5. Send `go depth <bounded-depth>`.
6. Parse info depth, score cp/mate, pv and bestmove/ponder.
7. Send `quit` and release process resources.

## Tests
- UCI centipawn score and principal variation parsing.
- UCI mate score parsing.
- Ignore malformed/non-info lines.
- Bestmove and ponder parsing.
- Reject malformed bestmove.
- Existing FEN, chess rules, SAN replay and host policy regression suite remains in CI.

## Not yet proven
A successful Android build proves packaging, not that the native executable can run on every device ROM. Physical device execution is still required.
