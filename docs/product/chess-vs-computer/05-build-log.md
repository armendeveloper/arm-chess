# Build log: Chess vs Computer (Android, Kotlin)

## 2026-10-14 — T-1: Project scaffold that builds and tests on this machine

**Stories:** enabler for all; guardrail "no INTERNET permission".

### What changed and why
- Generated the Gradle wrapper (9.8.1, `bin`) from the cached Gradle 9.3.1 with only a bare `settings.gradle.kts`, as the plan requires (AGP 9.4 needs Gradle >= 9.6).
- Added the version catalog with every version from design §1.2, the root build (`apply false` plugins, including `kotlin.jvm` to align the KGP used by AGP's built-in Kotlin) and `gradle.properties` per design §1.4.
- `:engine`: `kotlin("jvm")`, bytecode target 17 (no `jvmToolchain`), `kotlin-test-junit` + JUnit 4. `Smoke` placeholder object plus `SmokeTest`.
- `:app`: AGP application with built-in Kotlin (no `kotlin-android`), Compose compiler and serialization plugins. `namespace = "chess.app"`, `applicationId = "io.github.armendeveloper.armchess"` (decision H-1), `compileSdk 37`, `minSdk 26`, `targetSdk 36`, Java 17 bytecode, Compose BOM + Material 3, Robolectric with `isIncludeAndroidResources = true`. `MainActivity` calls `enableEdgeToEdge()` and shows the app label "Arm Chess" from `strings.xml`. The manifest strips `INTERNET` with `tools:node="remove"`. `implementation(project(":engine"))`.
- `.gitignore` covers `.gradle/`, `build/`, `.kotlin/`, `local.properties`, `keystore.properties`, `*.jks`, `*.keystore`, `.idea/`, `*.iml` and `.claude/`.
- `.gitattributes` keeps `gradlew` LF and `*.bat` CRLF, and `gradlew` is committed as executable (`100755`), so the wrapper runs on Linux CI (T-27) from a Windows checkout.
- `local.properties` (ignored) points at `C:\Users\armen\AppData\Local\Android\Sdk`.

### Files touched
`settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.{jar,properties}`, `gradlew`, `gradlew.bat`, `.gitignore`, `local.properties` (ignored), `engine/build.gradle.kts`, `engine/src/main/kotlin/chess/engine/Smoke.kt`, `engine/src/test/kotlin/chess/engine/SmokeTest.kt`, `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`, `app/src/main/kotlin/chess/app/MainActivity.kt`, `app/src/main/res/values/strings.xml`, `app/src/test/kotlin/chess/app/MainActivityTest.kt`.

### Tests added (written first, seen failing)
- `chess.engine.SmokeTest` — red: `Unresolved reference 'Smoke'`; green after adding `Smoke`.
- `chess.app.MainActivityTest` (Robolectric + `v2.createAndroidComposeRule<MainActivity>()`, asserts "Arm Chess" is displayed) — red: `Unresolved reference 'MainActivity'`; then red at runtime with Robolectric's JDK 21 module-access error (below); green after the fix. Runs on Robolectric's API 36 runtime (`android-all-instrumented 16-robolectric-13921718`).

### Resolved toolchain (all as pinned in design §1.2; no version changes)
Gradle 9.8.1 · AGP 9.4.1 · Kotlin 2.4.21 · Compose BOM 2026.09.00 · activity-compose 1.13.0 · core-ktx 1.19.1 · Robolectric 4.17 · androidx.test.ext:junit 1.3.0 · JUnit 4.13.2 · compileSdk 37 (`platforms/android-37.0`) · build-tools 37.0.0 · JDK Temurin 21.0.12.1.

### Commands run
- `./gradlew --version` → Gradle 9.8.1, launcher JVM 21.0.12.1.
- `./gradlew :engine:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` → **BUILD SUCCESSFUL**. `:engine:test` 1 test, 0 failures; `:app:testDebugUnitTest` 1 test, 0 failures; lint 0 errors, 2 warnings (`MissingApplicationIcon`, `OldTargetApi`).
- `apkanalyzer manifest permissions app/build/outputs/apk/debug/app-debug.apk` → `io.github.armendeveloper.armchess.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` only. No `android.permission.INTERNET`.

### Deviations from the plan
- **Robolectric 4.17 on JDK 21 needs a JVM flag.** Without it, every Robolectric test fails in `AndroidInterceptors$FileDescriptorInterceptor.setInt` with `IllegalAccessException: ... cannot access class jdk.internal.access.SharedSecrets (in module java.base) because module java.base does not export jdk.internal.access to unnamed module`. 4.17 is the latest Robolectric on Maven Central, so the fix is `testOptions.unitTests.all { it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED") }` in `app/build.gradle.kts`. Design §1.4 could mention it; not edited because no pinned version changed.
- **apkanalyzer is not empty.** The plan says the permission check "prints nothing". The debug APK lists `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, the signature-level permission that `androidx.core` (1.9+) declares and uses for itself to protect non-exported dynamic receivers. It is not a user-facing permission and is not `INTERNET`. The T-27 CI check should grep for `INTERNET` rather than assert empty output.
- The smoke text is "Arm Chess" (the app label from `strings.xml`) instead of "Chess", so the test and the UI share one string. T-2's `LaunchSmokeTest` should assert "Arm Chess" too.
- `createAndroidComposeRule` from `ui-test-junit4` is deprecated in this BOM; used the `androidx.compose.ui.test.junit4.v2` variant (StandardTestDispatcher). Later Compose tests should use the v2 rules.
- The manifest uses the platform theme `@android:style/Theme.Material.Light.NoActionBar` as a stand-in; T-12 adds the real theme, splash and icon.

### Follow-ups
- T-12: launcher icon (clears `MissingApplicationIcon`), app theme, `allowBackup=false`, debug `applicationIdSuffix`.
- `OldTargetApi` lint warning is expected (targetSdk 36 is deliberate, design §1.3).
- A plugin (AGP or KGP, not our scripts) calls the deprecated `Configuration.setVisible(boolean)`; Gradle warns it will be removed in Gradle 11. Nothing to do on our side.
- AGP warns `Unable to strip ... libandroidx.graphics.path.so` (no NDK strip tool configured); harmless for debug.

## 2026-10-14 — T-3: Core types, 0x88 board and FEN

**Stories:** US-4, US-2 (FEN in save).

### What changed and why
- `Square` (0..63 public squares, a1 = 0): `of`/`file`/`rank`, `to0x88` (`sq + (sq and 56)`), `from0x88`, `isOnBoard0x88` (`sq88 and 0x77.inv() == 0`, exact for any `Int`, so negative step results are off-board too), `name`, `parse`/`parseOrNull`, constants for the castling squares (a1–h1, a8–h8) and `Square.NONE = -1`.
- `Piece` raw codes `(color shl 3) or type` (white 1..6, black 9..14, `NONE = 0`); colour codes 0/1 double as array indices. FEN letter conversion. Public enums `PieceColor` (with `opposite`) and `PieceType` (with `uciChar`) for the app.
- `Move`: `@JvmInline value class Move(val bits: Int)` — from(6) · to(6) · promotion type(3) · flags(4: `CAPTURE`, `DOUBLE_PUSH`, `EN_PASSANT`, `CASTLE`), `uci()`, `promotionType`, boolean flag accessors, `Move.NONE` (a1a1). `Move.of` does no range checks (hot path; documented precondition). Documented conventions for T-4/T-5: en passant also carries `CAPTURE`; castling is the king's move with `CASTLE`.
- `Board` (internal, mutable): 0x88 `IntArray(128)` of pieces, king squares kept in step by `setPiece`, side to move, castling bits (`CastlingRights`), ep square, halfmove clock, fullmove number; `pieceAt(sq)`, `piece88(sq88)`, `copy`, `clear`, `equals`/`hashCode` over every field (T-4's make/unmake round-trip test needs it), and a diagram `toString`. All accessors take 0..63 squares; only the piece array is 0x88.
- `Fen.parse`/`format` (internal; the app will use `Position.fromFen`/`toFen` from T-6) and public `FenException : IllegalArgumentException`. Validation, each with its own message: empty FEN, field count (6, or 4 without clocks as in the Kiwipete FEN, defaulting to `0 1`), 8 ranks, 8 files per rank, invalid characters (including `0` and `9`), exactly one king per side, no pawns on ranks 1/8, side to move, castling field syntax and duplicates, castling rights vs king and rook squares, ep square syntax, ep rank vs side to move, the double-pushed pawn in front of the ep square, ep and origin squares empty, and clocks as plain decimal digits (halfmove ≥ 0, fullmove ≥ 1, no overflow). The castling field is written back in canonical `KQkq` order.
- Removed the T-1 placeholder `Smoke`/`SmokeTest` (its comment says it exists only until T-3 adds the board types); the new board tests prove the JVM test runner instead.

### Files touched
`engine/src/main/kotlin/chess/engine/board/{Square,Piece,Move,Board,Fen}.kt` (new), `engine/src/test/kotlin/chess/engine/board/{SquareTest,PieceTest,MoveTest,BoardTest,FenTest}.kt` (new), `engine/src/main/kotlin/chess/engine/Smoke.kt` and `engine/src/test/kotlin/chess/engine/SmokeTest.kt` (deleted), this log.

### Tests added (written first, seen failing)
Red: `compileTestKotlin` failed with `Unresolved reference 'Board'`, `'Piece'`, `'Square'`, `'CastlingRights'` and so on. Green after the implementation. 50 tests:
- `SquareTest` (9): design indices, named constants, name/parse round-trip for all 64 squares, file/rank, 0x88 round-trip and layout (exactly 64 of 128 indices on board), off-board steps in all four directions, 9 invalid names rejected, out-of-range `name`.
- `PieceTest` (5): colour/type packing for all 12 pieces, distinct 4-bit codes, constants, FEN letters, enum helpers.
- `MoveTest` (6): pack/unpack of every from × to × promotion (none, N, B, R, Q) × all 16 flag combinations (1,310,720 moves), flag accessors, equality, `Move.NONE`, UCI for quiet, castling, en passant and all four promotions, `promotionType`.
- `BoardTest` (7): empty defaults, 0x88 storage, king tracking through `setPiece`, deep `copy`, `equals` sensitive to each scalar field, `clear`, diagram.
- `FenTest` (23): start position parsed square by square; round-trip of the start position and all 7 perft FENs of design §4.2 (4-field Kiwipete comes back with `0 1`); scalar fields; ep round-trip for both sides; canonical castling order; whitespace tolerance; large clocks; and 15 rejection tests covering 42 invalid FENs, each asserting its specific message.
- The first green run had one failure in my own new test (`scalarFieldsAreParsed` expected a black pawn on a1 in Position 4 mirrored; it is on a2, a1 is a white rook). Fixed the assertion; the code was right.

### Commands run
- Baseline before changes: `./gradlew :engine:test` → BUILD SUCCESSFUL (SmokeTest).
- `./gradlew --rerun-tasks :engine:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` → **BUILD SUCCESSFUL**, 59 tasks executed. `:engine:test` 50 tests, 0 failures, 0 skipped; `:app:testDebugUnitTest` 1 test, 0 failures; lint 0 errors, 2 warnings (`OldTargetApi`, `MissingApplicationIcon`, both known from T-1); `app-debug.apk` built. No Kotlin compiler warnings.

### Deviations from the plan
- The colour enum is `PieceColor` (as the plan's T-3 line says) rather than `Color` (design §4.1), which also avoids clashing with Compose's `Color` in `:app`.
- `Board` and `Fen` are `internal` (design §4.1 says `Board` is internal; the public FEN entry point is `Position.fromFen`/`toFen` in T-6). `Square`, `Piece`, `PieceColor`, `PieceType`, `Move` and `FenException` are public.
- The ep check is stricter than the plan's "rank 3 or 6": the rank must match the side to move, the pushed pawn must be in front of the ep square, and the ep and origin squares must be empty. Real FENs (which only write an ep square after a double push) all pass this.
- Not checked yet: the side not to move being in check (design §4.1 mentions it for `Position.fromFen`) needs `Attacks.isAttacked` from T-4. Piece-count sanity (e.g. 9 pawns) is not checked; the plan does not ask for it.

### Follow-ups
- T-4/T-6: reject a FEN where the side not to move is in check (in `Position.fromFen`, once attacks exist).
- T-4: make/unmake will keep king squares through `setPiece`; if profiling shows the king-tracking branch matters, move king updates into make/unmake.

## 2026-10-14 — T-4: Move generation, make/unmake, legality and perft (start position)

**Stories:** US-4.

### What changed and why
- `Attacks` (internal): `isAttacked(board, sq, by)` scans outward from the target square (pawn squares, knight and king leaps, then diagonal and orthogonal rays to the first piece), as design §4.2 describes. `isInCheck(board, color)` wraps it for the king. The 0x88 step tables (`KNIGHT_STEPS`, `KING_STEPS`, `DIAGONALS`, `ORTHOGONALS`) live here and `MoveGen` reuses them.
- `MoveGen` (internal): `generatePseudoLegal(board, moves: IntArray): Int` writes `Move` bits into a caller-owned buffer of `MAX_MOVES = 256`. It covers pawn pushes, double pushes (flag `DOUBLE_PUSH`) and captures (`CAPTURE`), knight and king steps, and bishop, rook and queen rays. `generateLegal` filters in place (make, test the mover's king with `Attacks.isAttacked`, unmake). `legalMoves(board): List<Move>` is an allocating convenience for tests and later `Position`.
- `Board.make(move)` / `unmake(move)` with a preallocated undo stack (4 ints per ply: captured piece, castling rights, ep square, halfmove clock; 256 plies to start, doubles when full). `make` sets the ep square behind every double push and clears it otherwise, resets the halfmove clock on pawn moves and captures (else +1), bumps the fullmove number after Black's move, and moves the king square directly (not through `setPiece`, which closes the T-3 follow-up). `unmake` restores every field, so `Board.equals` holds; it throws `IllegalStateException` with an empty history. `copy()` and `clear()` start with an empty history, and the history is not part of `equals`.
- `Perft` (public object): `perft(fen, depth): Long` and `divide(fen, depth): Map<String, Long>` (UCI-sorted), as design §4.2 makes them public, plus internal `Board` overloads. It has one move buffer per depth (no allocation in the recursion) and bulk-counts at depth 1.
- **Seams for T-5** (marked `// T-5:` in the code):
  - `make` throws `UnsupportedOperationException` for `CASTLE`, `EN_PASSANT` or any promotion. It does not silently half-apply them.
  - The castling, en passant and promotion branches and the castling-right updates go where the `// T-5` comment sits in `make`.
  - Pawn moves onto the last rank are **not generated** yet; T-5 turns each into four promotions in `addPawnMoves`.
  - En passant goes in `addPawnCapture`, and castling with the king's steps in `generatePseudoLegal`.
  - Castling rights are not yet cleared when a king or rook moves. Nothing reads them until castling is generated.

### Files touched
`engine/src/main/kotlin/chess/engine/board/{Attacks,MoveGen,Perft}.kt` (new), `engine/src/main/kotlin/chess/engine/board/Board.kt`, `engine/src/test/kotlin/chess/engine/board/{AttacksTest,MoveGenTest,MakeUnmakeTest}.kt` (new), `engine/src/test/kotlin/chess/engine/perft/PerftTest.kt` (new), this log.

### Tests added (written first, seen failing)
Red: `compileTestKotlin` failed with `Unresolved reference 'Attacks'` (and `MoveGen`, `Perft`, `make`, `unmake`). Green after the implementation, with no test changes needed. 44 new tests (engine total 94):
- `PerftTest` (7):
  - initial position depth 0–4 = 1 / 20 / 400 / 8,902 / 197,281;
  - Position 3 depth 1–2 = 14 / 191 (extra; no special moves occur before depth 3 there);
  - `divide` at depth 3 has 20 root moves summing to 8,902, with e2e4 600, d2d4 560, a2a3 380 and g1f3 440, in UCI order;
  - `divide` at depth 1;
  - the FEN overloads match the `Board` ones;
  - the board is unchanged after perft;
  - negative perft depth and divide depth 0 are rejected.
- `MakeUnmakeTest` (11):
  - Make/unmake of **every legal move and every reply** (depth 2) in all 7 FENs of design §4.2 restores the board (`equals` on every field). Each make also changes the board.
  - Field updates after a quiet move (checked against the expected FEN), a double push, a capture, a piece move and a pawn move (halfmove/fullmove).
  - The king square follows the king and comes back.
  - A 10-ply line with captures and checks reaches the expected FEN and unwinds to the start.
  - A 600-ply knight shuffle grows the undo stack and unwinds exactly.
  - `unmake` with no history fails, including on a fresh `copy()` and after `clear()`.
  - Castling, en passant and promotion are rejected until T-5, and the board is untouched.
- `MoveGenTest` (15):
  - the 20 start moves, and Black's 20 after 1.e4;
  - the `DOUBLE_PUSH` and `CAPTURE` flags;
  - pawns capture only enemies and only diagonally; pushes stop at blockers; no double push off the start rank;
  - sliders stop at the first piece and capture only enemies;
  - **pins**: a rook pinned on a file, a knight pinned on a diagonal (no moves), a bishop pinned on a diagonal and a pawn pinned diagonally (it may only capture the pinner);
  - the king cannot step onto attacked squares, or retreat along the checking ray;
  - in check, exactly the 9 evasions (blocks, the capture and king steps) are legal;
  - pseudo-legal moves include the pinned knight's moves;
  - generation leaves the board unchanged.
- `AttacksTest` (11):
  - the start position's attack map;
  - white and black pawn directions, and no pawn wrap at the a- and h-files;
  - knight leaps, including from a corner;
  - king squares;
  - rook rays blocked on file and rank; bishop rays blocked, with no wrap from a3 to h3;
  - queen rays;
  - colour filtering;
  - `isInCheck` with and without a blocker.

### Perft results and timing
- All perft counts in the plan's T-4 list match the Chessprogramming wiki (above).
- Wall time on the dev machine, from the JUnit XML of the gate run below: `initialPositionDepth0To4` **0.033 s**, and the whole `PerftTest` class **0.091 s**. The JVM was already warm from the earlier test classes.
- Extra check, not committed: a throwaway test ran start-position perft 5 and got **4,865,351**. That is exactly 4,865,609 minus the 258 en passant captures the wiki lists at that depth, so everything except en passant is already right at depth 5. It took **263 ms** after a depth-4 warm-up (about 18.5 M leaf nodes/s with bulk counting). The plan's T-5 gate time is recorded by T-5.

### Commands run
- Baseline before changes: `./gradlew :engine:test` → BUILD SUCCESSFUL, 50 tests.
- Red: `./gradlew :engine:test` → `compileTestKotlin` FAILED (unresolved references above).
- `./gradlew :engine:test` → BUILD SUCCESSFUL, 94 tests, 0 failures.
- GATE: `./gradlew --rerun-tasks :engine:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` → **BUILD SUCCESSFUL**, 59 tasks executed.
  - `:engine:test`: 94 tests, 0 failures, 0 skipped.
  - `:app:testDebugUnitTest`: 1 test, 0 failures.
  - Lint: 0 errors, 2 warnings (`OldTargetApi`, `MissingApplicationIcon`, both known since T-1).
  - `app-debug.apk` built. No Kotlin compiler warnings.

### Deviations from the plan
- **Signatures.**
  - `Attacks.isAttacked` takes the board explicitly (`isAttacked(board, sq, by)`); `Attacks` is a stateless object rather than a `Board` method.
  - `Perft` is in the `board` package as the plan's file list says (`engine/.../board/Perft.kt`), while its test is in `chess.engine.perft` (also as listed).
  - `perft`/`divide` are public through FEN-string overloads, because `Board` is internal.
- **Extra test files.** `AttacksTest` and `MoveGenTest` were added beyond the plan's `PerftTest`/`MakeUnmakeTest`, for attack detection and the pin cases. The plan asked for two pin positions; there are four.
- **Make/unmake round-trip** covers depth 2 (every move and every reply), not only the root moves.
- **Not done here (as planned).** FEN rejection when the side not to move is in check is not in T-4's plan. It stays a follow-up for `Position.fromFen` (T-6); `Attacks.isAttacked` now makes it a few lines.

### Follow-ups
- T-5:
  - Replace the `UnsupportedOperationException` guard in `Board.make` with the special-move branches.
  - Delete or replace `MakeUnmakeTest.castlingEnPassantAndPromotionAreRejectedUntilT5`.
  - Generate promotions where `addPawnMoves` currently skips last-rank moves.
  - Clear castling rights on king or rook moves and rook captures.
  - Start position depth 5 should then give 4,865,609.
- `MAX_MOVES = 256` is sized for legal plus pseudo-legal moves. T-5 adds promotions; no realistic position comes near it, but the search (T-8) should keep using it rather than a smaller buffer.
- T-6: reject a FEN where the side not to move is in check (`Attacks.isInCheck(board, 1 - board.sideToMove)`).
