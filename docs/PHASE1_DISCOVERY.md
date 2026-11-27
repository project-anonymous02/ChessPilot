# Phase 1 — Discovery and Android Foundation

## Confirmed decisions

- Android-native shell: Kotlin.
- Embedded Chess.com browser: Android WebView.
- Board recognition: DOM-first, Vision fallback, subject to evidence from current page behavior.
- Engine: Stockfish, to be integrated only after position validation and game-mode gating.
- The application does not expose a general-purpose JavaScript-to-native bridge.
- External navigations are blocked from the embedded WebView.

## Phase 1 exit criteria

- [x] Repository and project scaffold created.
- [x] Navigation allowlist extracted into a separately testable policy.
- [x] Positive and negative host allowlist tests added.
- [x] CI workflow added for JVM unit tests and debug APK assembly.
- [ ] CI build observed to complete successfully.
- [ ] Physical Android device smoke test.
- [ ] Chess.com login and redirect behavior checked.
- [ ] Actual board DOM inspected.
- [ ] DOM extraction accuracy compared against PGN/FEN ground truth.

## Test matrix

| Input | Expected |
|---|---|
| https://chess.com | Allow |
| https://www.chess.com | Allow |
| https://accounts.chess.com | Allow |
| http://chess.com | Block |
| https://evilchess.com | Block |
| https://chess.com.evil.example | Block |
| https://example.com | Block |
| Missing host | Block |

## Important constraints

A board diagram alone does not encode all FEN metadata. Castling rights, en-passant target, halfmove clock, fullmove number, and side to move must be sourced from verified move history or other reliable page state. The reader must never invent these fields.

The current Android app is only the container and navigation shell. It makes no move recommendations. No engine analysis may be exposed during an ongoing game against a human.
