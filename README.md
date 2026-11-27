# ChessPilot

ChessPilot is an Android companion for Chess.com, with a local Stockfish 19 ARM64 engine for position analysis and training/review.

## Current app experience
- Chess.com is the primary screen, opened in an Android WebView restricted to HTTPS Chess.com pages; other HTTPS sites are opened externally.
- The live board monitor first reads a complete six-field FEN from Chess.com's game state or board attributes.
- When a complete FEN is unavailable, a DOM piece-placement fallback remains available. Because board placement alone cannot reveal castling rights, en-passant history, or move counters, recommendations from this fallback are explicitly labelled as estimates.
- Stockfish runs locally in a persistent UCI process on a background worker; newer positions replace queued/stale analysis.
- A native analysis workspace is available for user-supplied training/review FEN positions.

## Fair Play
Do not use engine recommendations during live games against human opponents. That violates Chess.com Fair Play rules and can lead to account sanctions. Use engine assistance only in contexts where it is expressly permitted, such as appropriate training positions, permitted bot/practice modes, or completed-game review.

## Current limitations
- DOM fallback analysis cannot recover game-history metadata. Its results may miss castling or en-passant options and are marked as estimates; prefer a complete Chess.com FEN for accurate analysis.
- Screenshot/vision recognition is not implemented.
- PGN parsing/replay exists in the chess core but is not yet wired into a user-facing import/replay flow.
- Completed-game detection is not implemented.
- Stockfish execution and overlay positioning still require physical ARM64 Android device testing across supported WebView/device versions.

## Build
Run the Android CI workflow. It downloads and SHA-256 verifies the official Stockfish 19 ARM64 binary, runs unit tests, and assembles the debug APK. A local APK build can complete without the binary, but engine execution requires `libstockfish.so` to be present in the app's `arm64-v8a` native library directory.

## Third-party notices
Stockfish is GPL-3.0. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
