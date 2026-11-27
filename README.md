# ChessPilot

ChessPilot is an Android companion shell for Chess.com, with a staged path toward safe board recognition and post-game / bot-game analysis.

## Current milestone: Android shell + chess core

- Kotlin Android application with embedded Chess.com WebView
- HTTPS navigation restricted to chess.com and its subdomains
- File/content access disabled in WebView
- Origin-restricted AndroidX WebMessageListener (no page-to-native commands enabled yet)
- Strict structural six-field FEN parser
- Chesslib 1.3.7 adapter for legal move generation and UCI move checks
- JUnit regression tests
- GitHub Actions CI builds a debug APK and uploads test reports

## Build and test

CI uses JDK 17 and Gradle 8.11.1:

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

## Roadmap

1. Verify Chess.com WebView login and navigation on a physical device.
2. Inspect the actual Chess.com board DOM and supported metadata.
3. Implement DOM extraction and cross-check it with verified PGN/FEN positions.
4. Add Vision fallback and piece classifier.
5. Add PGN replay as the source of truth for side-to-move, castling, en-passant, and counters.
6. Integrate Stockfish after position validation.
7. Add fair-play mode gating and comprehensive Android device tests.

## Fair-play boundary

No engine recommendations during an ongoing game against a human. Analysis is intended for permitted bot games and completed-game review. Unknown game mode or uncertain board state disables engine output.

## Current limitations

- No DOM reader or Vision classifier yet.
- No Stockfish binary or UCI engine integration yet.
- The FEN parser checks structure; Chesslib generates legal moves from a loaded FEN, but this does not prove a FEN was reached from authentic game history.
- Physical-device installation and WebView runtime testing remain outstanding.
