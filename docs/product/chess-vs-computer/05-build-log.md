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

## 2026-10-14 — T-12: App shell: container, theme, navigation, splash, edge-to-edge

**Stories:** US-1 (home entry), US-9 (baseline), design §1 platform baseline.

### What changed and why
- **Container (ADR-7).** `ChessApplication` (registered in the manifest) builds one `AppContainer` per process. The container holds `AppDispatchers` (main, default and io, injectable for tests) and the app-lifetime `appScope` (`SupervisorJob() + io`, design §5.1). Comments name what T-17, T-18 and T-19 add: the DataStores, repositories, `NewGameCoordinator`, AI dispatcher, `Random` and time source.
- **StrictMode in debug** (design §12): disk reads, disk writes and custom slow calls on the main thread, with `penaltyLog`. It is gated on `BuildConfig.DEBUG`, so `buildFeatures.buildConfig = true` is now on (T-26 needs it for the version anyway).
- **Theme (design §6.1).** `ui/theme/Color.kt` holds the light and dark M3 schemes from seed #6B4E37 (Tonal spot, standard contrast).
  - Material Theme Builder is a web tool, so I generated the values offline with the same library: material-color-utilities `SchemeTonalSpot(seed, isDark, 0.0)` + `MaterialDynamicColors`, from the `com.google.android.material:material:1.13.0` jar already in the Gradle cache. The throwaway Java program stayed in the scratchpad and is not committed.
  - `ArmChessTheme(darkTheme = isSystemInDarkTheme())` follows the system. There is no dynamic colour. Typography and shapes are the M3 defaults with the system font.
- **Board tokens (design §6.2).** `BoardColors` (an immutable data class) and `ArmChessBoardColors` are provided through `LocalBoardColors` by `ArmChessTheme` and read with `ArmChessTheme.boardColors`. The palette is the same in both themes. Geometry tokens are left to T-14, which draws the board.
- **Navigation (ADR-9).**
  - `nav/Routes.kt`: `sealed interface Route` with `@Serializable data object`s `Home`, `Game`, `Stats`, `About` and `Licences`. They are namespaced (`Route.Game`) so they don't clash with the engine's `Game` type.
  - `nav/ChessNavHost.kt` wires them. Every callback goes through `dropUnlessResumed`, so a double tap mid-transition can't push a screen twice or pop past Home. Back is never intercepted.
- **Placeholder screens.**
  - `HomeScreen`: the S1 first-launch state. The title is a heading, then the tagline, **New game** (filled), **Stats** (outlined) and **About** (text button). Max width 480 dp, 24 dp side padding, scrolls.
  - `StatsScreen`: top bar + "Stats are stored only on this device."
  - `AboutScreen`: the final copy (name, version, Privacy, Credits, **View licence**, **Open-source licences**).
  - `LicencesScreen`: top bar only.
  - `GameScreen`: top bar with `arrow_back` labelled "Home".
  - Shared components in `ui/components`: `BackTopAppBar` (the title is a heading and wraps) and `SubScreen` (Scaffold + scrolling column, max width 600 dp).
- **Edge-to-edge.** `enableEdgeToEdge()` is called.
  - Every Scaffold uses `contentWindowInsets = WindowInsets.safeDrawing`, and top bars use `safeDrawing` top + horizontal.
  - Scrolling screens apply the insets as content padding inside the scroll, so content scrolls behind the navigation bar and ends above it.
- **Splash.**
  - `installSplashScreen()` runs before `super.onCreate`. The keep condition is stubbed to `false`; T-17 wires "save not loaded and < 1.5 s".
  - `Theme.ArmChess.Starting` (parent `Theme.SplashScreen`) uses the `surface` colour as its background (`values/colors.xml`, `values-night/colors.xml`) and the default icon, which will be the launcher icon from T-13.
  - `Theme.ArmChess` is the window theme (`values-night` swaps to the dark platform parent). It keeps the window background equal to `surface`, so there's no flash.
- **Backup off (design §9.2, H-5).**
  - `android:allowBackup="false"`.
  - `android:dataExtractionRules="@xml/data_extraction_rules"`: cloud backup and device transfer exclude every domain.
  - `android:fullBackupContent="@xml/backup_rules"`: the same, for API 26–30. Added to clear lint's `DataExtractionRules` warning.
