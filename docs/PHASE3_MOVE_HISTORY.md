# Phase 3 — Move History Ground Truth

## Implementation
Added standard-start SAN movetext replay using Chesslib's documented MoveList parser and Board move API.

Each move is validated against the board before application. The replay returns:
- final six-field FEN
- SAN move list
- FEN snapshot after every ply

The replay therefore preserves turn, castling rights, en-passant target, halfmove clock and fullmove number based on actual move history, rather than guessing from the board diagram.

## Input boundary
This API accepts SAN mainline movetext from the standard starting position. It does not yet accept arbitrary PGN files with header tags, comments, recursive variations, or custom starting positions. Those require a dedicated PGN document parser.

## Security and robustness
- 200 KB input cap
- 1,000-ply cap
- rejects blank and unparseable input
- rejects illegal move sequence
- no engine output is created here

## Regression tests
- opening mainline to known final FEN
- castling-right and move counter preservation
- empty/oversized input
- invalid SAN
- intermediate FEN after each ply
