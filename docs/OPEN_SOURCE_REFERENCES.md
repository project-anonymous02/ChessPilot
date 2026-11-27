# Open-source references reviewed for ChessPilot

This document records reviewed upstream projects and what ChessPilot uses as design guidance. No source code from these projects was copied into the current native board implementation.

## 1. lichess-org/chessground

Repository: https://github.com/lichess-org/chessground  
License: GPL-3.0-or-later  
Project description: mobile/web chess UI used by Lichess.

Relevant documented capabilities:
- SVG rendering for board arrows, circles, and custom shapes.
- Arrows snap to valid moves.
- Mobile touch interactions and responsive board sizing.
- FEN position import/export.

ChessPilot currently implements a smaller native Android Canvas renderer independently: alternating squares, chess-piece glyphs, and a best-move arrow. It does not embed Chessground because Chessground is a TypeScript/browser library and its GPL licensing would require a deliberate distribution decision for any derivative integration.

## 2. KoborSoft/chess-analyser

Repository: https://github.com/KoborSoft/chess-analyser  
License: GPL-3.0 for the application; its README identifies the bundled chess-cv ONNX model as MIT-licensed.  
Project description: Android chess analysis app with on-device screenshot position recognition.

Reviewed recognition pipeline:
- BoardLocator.kt estimates a board rectangle and square size using image edge profiles, periodicity/autocorrelation, and checkerboard correlation.
- CnnRecognizer.kt crops 64 squares, runs an ONNX CNN, computes per-square confidence, and reconstructs the FEN piece-placement field.
- It flags a small set of suspicious outputs, including missing/extra kings and pawns on the first/eighth rank.

Important limitations observed in the source:
- Its recognition path assumes a fixed white-at-bottom board orientation; orientation detection must be added before using it on arbitrary screenshots.
- Its basic suspicious-state checks do not prove that the position is legally reachable.
- Confidence scores need calibration against real Chess.com layouts, themes, scaling, and overlays.
- The repository is a small prototype and should not be treated as a production-accuracy guarantee.

ChessPilot has not integrated this recognizer or model yet. A future screenshot-import feature should be restricted to training material and completed-game review, validate the resulting position with Chesslib, reject low-confidence or ambiguous recognition, and never display a move hint for a live human game.

## 3. ChessPilot implementation boundary

Current implementation:
- Uses Chesslib to validate FEN structure and legal moves.
- Uses the bundled Stockfish 19 ARM64 engine for local analysis.
- Renders the board and best-move arrow natively.
- Debounces FEN edits and discards stale analysis callbacks.
- Shows an explicit Fair Play warning.

Not implemented yet:
- Automatic Chess.com DOM position extraction.
- Screenshot recognition.
- Multi-PV arrow rendering.
- PGN import and completed-game detection.

The next recognition milestone must establish a trustworthy game context first. Unknown or live human-game contexts must not receive engine recommendations.
