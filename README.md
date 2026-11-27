# ChessPilot

ChessPilot is an Android companion for Chess.com, with a local Stockfish 19 ARM64 analysis workspace for training and post-game review.

## Current app experience
- Chess.com is the primary screen, opened in an Android WebView.
- The in-app browser is restricted to HTTPS Chess.com pages; other HTTPS sites are opened externally.
- A separate native analysis workspace is available for user-supplied training/review FEN positions.
- Stockfish runs locally; no cloud analysis service is required.

## Fair Play
Do not use engine recommendations during live games against human opponents. That violates Chess.com Fair Play rules and can lead to account sanctions. Engine analysis is intended for training positions, bot games where assistance is permitted, and completed-game review.

## Current limitations
- Chess.com is embedded as the primary browser, but automatic board/position extraction is not implemented yet.
- The native analysis workspace still requires manual FEN input.
- No engine arrows are injected into Chess.com.
- Screenshot recognition, DOM-based position recognition, PGN import/replay, and completed-game detection remain future work.
- Stockfish execution still needs a physical ARM64 Android smoke test.

## Build
Run the Android CI workflow. It runs unit tests and assembles the debug APK.

## Third-party notices
Stockfish is GPL-3.0. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