- **Icons.** Nine Material Symbols (Outlined, weight 400, 24 px; Apache-2.0) as VectorDrawables: `ic_arrow_back`, `ic_undo`, `ic_flag`, `ic_add`, `ic_format_list_numbered`, `ic_info`, `ic_error`, `ic_hourglass_empty` and `ic_crop_free`.
  - The path data comes from the `google/material-design-icons` repo (`symbols/web/<name>/materialsymbolsoutlined/<name>_24px.svg`).
  - The viewport is 960 with `translateY="960"`, because Symbols use the viewBox `0 -960 960 960`.
  - `arrow_back` and `undo` are `autoMirrored` for RTL. `material-icons-extended` is not used.
- **Strings.** Every design §4.10 key is in `strings.xml`.
  - `app_name` is "Arm Chess" (H-1). The copy uses `·` (U+00B7) and `…` (U+2026), and `result_meta` is a `<plurals>`.
  - One extra key: `action_back` = "Back", for the top-bar arrow on Stats, About and Licences. §4.10 only defines "Home", for the Game screen's arrow.
- **Debug build** gets `applicationIdSuffix = ".debug"` (design §15.1; the T-1 entry lists it as a T-12 follow-up).
- **Localisation rules** from the 2026-10-14 scope change (hy and ru later, in T-29) are followed:
  - no user-visible text in Kotlin
  - plurals for counts
  - whole-sentence format strings with positional arguments
  - no fixed widths on text containers: columns have only max widths, and buttons and top-bar titles wrap

### Files touched
`app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/kotlin/chess/app/{ChessApplication,AppContainer,MainActivity}.kt`, `app/src/main/kotlin/chess/app/nav/{Routes,ChessNavHost}.kt`, `app/src/main/kotlin/chess/app/ui/theme/{Color,Theme,BoardColors}.kt`, `app/src/main/kotlin/chess/app/ui/components/{BackTopAppBar,SubScreen}.kt`, `app/src/main/kotlin/chess/app/ui/home/HomeScreen.kt`, `app/src/main/kotlin/chess/app/ui/stats/StatsScreen.kt`, `app/src/main/kotlin/chess/app/ui/about/{AboutScreen,LicencesScreen}.kt`, `app/src/main/kotlin/chess/app/ui/game/GameScreen.kt`, `app/src/main/res/values/{strings,colors,themes}.xml`, `app/src/main/res/values-night/{colors,themes}.xml`, `app/src/main/res/drawable/ic_*.xml` (9), `app/src/main/res/xml/{data_extraction_rules,backup_rules}.xml`, the tests below, and this log.

New dependencies (all already pinned in the catalog, design §1.2):
- `androidx.core:core-splashscreen`
- `androidx.navigation:navigation-compose`
- `androidx.lifecycle:lifecycle-runtime-compose`: for `dropUnlessResumed`, and needed later for `collectAsStateWithLifecycle`
- `kotlinx-coroutines-android`
- `compose-ui-tooling-preview`, plus `ui-tooling` as `debugImplementation`, for `@Preview`

### Tests added (written first, seen failing)
**Red:** the first run failed to compile (`Unresolved reference 'ChessApplication'`, `'drawable'`, `ArmChessBoardColors`, `Route`, …). All 25 new tests then passed on the first green run.

**Mutation check:** to confirm the tests catch real breakage, I made two deliberate mutations and reverted them. Making Stats' back arrow a no-op failed `statsBackButtonReturnsHome`. Hard-coding `darkTheme = false` failed `followsSystemDarkMode`.

- `chess.app.nav.ChessNavHostTest` (Robolectric, `v2.createAndroidComposeRule<MainActivity>`, 6 tests). System back goes through `onBackPressedDispatcher`.
  - Home shows the heading "Arm Chess", the tagline, and clickable **New game**, **Stats** and **About**.
  - Stats ↔ Home, by system back and by the "Back" arrow.
  - New game → placeholder Game, and back to Home by system back and by the "Home" arrow.
  - About → Open-source licences → back → About → Back → Home.
