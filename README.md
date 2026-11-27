# ChessPilot

ChessPilot is an Android companion shell for Chess.com with a verified chess-position core and an offline Stockfish analysis engine.

## Current milestone: Stockfish 19 ARM64 integration

- Kotlin Android application with Chess.com in WebView
- HTTPS navigation limited to Chess.com and its subdomains
- WebView local file/content access disabled
- Strict structural parser for six-field FEN
- Chesslib adapter for legal move generation and UCI legality checks
- SAN movetext replay from the standard starting position
- Stockfish 19 ARM64 UCI executable, fetched from the official release and SHA-256 verified in CI
- Native engine process runs off the UI thread with bounded search depth and serialized requests
- UCI info, score, principal variation, bestmove and ponder parsing
- Automated GitHub Actions unit tests and debug APK artifact

## Build and test

CI uses JDK 17, Gradle 8.11.1 and packages the official Stockfish 19 ARM64 binary:

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

The current APK targets arm64-v8a devices. The engine binary is licensed under GPL-3.0; see THIRD_PARTY_NOTICES.md and the official Stockfish source repository.

## Fair-play boundary

Stockfish is exposed as an offline FEN analysis tool for training positions and completed-game review. Do not use engine recommendations during an ongoing game against a human. The current milestone does not read live game positions from Chess.com and does not draw move hints over its board.

## Roadmap

1. Verify engine startup and UCI output on physical Android.
2. Add complete PGN document import, including tags, comments and variations.
3. Inspect actual Chess.com DOM for permitted post-game review pages.
4. Build DOM reader and compare it against PGN replay ground truth.
5. Add Vision fallback for supported screenshots.
6. Add strict game-mode and completed-game gating before any page-derived analysis.

## Limitations

- The engine is currently exposed through manual FEN input; automatic board recognition is not implemented.
- Stockfish ARM64 is bundled; 32-bit and x86 Android ABIs are not packaged.
- Device installation and native process execution still need a physical Android smoke test.
- SAN replay accepts mainline SAN movetext from the standard starting position, not arbitrary PGN documents with headers/comments/variations.
