# ChessPilot

**ChessPilot** is an Android companion shell for Chess.com. This repository starts with a secure embedded browser and a native status panel; board recognition and engine analysis are separate phases and are not yet enabled.

## Current milestone: Android foundation

- Kotlin Android application
- Chess.com hosted in Android WebView
- HTTPS-only navigation restricted to `chess.com` and its subdomains
- Local file/content access disabled in WebView
- AndroidX WebMessageListener restricted to Chess.com origins when supported
- JVM unit tests for the host allowlist
- GitHub Actions workflow that runs unit tests, assembles a debug APK, and uploads it as an artifact

## Build and test

The CI uses JDK 17 and Gradle 8.11.1:

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Roadmap

1. Verify the Android WebView login and navigation behavior on a physical device.
2. Discover the live Chess.com board DOM without assuming undocumented selectors.
3. Implement DOM position extraction and compare it against trusted PGN/FEN data.
4. Build and evaluate the Vision fallback for six piece types and empty squares.
5. Add complete position reconstruction and legality validation.
6. Integrate Stockfish only for bot games and completed-game analysis.
7. Add regression tests, performance checks, and Android device testing.

## Fair-play boundary

ChessPilot must not provide engine move recommendations during an ongoing game against a human. Engine analysis is intended for bot games where permitted and for completed-game review. If game mode or position validity is uncertain, engine output remains disabled.

## Known limitations

- No board reader is implemented yet.
- No Stockfish binary or engine integration is included yet.
- The current app has not been runtime-tested on a physical Android device.
- CI compilation and unit tests are not a substitute for WebView device testing.