- `chess.app.ui.theme.BoardColorsTest` (plain JVM, 7 tests):
  - every §6.2 token value
  - luminance matches the §6.2 table to 0.001
  - every "Pass" pair in §5.6 meets its threshold: coordinates ≥ 4.5:1 on all four square types; ink, check, halo, glyph and mark ≥ 3:1
  - ink and check on dark squares are < 3:1, which is why the halo rule exists
  - The WCAG formula is in the test helper `chess.app.testutil.Wcag`, sanity-checked with black on white = 21:1.
- `chess.app.ui.theme.ThemeTest` (Robolectric Compose, 5 tests):
  - the light and dark schemes have the generated primary, surface, onSurface and errorContainer
  - with `@Config(qualifiers = "night"/"notnight")`, the default theme follows the system
  - board colours are identical in both themes
- `chess.app.ChessApplicationTest` (3 tests): one container per process with an active `appScope`; the standard dispatchers; `FLAG_ALLOW_BACKUP` is off.
- `chess.app.ResourcesTest` (4 tests):
  - the 9 icons inflate as `VectorDrawable`
  - every §4.10 key exists
  - `app_name`, the `·` and `…` characters and a format string
  - the `result_meta` plural for 1 and 42

### Commands run
- `./gradlew :engine:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` → **BUILD SUCCESSFUL**.
  - `:engine:test`: 1 test, 0 failures. This branch is based on T-1, so the engine still has only `SmokeTest`.
  - `:app:testDebugUnitTest`: 26 tests, 0 failures (6 suites).
  - Lint: 0 errors, 88 warnings:
    - `MissingApplicationIcon` (expected until T-13)
    - `OldTargetApi` (deliberate, design §1.3)
    - 86 `UnusedResources`: the copy deck and icons are added ahead of the screens that use them, and the warnings clear as T-14 to T-26 land.
  - The first gate run failed lint with `UnusedMaterial3ScaffoldPaddingParameter` on the empty Game placeholder. Fixed by applying the padding.
- `apkanalyzer manifest permissions app/build/outputs/apk/debug/app-debug.apk` → `io.github.armendeveloper.armchess.debug.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` only. No `android.permission.INTERNET`.
- `apkanalyzer manifest print` → `allowBackup="false"`, with `dataExtractionRules` and `fullBackupContent` set; `MainActivity` uses the starting theme.
- **Visual check without an emulator.** `chess36` doesn't exist yet (T-2), so a temporary Robolectric test with `@GraphicsMode(NATIVE)` captured Home, Stats, About and Game at 360 × 640 dp, in light and dark. The walnut scheme, headings, button hierarchy and the `arrow_back` vector render correctly. The test was deleted, not committed.

### Deviations from the plan
- **Home "New game" navigates straight to the Game placeholder.** In the design it opens the New game sheet (S2), which arrives with T-18. This makes the Game route reachable from the UI, as the T-12 test list asks.
- **No dynamic-colour parameter.** The plan says `dynamicColor = false`; the theme simply has no dynamic path. Adding one later is a few lines in `Theme.kt`.
- **Extra string `action_back`** ("Back"), as above.
- **`fullBackupContent`** was added alongside `dataExtractionRules`; it wasn't in the plan.
- **Debug `applicationIdSuffix = ".debug"`** was added (design §15.1, T-1 follow-up). Anything that hard-codes the debug package name, such as T-2's harness, must use `io.github.armendeveloper.armchess.debug`.
- **No launcher icon and no `android:icon`** in the manifest; T-13 adds both. The splash uses the default icon, which becomes the launcher icon once T-13 lands. Home's 72 dp app mark also waits for that art.
- **About shows `Version 1.0.0`** (versionName only). The design's "(1)" version code and the real Licences content are T-26.

