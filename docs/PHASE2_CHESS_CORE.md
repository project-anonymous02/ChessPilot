# Phase 2 — Chess Core

## What is implemented
- Strict six-field FEN syntax and structural checks.
- Position representation ordered rank 8 to rank 1.
- Square lookup and FEN serialization.
- Chesslib adapter for loading a FEN and generating legal UCI moves.
- UCI move membership check against the legal move list.
- JUnit tests for standard opening move counts and a position after 1.e4.

## Important distinction
The structural parser and Chesslib move generator do not prove that a position was reached through a valid historical game. A later PGN replay layer must validate the full sequence of moves and preserve the metadata that a board diagram alone cannot reveal.

## Dependency
Chesslib 1.3.7 is resolved from JitPack. It is an Apache-2.0 Java chess library with documented FEN, PGN, and legal-move APIs.

## Phase 2 acceptance
- [ ] CI resolves Chesslib 1.3.7.
- [ ] Start position returns 20 legal moves.
- [ ] e2e4 is accepted; e2e5 is rejected.
- [ ] Position after 1.e4 gives a valid black-to-move list.
- [ ] Debug APK packages successfully.
