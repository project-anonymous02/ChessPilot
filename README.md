# ChessPilot

ChessPilot is an Android chess training and post-game review companion powered by Stockfish 19 (ARM64).

## Current app experience
- Native chessboard is the main screen, not a browser with a bottom analysis form.
- A best-move arrow is drawn directly on the board.
- Editing the training/review FEN triggers validation and analysis automatically after a short debounce; no Analyze button is required.
- The first launch displays a Fair Play warning.
- App launcher icon uses a chess knight motif.
- Stockfish runs locally on ARM64; no cloud analysis service is required.

## Fair Play boundary
Do not use engine recommendations during live games against human opponents on Chess.com or other platforms. That violates platform fair-play rules and may lead to account sanctions. ChessPilot's engine workflow is intended for training positions, permitted bot games, and completed-game review.

This milestone intentionally does not read or overlay a live human game's board from Chess.com. It does not automate moves.

## Open-source design references
See docs/OPEN_SOURCE_REFERENCES.md for the reviewed chessboard and screenshot-recognition projects, their licenses, the design concepts used, and limitations that must be addressed before adopting screenshot recognition.

## Current limitations
- Position entry is currently by FEN; automatic Chess.com board recognition is not implemented.
- The board currently renders the single best-move arrow, not Multi-PV candidate arrows.
- PGN import/replay and automatic completed-game detection remain future work.
- Stockfish execution must still be smoke-tested on a physical ARM64 Android device.

## Build
Run the Android CI workflow. The debug APK is produced by the workflow artifact when tests and assembly succeed.

## Third-party notices
Stockfish is GPL-3.0. See THIRD_PARTY_NOTICES.md.