### Follow-ups
- T-13: add `android:icon` and `android:roundIcon` to the manifest. Optionally set `windowSplashScreenAnimatedIcon`, if the splash needs a different drawable from the launcher icon.
- T-17: the real splash keep condition, plus Home's card, skeleton and corrupt-save banner.
- T-18: the New game sheet replaces the direct Home → Game navigation.
- T-14: board geometry tokens (§6.2) next to `BoardColors`.
- T-26: the "Open-source licences" `ListItem` sits 16 dp further in than the column text (ListItem's own padding). Align it when building the real About screen.

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

## 2026-10-14 — T-2: Emulator harness and first instrumented test

**Stories:** enabler (US-2 process death, US-6 timing, release smoke). Unblocks every **[emulator]** task and T-11.

### What changed and why
- **AVD `chess36`** created from the already-installed `system-images;android-36.1;google_apis_playstore;x86_64` (decision H-2; nothing was installed with `sdkmanager`). Its own `config.ini` now has `hw.lcd.width=720`, `hw.lcd.height=1280`, `hw.lcd.density=320` (360 × 640 dp) and `hw.ramSize=2048`. `grainlab_test` was not touched.
  - `avdmanager.bat create avd -n chess36 -k "system-images;android-36.1;google_apis_playstore;x86_64"` worked as-is from Git Bash. The quoted `;` path reaches the `.bat` as one argument, so the `/` fallback was not needed.
  - `PlayStore.enabled` is set to `yes`. `avdmanager` writes `no` even for a Play image, and the plan picked the Play image so TalkBack can be installed later.
- **Acceleration:** `emulator -accel-check` → `WHPX(10.0.26300) is installed and usable`. The boot log confirms "Windows Hypervisor Platform accelerator is operational". Emulator 37.2.12.0.
- **`app/build.gradle.kts`:** `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`, plus these `androidTestImplementation` dependencies, all from the catalog:
  - `ext-junit`
  - `runner`
  - `espresso-core` (see deviations)
  - `uiautomator`
  - the Compose BOM and `ui-test-junit4`

  `ui-test-manifest` was already `debugImplementation`.
- **`LaunchSmokeTest`** (`app/src/androidTest/kotlin/chess/app/`), using `v2.createAndroidComposeRule<MainActivity>()`:
  - `debugBuildIsUnderTest`: the target package is `io.github.armendeveloper.armchess.debug`, the T-12 suffix.
  - `activityLaunchesAndShowsAppName`: the activity is `RESUMED` past the splash, and "Arm Chess" is displayed.
- **`scripts/emulator.md`:** the verified commands for this machine. It covers the prerequisite and acceleration checks, creating and configuring the AVD, booting headless and windowed, waiting for `sys.boot_completed`, running the connected tests (all, or one class), shutting down, PowerShell notes, and the Espresso known issue.

### Files touched
`app/build.gradle.kts`, `gradle/libs.versions.toml` (one version and one library: `espresso`), `app/src/androidTest/kotlin/chess/app/LaunchSmokeTest.kt` (new), `scripts/emulator.md` (new), and this log. Outside the repo: `~/.android/avd/chess36.ini` and `chess36.avd/` (new). Ignored: `local.properties`.

### Tests added (written first, seen failing)
- **Red, compile:** `:app:compileDebugAndroidTestKotlin` failed with `Unresolved reference 'junit4'`, `'test'`, `'createAndroidComposeRule'` and others, because there were no androidTest dependencies yet.
- **Red, on device:** with the dependencies added, both tests failed on `chess36` with `NoSuchMethodException: android.hardware.input.InputManager.getInstance` from `Espresso.onIdle`. Fixed by the Espresso pin.
- **Green:** 2 of 2 passed on `chess36`.
- **Mutation check:** changing the expected text to "Arm Chesz" failed `activityLaunchesAndShowsAppName` on the device ("... contains 'Arm Chesz' ... is not displayed!"). Reverted.

### Commands run
- `emulator -accel-check` → WHPX usable.
- `emulator -avd chess36 -no-window -no-snapshot-save -no-audio -no-boot-anim` (background), then `adb wait-for-device` and a `getprop sys.boot_completed` loop.
  - **Cold boot to `boot_completed=1`: 66 s.**
  - `wm size` → 720x1280; `wm density` → 320; `ro.build.version.sdk` → 36 (release 16).
- `./gradlew :app:connectedDebugAndroidTest` → **BUILD SUCCESSFUL**, `chess36(AVD) - 16`, 2 tests, 0 failures. Run twice: after the fix, and again as the final run after the gate.
- `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=chess.app.LaunchSmokeTest` → BUILD SUCCESSFUL, 2 tests. This checks the single-class command in the runbook.
- GATE: `./gradlew :engine:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` → **BUILD SUCCESSFUL**.
  - `:engine:test`: 94 tests, 0 failures.
  - `:app:testDebugUnitTest`: 26 tests, 0 failures.
  - Lint: 0 errors, 88 warnings, the same set as T-12: `MissingApplicationIcon`, `OldTargetApi` and 86 `UnusedResources`.
- `adb -s emulator-5554 emu kill` → "OK: killing emulator, bye bye".
  - Afterwards `adb devices` is empty and no `emulator.exe` or `qemu` process remains. The emulator's own `-kill … -sleep 20` watchdog exited by itself.
  - The adb server was already running before this task and was left running.

### Deviations from the plan
- **The plan's steps 1–2 were replaced by the 2026-10-14 decisions.** Nothing was installed, and the image is `android-36.1`, not `android-36`.
- **New catalog entry `espresso = "3.7.0"`** (`androidx-test-espresso-core`), used only by androidTest.
  - Compose `ui-test-junit4` from BOM 2026.09.00 brings in `espresso-core:3.5.0` transitively. That version calls `InputManager.getInstance()`, which API 36.1 removed, so every Compose instrumented test fails in `Espresso.onIdle`.
  - 3.7.0 is the latest stable release and belongs to the same AndroidX Test release as `runner` 1.7.0 and `core` 1.7.0, which are already in the catalog.
  - Design §1.2 could list it.
- **`PlayStore.enabled=yes`** in the AVD config. The plan did not mention it; see above.
- **"Arm Chess" instead of "Chess"** in the smoke test, as the T-1 log asked.
- **The windowed boot command is documented but was not run,** so no window was opened on the desktop. It is the headless command without `-no-window -no-audio -no-boot-anim`.

### Follow-ups
- Tasks marked **[emulator]**, and T-11, can now use `chess36`; see `scripts/emulator.md`.
- Instrumented tests must keep the `espresso-core` pin until the Compose BOM brings in 3.7.0 or later itself. Remove it then.
- T-27 (CI): an API 36.1 image on a Linux runner needs KVM; the `-no-window ... -no-boot-anim` command line from the runbook carries over.

## 2026-10-14 — T-5: Castling, en passant, promotion; the full perft gate

**Stories:** US-4 (QA gate).

### What changed and why
- **`Board.make` / `unmake`** now play every move kind. The T-4 `UnsupportedOperationException` guard is gone.
  - *Castling* (the king's move flagged `CASTLE`): the rook also jumps from its corner to the square the king crossed, and back on unmake (`moveCastlingRook`).
  - *En passant*: the captured pawn is taken from one rank behind the target square, and unmake puts it back there (the target square is emptied). It is stored in the same undo slot as any captured piece.
  - *Promotion*: the promoted piece goes on the target square; unmake puts a pawn of the mover's colour back on the from-square.
  - *Castling rights*: a 64-entry `CASTLING_MASK` is ANDed for the from- and to-squares of every move. This clears both rights when the king leaves e1/e8, one right when a rook leaves a1/h1/a8/h8, and the opponent's right when a rook is captured on its home square. A rook that comes home does not regain the right. The undo stack already saved the rights, so unmake needs nothing new.
- **`MoveGen`** generates every move kind.
  - *Promotions*: a pawn on its seventh rank produces four moves (Q, R, B, N, queen first) for each push and each capture, the captures flagged `CAPTURE`. Last-rank moves without a promotion are never generated.
  - *En passant*: a diagonal pawn step onto the empty en passant square, flagged `EN_PASSANT or CAPTURE`. The square always belongs to the side to move, so no colour check is needed. Pins, including the rank pin where both pawns leave the king's rank, are left to the existing make/test/unmake legality filter, which handles them with no special case.
  - *Castling* (`addCastling`, after the king's steps): the right is set, the squares between king and rook are empty (b1/b8 included for queenside), the king is not in check, and the square it crosses (f1/d1/f8/d8) is not attacked. A set right means the king and rook are on their home squares, because FEN validation (T-3) and `make` keep it that way.
- **Perft gate.** `PerftTest` runs the full gate column of design §4.2 for all 7 positions, one test per position. `PerftDeepTest` holds the release column and is tagged with the JUnit 4 category `chess.engine.PerftDeep`. In `engine/build.gradle.kts`, the default `test` task excludes that category, and a new `perftDeep` `Test` task (group `verification`) runs only it. That task is never up to date and never taken from the build cache, because it is both a release check and a timing record.
- All `// T-5:` markers from T-4 are resolved and removed. The engine has no user-visible text, and none was added.

### Files touched
`engine/src/main/kotlin/chess/engine/board/{Board,MoveGen}.kt`, `engine/build.gradle.kts`, `engine/src/test/kotlin/chess/engine/PerftDeep.kt` (new, category marker), `engine/src/test/kotlin/chess/engine/perft/{PerftDeepTest,PerftPositions}.kt` (new), `engine/src/test/kotlin/chess/engine/perft/PerftTest.kt`, `engine/src/test/kotlin/chess/engine/board/{MoveGenTest,MakeUnmakeTest}.kt`, `docs/product/chess-vs-computer/04-plan.md` (Results row), this log.

### Tests added (written first, seen failing)
Red: with the tests written and the T-4 code unchanged, `./gradlew :engine:test` ran 127 tests and **32 failed** (every castling, en passant and promotion test, and the perft counts that need them). Position 6 and a few "not generated" checks already passed. Green after the implementation, with no test changes. The default `test` now has **127 tests** (T-4: 94; 36 added, 3 removed or replaced, net +33), plus 7 in `perftDeep`.
- `PerftTest` (13): the gate column, one test per position: initial 0–5 (… 4,865,609), Kiwipete 1–4, position 3 1–5, position 4 1–4, position 4 mirrored 1–4, position 5 1–4 and position 6 1–4. Also: perft leaves all 7 boards unchanged; `divide` lists `e1g1`/`e1c1` in Kiwipete and the 6 root moves of position 4 and its mirror (sum 264). The T-4 tests for divide, depth 1, FEN overloads and invalid depths are kept. `initialPositionDepth0To4` and `position3Depth1To2` became the full-depth tests.
- `PerftDeepTest` (7, `perftDeep` only): initial 6 = 119,060,324; Kiwipete 5 = 193,690,690; position 3 6 = 11,030,083; position 4 and mirrored 5 = 15,833,292; position 5 5 = 89,941,194; position 6 5 = 164,075,551.
- `MoveGenTest` (+16):
  - *Castling*: both sides and both wings, flagged `CASTLE`. One test for each blocked reason: no right; a piece on f1, g1, d1, c1 or b1 (own or enemy); king in check; transit square f1, d1 or f8 attacked; destination g1 or c1 attacked. Queenside is still allowed when only b1 is attacked, and castling is allowed when the rook is attacked.
  - *En passant*: flags, no capture without a target square, two capturers, Black's captures. The rank pin is illegal in both position 3 lines: 1.Rb1 c5 bxc6 (pseudo-legal but filtered) and 1.e4 fxe3. Capturing the checking pawn is legal. A diagonally pinned pawn may capture en passant only along the pin.
  - *Promotion*: four promotions for a white push and a black push, each a plain move (no flags). Four capture-promotions when the push square is blocked, each flagged `CAPTURE`. No pseudo-legal pawn move reaches the last rank without promoting.
- `MakeUnmakeTest` (+12, −1): `castlingEnPassantAndPromotionAreRejectedUntilT5` is deleted because its behaviour is gone.
  - Each new test plays the move, compares `Fen.format` with the expected FEN, then unmakes and compares the whole `Board`. This covers all four castlings (rook placement, rights, clocks), the king square after castling, en passant for both colours, all four promotions and a black under-promotion, and capture-promotions.
  - Castling rights: a king move clears both; a rook move from a1, h1 or h8 clears one wing; capturing a rook on a8 or h8 clears the opponent's right; a rook coming home does not regain it; a rook moving off a non-home square leaves the rights alone.
  - En passant is only available on the move right after the double push.
  - Depth-3 make/unmake round trips run through four positions full of special moves.
  - The existing depth-2 round trip over the 7 perft FENs now also exercises special moves. It reads them from the new shared `PerftPositions`.

### Perft results and timing
- Every count in the gate and deep columns of design §4.2 matches the Chessprogramming wiki. Start position depth 5 is now **4,865,609**; T-4 got 4,865,351, short by exactly the 258 en passant captures.
- Gate timing: `./gradlew :engine:test --tests "chess.engine.perft.*" --rerun` took **0.94 s** for the `PerftTest` class in JUnit (about 17.0 M leaf nodes, cold JVM) and **8.6 s** wall clock including Gradle.
- Per-position times in the full `:engine:test` run (warm JVM): initial 0–5 0.20 s, Kiwipete 0.16 s, position 3 0.04 s, position 4 0.02 s, mirrored 0.02 s, position 5 0.12 s, position 6 0.14 s. Class total 0.72 s.
- `perftDeep` (about 609 M leaf nodes):

  | Test | Run 1 | Run 2 |
  |---|---|---|
  | position 5, depth 5 | 17.4 s | 4.2 s |
  | Kiwipete, depth 5 | 14.0 s | 7.7 s |
  | position 6, depth 5 | 9.5 s | 6.4 s |
  | initial, depth 6 | 6.1 s | 4.9 s |
  | position 4 mirrored, depth 5 | 0.75 s | 0.65 s |
  | position 3, depth 6 | 0.70 s | 0.64 s |
  | position 4, depth 5 | 0.72 s | 0.65 s |
  | **Total in JUnit** | **49.2 s** | **25.2 s** |
  | Wall clock | 72 s | 27 s |

  Run 1 shared the machine with a parallel build. Run 2 is about 24 M leaf nodes/s with bulk counting. The time is recorded in the plan's Results table.

### Commands run
- Baseline before changes: `./gradlew :engine:test` → BUILD SUCCESSFUL, 94 tests.
- Red: `./gradlew :engine:test` → FAILED, 127 tests, 32 failed (`PerftDeepTest` correctly excluded).
- `./gradlew :engine:test` → BUILD SUCCESSFUL, 127 tests, 0 failures.
- `./gradlew :engine:test --tests "chess.engine.perft.*" --rerun` → BUILD SUCCESSFUL, 13 tests (timing above).
- `./gradlew :engine:perftDeep` → BUILD SUCCESSFUL, 7 tests, 0 failures (run 1).
- `./gradlew :engine:perftDeep --warning-mode all` → BUILD SUCCESSFUL, 7 tests (run 2). The new task adds no deprecation warnings.
- GATE: `./gradlew --rerun-tasks :engine:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` → **BUILD SUCCESSFUL** in 57 s, 61 tasks executed.
  - The first attempt failed at configuration with "The filename, directory name, or volume label syntax is incorrect". The cause was this worktree's new `local.properties`, written with unescaped backslashes. It was fixed to `sdk.dir=C\:\\Users\\...` and the gate was rerun; no code change was needed.
  - `:engine:test`: 127 tests, 0 failures, 0 skipped.
  - `:app:testDebugUnitTest`: 26 tests, 0 failures.
  - Lint: 0 errors, 88 warnings. These are 86 `UnusedResources` (the copy deck added ahead of its screens, known since T-12), `OldTargetApi` and `MissingApplicationIcon`, all from `:app`, which T-5 does not touch.
  - `app-debug.apk` built. No Kotlin compiler warnings.

### Deviations from the plan
- **Castling destination check.** Pseudo-legal generation checks rights, empty squares, "not in check" and the crossed square. Whether the destination is attacked is left to the legality filter, as for every other king move. Legal move lists therefore match design §4.2 exactly, but `generatePseudoLegal` may include a castling move into check, just as it includes other king moves into check.
- **En passant square.** It is still set after every double push (T-4 behaviour, standard FEN), not only when a capture is possible. Perft does not depend on this. The T-6 Zobrist key should hash the en passant file only when a capture is possible, so that transpositions match.
- **Extra test files.** `PerftPositions.kt` (shared FENs) and `PerftDeepTest.kt` are not in the plan's file list. The category marker is in the test source set as `chess.engine.PerftDeep`, the name design §4.7 uses.
- **No Zobrist round-trip check.** Design §4.2 mentions the Zobrist key in round-trip tests, but that key arrives in T-6.

### Follow-ups
- T-6: hash the en passant file only when a pawn of the side to move can capture. Extend the make/unmake round-trip tests to compare the Zobrist key.
- T-6: reject a FEN where the side not to move is in check (carried over from T-4).
- `perftDeep` takes about 25–50 s here. Run it before each release (T-27/T-28 checklists).
