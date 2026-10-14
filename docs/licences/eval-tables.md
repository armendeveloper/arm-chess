# Evaluation tables: provenance

- **What:** the piece values and piece-square tables in `engine/src/main/kotlin/chess/engine/search/Pst.kt` and `Eval.kt` (design 03 §4.5).
- **Source of the shape:** the "Simplified Evaluation Function" by Tomasz Michniewski, as described on the [Chessprogramming wiki](https://www.chessprogramming.org/Simplified_Evaluation_Function). Design §4.5 asks for the tables to start from that shape and for the source to be recorded here.
- **Written by:** the T-8 build, 2026-10-14. The tables were typed from the developer's knowledge of the published function. The wiki page was not downloaded or copied in this session.

| Table | Origin |
|---|---|
| Piece values P 100, N 320, B 330, R 500, Q 900 | Simplified Evaluation Function values (also design §4.5) |
| Pawn (middlegame), knight, bishop, rook (middlegame), queen, king (middlegame), king (endgame) | The published Simplified Evaluation Function tables, as recalled (not re-checked against the page) |
| Pawn (endgame): a flat bonus per rank, rising to 90 on the seventh | Our own |
| Rook (endgame): 10 on the seventh rank, 5 on the eighth, otherwise 0 | Our own |
| Middlegame-to-endgame tapering by non-pawn material (N 1, B 1, R 2, Q 4, max 24), bishop pair 30, tempo 10, mop-up `10 × lone king's centre distance + 4 × (14 − king distance)` | Our own choices of common engine techniques |

## Licence note

These are short tables of numbers that describe a widely used evaluation heuristic. They are recorded here for provenance, as the design asks. No licence text ships with the app for them, and the T-8 build did not review licences. If the release review (T-26/T-27) decides that an attribution is wanted, the About screen string would credit Tomasz Michniewski's Simplified Evaluation Function.
