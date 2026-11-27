# ChessPilot

ChessPilot is an Android companion shell for Chess.com with a verified chess-position core.

## Current milestone: Android shell + chess core

- Kotlin Android application with Chess.com in WebView
- HTTPS navigation limited to Chess.com and its subdomains
- WebView local file/content access disabled
- Origin-restricted AndroidX WebMessageListener; no webpage-to-native commands enabled
- Strict structural parser for six-field FEN
- Chesslib adapter for legal move generation and UCI legality checks
- SAN movetext replay from the standard starting position
- Per-ply FEN snapshots preserving side-to-move, castling rights, en-passant target, and move counters
- Automated GitHub Actions unit tests and debug APK artifact

## Build and test

CI uses JDK 17 and Gradle 8.11.1:

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

## Roadmap

1. Verify WebView login and navigation on physical Android.
2. Inspect actual Chess.com board DOM and metadata.
3. Build the DOM reader and compare it against PGN replay ground truth.
4. Add Vision fallback and piece classification.
5. Integrate Stockfish after validated board state and fair-play mode gating.
6. Add complete Android UI and device testing.

## Fair-play boundary

No engine recommendations during an ongoing game against a human. Engine analysis is intended for permitted bot games and completed-game review. Unknown game mode or uncertain board state disables engine output.

## Limitations

- DOM reader and Vision classifier are not yet implemented.
- Stockfish is not integrated yet.
- SAN replay accepts mainline SAN movetext from the standard starting position, not arbitrary PGN documents with headers/comments/variations.
- Device installation and live WebView behavior still need a physical Android smoke test.
- A structurally valid FEN is not proof of a historically valid game; replay validated move history when available.
