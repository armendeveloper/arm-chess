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
## 2026-10-14 — T-13: Piece artwork and licence record

**Stories:** US-3 (pieces), PRD artwork dependency, design 02 §6.3.

### What changed and why
- **Licence check, all 12 files** (the designer had checked 2). Used the Commons API to read the wikitext, description-page history and upload history of every `File:Chess_{k,q,r,b,n,p}{l,d}t45.svg` page.
  - All 12 offer `{{self|GFDL|migration=relicense|BSD|GPL}}`, with `author={{U|Cburnett}}`.
  - On every page, Cburnett **himself** added BSD (2007-01-12, "Dual license with BSD") and GPL (2007-12-12).
  - One IP vandal edit to the licence block of `Chess_rdt45.svg` (2023) was reverted, and the page is clean now.
  - So the Cburnett set stays. No fallback was needed.
- **Revision choice:** every file has been re-uploaded by other Commons users since 2010 (cleanups, symmetry fixes). We converted **Cburnett's own last revision (2007-01-03)** of each file, so all the artwork is the BSD grantor's own work. We rendered both versions on light and dark squares at several sizes and compared them. The differences are cosmetic. SHA-1s and URLs for both are in `docs/licences/pieces-cburnett.md`.
- **Conversion:**
  - Downloaded into a fresh temp directory outside the repo; checked each file against its Commons SHA-1; scanned for active content (none).
  - Converted with `Svg2Vector` from `com.android.tools:sdk-common:32.4.1`, the converter behind Android Studio's Vector Asset tool, run headless. No warnings.
  - The only manual change to each output file is a provenance and licence header comment.
- **Visual check:** a throwaway Robolectric native-graphics test rendered all 12 drawables at 24, 72, 96 and 288 px on `#F0D9B5` and `#8C6A4E`. They look right; the black pieces' white details survive at 96 px. The test was deleted, not committed.
- **Launcher icon:** an adaptive icon in `mipmap-anydpi/`.
  - Foreground: the black knight, scaled 1.4× inside the 66 dp safe zone.
  - Background: `@color/ic_launcher_background` #FFDCC3, the light `primaryContainer` that T-12's `Color.kt` uses for seed #6B4E37.
  - Monochrome: a knight silhouette with the eye, nostril and mane as even-odd holes, for themed icons.
  - Manifest: added only `android:icon` and `android:roundIcon`.
- `docs/licences/pieces-cburnett.md` records:
  - per file: URL, author, licence options, who added BSD and when, the revision used and its SHA-1, plus the current revision;
  - the check date;
  - the full BSD-3-Clause notice for S7b and the S7 attribution line.

### Files touched
`app/src/main/res/drawable/piece_{w,b}{k,q,r,b,n,p}.xml` (12), `app/src/main/res/drawable/ic_launcher_{foreground,monochrome}.xml`, `app/src/main/res/mipmap-anydpi/ic_launcher{,_round}.xml`, `app/src/main/res/values/ic_launcher_background.xml`, `app/src/main/AndroidManifest.xml` (icon attributes only), `app/src/test/kotlin/chess/app/PieceArtResourcesTest.kt`, `docs/licences/pieces-cburnett.md`, this log.

### Tests added (written first, seen failing)
`chess.app.PieceArtResourcesTest` (Robolectric):
- `allTwelvePieceDrawablesInflateAsVectorDrawablesWithNonZeroSize`: each `R.drawable.piece_*` resolves to its name, inflates as `VectorDrawable`, and has a non-zero, square intrinsic size.
- `launcherIconsAreAdaptiveWithForegroundBackgroundAndMonochromeLayers`: `ic_launcher` and `ic_launcher_round` are `AdaptiveIconDrawable`s with a background, a vector foreground and a vector monochrome layer.
- `manifestDeclaresLauncherIcon`: `ApplicationInfo.icon == R.mipmap.ic_launcher`.

Red: compile errors (`Unresolved reference 'drawable'`) before the resources existed.

### Commands run
- `./gradlew :engine:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` before any change: BUILD SUCCESSFUL (baseline).
- GATE after the change (`./gradlew clean` first): **BUILD SUCCESSFUL**.
  - `:engine:test`: 1 test, 0 failures.
  - `:app:testDebugUnitTest`: 4 tests, 0 failures (`MainActivityTest` 1, `PieceArtResourcesTest` 3).
  - Lint: 0 errors, 1 warning (`OldTargetApi`, expected). `MissingApplicationIcon` is gone.
  - An earlier run flagged `ObsoleteSdkInt` for `mipmap-anydpi-v26` (minSdk is 26), so the icons moved to `mipmap-anydpi`.
- `aapt2 dump badging app-debug.apk` → `application: label='Arm Chess' icon='res/mipmap-anydpi-v21/ic_launcher.xml'`. aapt2 versions the folder itself.
- **Build-cache gotcha:** after renaming `mipmap-anydpi-v26/` to `mipmap-anydpi/`, `:app:mergeDebugResources` was restored FROM-CACHE from the old layout. `processDebugResources` then failed with `resource mipmap/ic_launcher not found`, even after `clean`. The cache key apparently doesn't include the resource folder name. Fixed by re-running `:app:mergeDebugResources :app:packageDebugResources --rerun-tasks` once, which re-stores the entry. `--no-build-cache` also passes. Only a local build cache that holds the intermediate `-v26` layout from this session is affected.

### Deviations from the plan
- **Revision:** the author's own 2007 revisions rather than the current Commons revisions (reason above).
- **Conversion tool:** the Vector Asset converter ran headless (`Svg2Vector`), not through Android Studio's UI. It is the same code.
- **Icon folder:** `mipmap-anydpi/` rather than `mipmap-anydpi-v26/` (lint `ObsoleteSdkInt`; minSdk is 26).
- **Not committed:** the helper scripts (download, SHA-1 check, conversion, render). The licence record has the URLs and SHA-1s, so the inputs can be checked again.
- **Manual step still open:** the plan asks for a PR screenshot of the 12 pieces at 96 dp on light and dark squares. It was rendered locally (Robolectric) but is not in the repo.

### Follow-ups
- T-26: S7 shows `about_pieces`, and S7b shows the BSD notice from `docs/licences/pieces-cburnett.md` (the binary must carry the full text: clause 2).
- T-12 / merge: keep `ic_launcher_background` (#FFDCC3) in sync with `PrimaryContainerLight` if the theme colours change.
- T-14: `PieceArt` maps pieces to `R.drawable.piece_{w,b}{k,q,r,b,n,p}`. Each drawable is 45 dp intrinsic; scale it to the square.


## 2026-10-14 — T-6: Zobrist keys, immutable Position/Game, game status and all draw types

**Stories:** US-5 (all end conditions), US-2 (replay for the save loader), US-10 (undo).

### What changed and why
- **Zobrist keys** (`board/Zobrist.kt`, new). One random 64-bit number per (piece, square), one for Black to move, one per castling-rights combination (the XOR of four per-right keys) and one per en passant file. They come from SplitMix64 with a fixed seed ("ARMCHESS"), so keys are the same on every run and device. `Zobrist.compute(board)` builds a key from scratch. Clocks are not hashed.
- **`Board`** keeps `key` up to date in `make`/`unmake`:
  - `make` XORs out the mover and any captured piece, XORs in the placed piece (the promoted piece for a promotion), and moves the castling rook's key. It swaps the old castling-rights key for the new one, flips the side key, and takes out the previous en passant file if one was hashed.
  - `unmake` restores the key from the undo stack, which grew from 4 to 5 ints per ply (the hashed en passant file), plus a parallel `LongArray` for the key.
  - **En passant (build-log follow-up from T-5).** The board still records the en passant square after every double push, as FEN does. The key includes the en passant file only when `Board.hasLegalEnPassant()` is true. That function finds a pawn beside the pawn that just double-pushed, plays the capture on the cells, tests its own king and restores the cells. So the rank-pin case (position 3, 1.e4 fxe3) is excluded, and a capture that removes a checking pawn counts. This is US-5's "same en passant capture availability". The test runs only after a double push, so the cost in `make` is small.
  - `Board(undoPlies)` and `copy(undoPlies)` take the initial undo depth. `Position` snapshots use 1, so a stored position does not carry a 256-ply undo stack (about 7 KB).
  - `equals`/`hashCode` now include the key, so every existing round-trip check also checks the key. `resetKey()` recomputes it after direct field writes; `Fen.parse` calls it. Other new members are `samePlacementAs` and `hasHashedEnPassant`.
- **`Position`** (`rules/Position.kt`, public, immutable). It wraps a private `Board` that is never changed after construction; move generation runs on a copy, so a position can be read from several threads.
  - `fromFen`/`toFen`, `START`/`START_FEN`, `sideToMove`, `castlingRights`, `enPassantSquare`, the clocks, `pieceAt`, `kingSquare`, `isCheck`, `legalMoves()` (lazy, cached, read-only), `isLegal`, `play(move)` (throws `IllegalMoveException`), `repetitionKey` and `samePositionAs`.
  - **`fromFen` rejects a FEN where the side not to move is in check** (follow-up from T-3 and T-4), with a `FenException`. `Fen.parse` itself still accepts such FENs, for perft and board tests.
  - `samePositionAs` compares placement, side to move, castling rights and the en passant square only when a legal capture exists. `repetitionKey` is the board's Zobrist key, used as a pre-filter. A hash collision can never end a game (design §4.3).
  - `equals` is stricter than `samePositionAs`: every FEN field, clocks included.
  - `internal toBoard()` gives the search (T-8) a mutable copy.
- **`IllegalMoveException : IllegalArgumentException`** (public, in `Position.kt`).
- **`GameStatus`** (`rules/GameStatus.kt`): a sealed interface. Its members are `Ongoing`, `Checkmate(winner: PieceColor)`, and the sealed sub-interface `Draw` with `Stalemate`, `InsufficientMaterial`, `ThreefoldRepetition` and `FiftyMoveRule`, plus `isOver`.
  - These are reasons, not text: no user-visible English in the engine (scope change: hy/ru). The app maps them to strings in T-16.
  - `GameStatus.of(positions)` (internal) evaluates in design §4.3 order: checkmate, stalemate, insufficient material, threefold, 50-move.
- **`Draws`** (`rules/Draws.kt`, internal):
  - `isInsufficientMaterial` covers exactly the PRD's four cases, with an overload on `Board` for the search.
  - `isThreefoldRepetition` scans back only as far as the halfmove clock and only same-side-to-move positions. It counts `samePositionAs` matches, and the occurrences need not be consecutive.
  - `isFiftyMoveRule` is `halfmoveClock >= 100`.
  - Both draws are automatic (decision OQ-3).
- **`Game`** (`rules/Game.kt`, public, immutable):
  - `positions` (read-only, moves + 1 entries), `moves`, `startPosition`, `position` (current), `status` (lazy).
  - `play(move)` throws `IllegalMoveException` for an illegal move and for any move once the game is over.
  - `undo(plies = 1)` takes 0..moves; the status is recomputed.
  - `legalMovesFrom(sq)` is empty once the game is over.
  - `equals`/`hashCode` compare positions and moves.
  - `Game.start(position = START)` and `Game.replay(startFen, ucis)`. `replay` throws `FenException` or `IllegalMoveException` (malformed, illegal, or after the end). It builds its lists in one pass, so replay is linear.

### Files touched
`engine/src/main/kotlin/chess/engine/board/{Board,Fen}.kt`, `engine/src/main/kotlin/chess/engine/board/Zobrist.kt` (new), `engine/src/main/kotlin/chess/engine/rules/{Position,Game,GameStatus,Draws}.kt` (new), `engine/src/test/kotlin/chess/engine/board/MakeUnmakeTest.kt`, `engine/src/test/kotlin/chess/engine/board/ZobristTest.kt` (new), `engine/src/test/kotlin/chess/engine/rules/{PositionTest,GameStatusTest,RepetitionTest,GameTest,RulesTestSupport}.kt` (new), this log. `app/` is not touched.

### Tests added (written first, seen failing)
Red: with the tests written and no implementation, `:engine:compileTestKotlin` failed with 458 errors (no `Zobrist`, `Board.key`, `Position`, `Game`, `GameStatus`). Green on the first full run after the implementation. All suites passed at once, so I checked that the tests can fail with temporary mutations, each reverted:
- Hashing the raw FEN en passant square: 5 failures.
- Treating a pseudo-legal en passant capture as legal: 2 failures (the rank-pin tests).
- Dropping castling rights from the key and `samePositionAs`, and checking the 50-move rule before mate: 8 failures.
- Dropping the castling rook from the incremental key, ignoring bishop square colour, and limiting the repetition window to 4 plies: 13 failures.

`:engine:test` now runs **198 tests** (T-5: 127; +71).
- `ZobristTest` (8):
  - The key is deterministic, and the parsed key equals the from-scratch key for all 7 perft FENs. The empty board's key is 0.
  - Ten variations of side, placement and each castling right all get different keys. Clocks are ignored.
  - En passant: 1.e4 with no capturer leaves the file out; a capturer on d4 puts it in; the position 3 rank pin leaves it out; a capture that removes the checking pawn (1.d4+ exd3) puts it in.
  - **The incremental key equals the from-scratch key after every move of 3 random 200-ply games (seeds 1–3), and after every unmake back to the start.**
- `MakeUnmakeTest` (no new tests; the helper is stronger). `checkRoundTrips` now asserts incremental key == `Zobrist.compute` after every make and every unmake, and that unmake restores the key. That covers the depth-2 round trip through the 7 perft positions and the depth-3 one through the special-move positions. `Board.equals` now includes the key too.
- `PositionTest` (15):
  - The start position and its fields; `fromFen`/`toFen` round trip for the perft FENs; malformed FENs rejected.
  - **The side not to move in check is rejected for both colours**; the same placements are accepted with the checked side to move.
  - `play` returns a new position and leaves the original unchanged. It rejects an illegal move, a move without the generator's flags, and a pinned piece leaving its line. `isLegal`. `legalMoves()` is cached and read-only. `isCheck`. Equality covers every FEN field.
  - `samePositionAs`/`repetitionKey`: clocks are ignored; side, castling and placement count; an ep square with no capturer, or only a pseudo-legal one, makes no difference; a legal ep capture does.
- `GameStatusTest` (22):
  - Checkmate: fool's mate (Black wins), back-rank mate (White wins), mate given in the start FEN, and a back-rank check with an escape (not mate).
  - Stalemate: Q v K, K+P v K, and one reached by a move.
  - Insufficient material: K v K; K+B v K and K+N v K for both colours; K+B v K+B with same-coloured bishops on dark (c1/f8) and light (d3/h1) squares.
  - **Not insufficient material:** opposite-coloured bishops, K+N+N v K, K+P v K, two same-coloured bishops on one side, B v N, N v N, a rook, a queen, and same-coloured bishops plus a pawn. A capture that leaves K+B v K ends the game.
  - **50-move rule:** ongoing at 98 and 99 halfmoves, a draw at exactly 100; a start FEN at 100 is drawn; a capture or pawn move on the 100th halfmove resets the clock.
  - Precedence: **mate on the 100th halfmove wins**; stalemate on the 100th is stalemate; insufficient material comes before the 50-move rule. `isOver` and `Draw` membership are checked for every status.
- `RepetitionTest` (9). Each sequence varies its squares per cycle, so only the position under test can reach three occurrences:
  - The knight shuffle draws on the **third, non-consecutive** occurrence (plies 0, 4, 8). Twofold is not a draw. A third occurrence with Black to move (plies 1, 5, 9) counts.
  - **No draw when only castling rights differ**: king walks, then a rook move that loses one right. The placement recurs at ply 8 without a draw; the draw comes at ply 12.
  - **A raw FEN ep square with no legal capture repeats with later occurrences** (1.e4, draw at ply 9). **A legal ep capture makes the first occurrence different** (1.e4 h6 2.e5 d5: no draw at ply 12, draw at 16).
  - Repetition in a game started from a FEN. Repetition before the 50-move rule when both happen on the same ply.
- `GameTest` (17):
  - New game; `play` appends and leaves the original unchanged; `play` rejects illegal moves and any move after the game ended.
  - `legalMovesFrom`: own piece, empty square, opponent's piece, a piece with no moves, and empty after the end.
  - **`undo(2)` after fool's mate restores the position, moves, positions and an ongoing status.** Undo after a threefold makes the game ongoing again. `undo()` defaults to 1, `undo(0)` returns the same game, and out-of-range values throw.
  - `replay` matches `play`, and resolves castling, en passant and under-promotion from UCI. **It rejects an illegal move** (with the move in the message), malformed or empty UCI, a missing or extra promotion letter, moves after mate, and an invalid or side-not-to-move-in-check FEN.
  - Game equality; the lists are read-only.
  - **Replaying 2,000 plies takes < 50 ms.** The test builds a 2,000-ply game that never ends (seeded random moves that keep the status ongoing, pawn pushes when the clock reaches 90), then replays it 5 times and logs the times. Measured best: **7.0 ms** (runs 11.2 / 8.6 / 9.0 / 7.0 / 7.8 ms) in a plain `:engine:test`, and **9.2 ms** in the GATE run.

### Commands run
- Baseline: `./gradlew :engine:test` → BUILD SUCCESSFUL, 127 tests.
- Red: `./gradlew :engine:test` → compilation FAILED (458 unresolved references), as expected.
- `./gradlew :engine:test` → BUILD SUCCESSFUL, 198 tests, 0 failures.
- Mutation runs (`./gradlew :engine:test`, with and without `--tests "chess.engine.rules.*" --tests "chess.engine.board.ZobristTest"`) → FAILED as listed above. Each mutation was reverted, and `grep -rn MUTATION engine/` finds nothing.
- GATE: `./gradlew --rerun-tasks :engine:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` → **BUILD SUCCESSFUL** in 1 min 27 s, 61 tasks executed.
  - `:engine:test`: 198 tests, 0 failures, 0 skipped. `PerftTest` took 0.86 s.
  - `:app:testDebugUnitTest`: 26 tests, 0 failures.
  - Lint: 0 errors, 88 warnings, the same set as T-5 (`UnusedResources`, `OldTargetApi`, `MissingApplicationIcon`, all in `:app`).
  - `app-debug.apk` built. No Kotlin compiler warnings.
- `./gradlew :engine:perftDeep` → BUILD SUCCESSFUL, 7 tests, 0 failures. **31.3 s** in JUnit (T-5 run 2: 25.2 s), 43 s wall clock. Per test: 10.4 / 8.2 / 5.9 / 4.3 / 0.9 / 0.8 / 0.8 s. The extra time is the key update in `make` plus machine variance; the counts are unchanged.

### Deviations from the plan
- **`IllegalMoveException`** lives in `Position.kt`, and the test helpers in `RulesTestSupport.kt`; the plan lists neither.
- **`Game.play` after the end** throws `IllegalMoveException`, not `IllegalStateException`, so that `replay` and the app have one exception to handle. Once a game has ended, no move is legal in it.
- **`legalMoves()` is a function** (design §4.1); `isCheck` and `repetitionKey` are properties.
- **UCI resolution in `replay`** matches `move.uci()` against the legal moves. `// TODO(T-7)` marks where `Uci.parse` will take over.
- **`Fen.parse` still accepts** a FEN where the side not to move is in check. Only the public `Position.fromFen` rejects it, so internal perft and board tests that use such FENs keep working.
- **`Draws.isInsufficientMaterial(board: Board)`** is added for T-8's in-search draw detection. It is not used yet.

### Follow-ups
- T-7: replace the UCI matching in `Game.replay` with `Uci.parse` (the `TODO(T-7)` marker).
- T-8: build the search board from `Position.toBoard()`. Use `Board.key` (which already includes the legal-ep rule) for the TT and in-search repetition. Use the history's `repetitionKey`s back to the last irreversible move, and `Draws.isInsufficientMaterial(board)`.
- T-16: map `GameStatus` (`Checkmate(winner)`, the four `Draw` reasons) to localized strings. The engine provides no text.
- `perftDeep` now takes about 31 s here; still run it before each release.

## 2026-10-14 — T-14: ChessBoard static render and 64 semantics nodes

**Stories:** US-3 (cues, coordinates), US-8 (square descriptions), US-9 (square size). Design 02 §4.3.3, §5.3, §5.6–5.9, §6.2; tech design §6.3, §7, §8.1.

### What changed and why
- **UI model, no engine types** (`ui/game/GameUiState.kt`, the board part of tech design §6.3). The engine's public API is changing in T-6, which runs in parallel, so the board has its own small model that T-15's `GameUiMapper` fills from `Position`:
  - `Side` (White/Black), `PieceKind`, `PieceUi(side, kind)`.
  - `TargetKind` with six values, each with `isCapture`: `Move`, `Castling`, `Promotion` (dots) and `Capture`, `EnPassant`, `CapturePromotion` (rings). Each value maps to exactly one §5.3 target phrase.
  - `BoardUi(pieces: List<PieceUi?> /*64, a1 = 0*/, player, selected?, targets: Map<Int, TargetKind>, lastMove: LastMoveUi?, checkSquare?, interactive)`. `init` validates 64 squares and every cue square. `cell(sq)` / `cells` derive `CellUi(square, piece, selected, inCheck, target, lastMove: LastMoveRole?)`. `isOwnPiece(sq)` uses `player`.
  - `SquareActionUi(to, kind, captured?)`: the input for custom-action labels that T-24 puts on squares.
  - `Squares`: `of`, `file`, `rank`, `name`, `parse`, `fileName`, `rankName`, with the engine's numbering (a1 = 0, h8 = 63).
  - The tech design's `whiteAtBottom` is replaced by `player`: the player is always at the bottom, and the same field decides which pieces are "own" for click labels.
- **`ChessBoard(board, onSquareClick: (Int) -> Unit, modifier)`** (`ui/game/board/ChessBoard.kt`).
  - A custom `Layout` takes the largest square that fits, rounded down to whole pixels per square, and centres it.
  - One `drawBehind` runs the drawing plan. One `pointerInput` (`detectTapGestures`) maps the tap to a square with `squareAtOffset`. It reports the square only while `interactive`; the ViewModel decides what a tap means (T-15). T-25 replaces the detector with the 8 dp tap-or-drag handler.
  - 64 transparent `Box`es are emitted in reading order and placed on the grid.
  - `onSquareClick` is the seam for T-15 (`{ vm.onAction(SquareTap(it)) }`). The tech design's `onAction: (GameAction)` waits for `GameAction` in T-15.
- **Drawing plan** (`BoardDrawing.kt`). `boardMarks(board)` is a pure list of `BoardMark`s, in the §4.3.3 layer order:
  - `SquareFill(light, lastMove)`
  - `CheckTint`
  - `RankLabel` / `FileLabel`: rank digits top-left on the leftmost visual file, file letters bottom-right on the bottom visual rank; the colour follows the square shade
  - `LastMoveCorner`
  - `PieceMark`
  - `SelectedBorder`
  - `MoveDot` / `CaptureRing`
  - `CheckBadge`

  `DrawScope.drawBoard` only executes the list, so the JVM tests pin what is drawn and where. The orientation helpers are `squareAt`, `visualRow`, `visualCol`, `visualOrder` and `squareAtOffset`.
  - Cue details per §4.3.3 and §6.2. Every ink and check cue has the 1.5 dp white halo:
    - the selected border has its halo inside
    - dots have a halo disc
    - rings have a wider white ring underneath, which gives a halo on both edges
    - the badge is a red disc with a white "+" and a halo
    - the check tint is a radial gradient from 55% to 0% at 70% of the half-square
    - the last-move corner triangle has no halo
  - Coordinates use `TextMeasurer` at `0.24 s` converted with `toSp()`, so they scale with the board and not the font setting (§5.9). They are Roboto Medium.
- **Geometry tokens** (`ui/theme/BoardMetrics.kt`, the T-12 follow-up). `BoardMetrics.of(squarePx, density)` resolves the §6.2 table: dot, ring, strokes, halo, badge, glyph, corner, coordinate size and inset, with the dp minimums.
- **`PieceArt`** (`PieceArt.kt`) is the only place that names `R.drawable.piece_*`. `piecePainters()` gives the 12 `VectorPainter`s, each scaled to the square.
- **Semantics overlay** (`BoardSemantics.kt`).
  - The container has `isTraversalGroup = true` and test tag `chess_board`, and no `collectionInfo` (§5.3).
  - Each square uses `clearAndSetSemantics`: test tag `square_e4`, `contentDescription` from `SquareDescriber`, and `onClick(label)` only when `clickLabel` isn't null. The action calls `onSquareClick(square)`.
  - The nodes have no pointer input.
- **`SquareDescriber`** (`ui/a11y/SquareDescriber.kt`) over a `Strings` interface (`ui/a11y/Strings.kt`; `AndroidStrings(resources)` in production, built from `LocalResources`).
  - `describe(cell)` implements the §5.3 grammar.
  - `clickLabel(cell, player, interactive)` returns **select** for own pieces (even with no legal moves), **deselect** for the selected piece, **move here** or **capture** for targets, and null otherwise or when not interactive.
  - `actionLabel(SquareActionUi)` returns the §5.3 custom-action labels: Move to f3 / Take knight on f6 / Take pawn en passant on d6 / Castle kingside or queenside / Move to e8 and promote / Take rook on f8 and promote.
  - `spokenCoordinate()` is the one place to switch to "A 1" if QA needs it.
- **Localisation (2026-10-14 scope change).** No English is assembled in code. The 43 new `a11y_*` strings have translator comments:
  - `a11y_square` = `%1$s, %2$s` (coordinate, contents)
  - each state is appended with `a11y_square_with_state` = `%1$s, %2$s`, so a translation controls the separator and order
  - piece names are whole phrases per colour and piece (12), so Russian and Armenian can make the adjective agree with the noun
  - each target kind is one phrase ("move available, castling")
  - capture actions are one string per captured piece type ("Take knight on %1$s", ×5, plus ×4 "… and promote"), so Russian can use the accusative case
  - coordinates stay Latin; they are notation
- **Previews** with `BoardFixtures`, an internal FEN piece-placement reader with no rules:
  - the start position as White
  - selected king in check
  - all cues as Black in the dark theme
  - 24 dp squares

### Files touched
- New: `app/src/main/kotlin/chess/app/ui/game/GameUiState.kt`, `app/src/main/kotlin/chess/app/ui/game/board/{ChessBoard,BoardDrawing,BoardSemantics,PieceArt,BoardFixtures}.kt`, `app/src/main/kotlin/chess/app/ui/a11y/{Strings,SquareDescriber}.kt`, `app/src/main/kotlin/chess/app/ui/theme/BoardMetrics.kt`.
- Changed: `app/src/main/res/values/strings.xml` (43 `a11y_*` strings).
- Tests: `app/src/test/kotlin/chess/app/ui/a11y/SquareDescriberTest.kt`, `app/src/test/kotlin/chess/app/ui/game/BoardUiTest.kt`, `app/src/test/kotlin/chess/app/ui/game/board/{BoardDrawingTest,ChessBoardSemanticsTest,ChessBoardRenderTest}.kt`, `app/src/test/kotlin/chess/app/ui/theme/BoardMetricsTest.kt`.
- This log.

### Tests added (written first, seen failing)
**Red:** the first run failed to compile (`Unresolved reference 'CellUi'`, `'SquareDescriber'`, `'AndroidStrings'`, …). On the first green run, one render test failed (the halo sample, explained below), and it was fixed in the test.

**Mutation check (reverted):** three mutations together failed 7 tests:
- not drawing the selected border
- Black orientation computed as White
- forwarding taps when not interactive

- `SquareDescriberTest` (Robolectric, real resources, 26 tests):
  - every §5.3 example verbatim
  - last move from; plain move; capture-promotion
  - the state-word order (selected → in check → target → last move)
  - all 12 piece names
  - click labels per rule, for both sides and for a non-interactive board
  - every custom-action label, plus rejection of a capture without a captured piece
- `BoardUiTest` (JVM, 8):
  - square numbering and parsing, and invalid names
  - the placement reader
  - 64-square and cue-range validation
  - cell derivation; own pieces; `isCapture`
- `BoardDrawingTest` (JVM, 16):
  - reading order for White (a8 … h1) and Black (h1 … a8)
  - row/column round trip; touch → square, including the edges and off-board
  - square shades (a1 dark); 32 pieces on their squares in both orientations
  - coordinates on the a-file and rank 1 for White, the h-file and rank 8 for Black, coloured by square shade
  - each cue type → its mark; no cues without cue state
  - marks in the §4.3.3 layer order
- `BoardMetricsTest` (JVM, 2): the §6.2 fractions on a 45 dp square, and the dp minimums on a 24 dp square at 2×.
- `ChessBoardSemanticsTest` (Robolectric Compose, 11):
  - 64 child nodes of the board in reading order for each orientation, with bounds checked row-major on screen
  - `isTraversalGroup` set; no `CollectionInfo` or `CollectionItemInfo`
  - "e1, white king, selected, in check" from the `kingInCheckSelected` fixture, plus neighbouring squares
  - click labels (deselect, move here, capture, select) and no click action on enemy pieces, empty non-targets or last-move squares
  - not interactive → no click actions at all, descriptions kept
  - an accessibility click reports d1 and e2
  - a touch reports e2/a8/d4 as White and d7/h1 as Black; a touch is ignored when not interactive
  - nodes update when the board state changes
- `ChessBoardRenderTest` (Robolectric `@GraphicsMode(NATIVE)`, xhdpi, 8). It samples pixels well inside each mark, with sample points taken from `BoardMetrics`:
  - square shades
  - a piece in the right cell for White and for Black
  - last-move tint and the bottom-left corner
  - the selected ink border with its white halo inside
  - a dot vs a hollow ring
  - the check badge (red disc, white "+") and the tint
  - coordinates on a1 and h1, and none on d5

  At mdpi the 1.5 px halo covers no whole pixel, so the class renders at xhdpi.

### Commands run
- Baseline GATE before any change: **BUILD SUCCESSFUL** (engine 127, app 29 tests).
- GATE `./gradlew :engine:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` after the change: **BUILD SUCCESSFUL** (1 m 48 s).
  - `:engine:test`: 127 tests, 0 failures.
  - `:app:testDebugUnitTest`: 100 tests, 0 failures, 0 skipped (13 suites; 71 new).
  - Lint: 0 errors, 87 warnings. 86 are `UnusedResources`, all pre-existing copy-deck keys for screens that come later; none is a new `a11y_*` string. The other is `OldTargetApi` (deliberate).
- **Visual check without an emulator.** A throwaway Robolectric native-graphics test rendered the four fixtures at xxhdpi:
  - start as White
  - king in check selected
  - all cues as Black
  - all cues at 24 dp squares

  Orientation, coordinates, tint, corners, halos, rings over pieces and the check badge all match §4.3.3. The test was deleted, not committed.

### Deviations from the plan
- **`ChessBoard(board, onSquareClick, modifier)`** instead of `onAction`: `GameAction` arrives with T-15.
- **`BoardUi.player`** instead of `whiteAtBottom` (same information; see above).
- **Taps on a non-interactive board are dropped in the board.** No semantics click actions exist then either. The ViewModel still guards (T-15 test).
- **Custom actions are not attached to squares yet.** `SquareDescriber.actionLabel` and its strings are done and tested; attaching them is T-24, as the plan's T-24 list says.
- **Extra files:** `BoardFixtures.kt` (previews and tests) and `ui/theme/BoardMetrics.kt` (the geometry tokens the T-12 log deferred to T-14).
- **No PR screenshot committed;** the render tests and the throwaway capture stand in for it.

### Follow-ups
- T-15: map `Position` → `BoardUi`. `TargetKind` needs castling (king's destination), en passant (destination square, ring) and promotion vs capture-promotion. Wire `onSquareClick` to `SquareTap`. Show the board in `GameScreen`; it is not on screen yet.
- T-24: put `SquareDescriber.actionLabel(...)` custom actions and keyboard focus on `squareSemantics`, and draw the focus ring as a new `BoardMark` layer between the cues and the dragged piece.
- T-25: replace `detectTapGestures` with the `BoardInput` tap-or-drag handler. Add drop-target and ghost marks.
- T-29 translator note: the state phrases ("selected", "in check") follow a noun phrase whose gender depends on the piece. Russian may need neutral forms (e.g. "выбрано"), or the describer could pass the piece to gendered state strings.
- QA (T-28): check how TalkBack says "a1". Switch `SquareDescriber.spokenCoordinate` if needed.

## 2026-10-14 — T-7: SAN and UCI notation

**Stories:** US-11 (move list), US-8 (spoken moves), and the save format (ADR-5).

### What changed and why
- **`Uci`** (`rules/Uci.kt`, new, public object):
  - `format(move)` gives `"e2e4"`, `"e7e8q"`. Castling is the king's move (`e1g1`), en passant is the pawn's move to the target square.
  - `parse(position, text)` and `parseOrNull` resolve the text against the legal moves by from, to and promotion, so the castling, en passant, capture and double-push flags come from the move generator.
  - The text is strict: 4 or 5 lowercase characters, with a `q`/`r`/`b`/`n` promotion letter exactly when the move promotes. `parse` throws `IllegalMoveException`.
- **`Game.replay`** now resolves each move with `Uci.parseOrNull` (the `TODO(T-7)` from T-6), with the same error message. This compares three ints instead of building a UCI string for every legal move.
- **`SanLetters`** (`rules/San.kt`, public data class): the five piece letters, which are injectable. `SanLetters.INTERNATIONAL` (K Q R B N) is the default everywhere.
  - T-29 can pass localized letters (for example Russian Кр Ф Л С К) without touching the SAN logic.
  - A letter may be several characters long. The parser tries the longest letters first, so "Кр" wins over "К".
  - Letters must be non-empty, distinct, and free of whitespace and of the characters SAN uses (`a`–`h`, digits, `x = + # - . ! ?`), so the notation cannot become ambiguous.
  - Castling stays `O-O`/`O-O-O` and squares stay `a1`–`h8` whatever the letters.
- **`San`** (`rules/San.kt`, public object):
  - `format(position, move, letters)` gives piece letters, disambiguation (file if that is enough, else rank, else both: `Nbd7`, `R1e2`, `Qh4xe1`), `x`, pawn captures with the from-file (`exd5`; en passant is `exd6` with no suffix), `=Q`, `O-O`/`O-O-O`, and `+` or `#` worked out from the resulting position.
  - Disambiguation looks only at **legal** moves, so a pinned piece never adds a file or rank.
  - `format` throws `IllegalMoveException` for a move that is not legal.
  - The output is notation, not prose: the engine still has no user-visible English.
- **SAN parser** `San.parse(position, text, letters)` / `parseOrNull`. The task asked for one; design §4.4 said v1 needs none (see Deviations).
  - Accepted, beyond strict SAN: a missing `+`/`#` (and `+` on a mate), `0-0`/`0-0-0`, a promotion without `=` (`e8Q`), a missing `x` (`ed5`, `Ne5`), extra disambiguation (`Ng1e2`, `e2e4`), an `e.p.` suffix before or after the check mark, `!`/`?` annotations, and surrounding spaces.
  - Rejected: anything that claims something false (an `x` that captures nothing, a `+`/`#`/`e.p.` that does not hold), ambiguity, a missing or impossible promotion piece (`e8`, `e8=K`, `Nf3=Q`), castling written as a king move (`Kg1`), a pawn "push" onto an occupied square (`d5` never means `exd5`), lowercase piece letters, `-` separators, and other malformed text.
  - The `IllegalMoveException` message says why ("ambiguous", "not legal", "not check", "not mate", "malformed") and gives the FEN, for logs.
- **`Game.sanList(letters = INTERNATIONAL)`**: one SAN string per move. It takes the check and mate marks from the stored positions, so nothing is replayed, and the legal-move lists it needs are cached in each `Position`.

### Files touched
`engine/src/main/kotlin/chess/engine/rules/{San,Uci}.kt` (new), `engine/src/main/kotlin/chess/engine/rules/Game.kt` (`replay` through `Uci`, plus `sanList`), `engine/src/test/kotlin/chess/engine/rules/{UciTest,SanTest,SanParseTest}.kt` (new), `engine/src/test/kotlin/chess/engine/rules/RulesTestSupport.kt` (`randomGame`/`randomGameOf` helpers), this log. `app/` and the board and search files are not touched; `Board` and `Position` are unchanged.

### Tests added (written first, seen failing)
Red: with the tests written and no implementation, `:engine:compileTestKotlin` failed with 81 errors (no `Uci`, `San`, `SanLetters`, `Game.sanList`). All suites passed on the first run after the implementation, so I checked that they can fail with temporary mutations, each reverted:
- Never disambiguating by rank alone, always writing `+` for mate, and letting a king move parse as castling: 4 failures (the table, both games, and `rejectsIllegalMoves`).
- Ignoring the promotion letter in `Uci.parseOrNull`: 7 failures (5 in `UciTest`, 2 in `GameTest` replay).

`:engine:test` now runs **231 tests** (T-6: 198; +33).
- `UciTest` (10):
  - `format` for quiet moves, double pushes, captures, both castlings, en passant, and promotions.
  - `parse` infers castling from the king move (and rejects it without the right), en passant (the captured pawn is removed), and all four promotions, pushing or capturing.
  - It rejects malformed or illegal text: empty, short, uppercase, padded, `e2-e4`, `0000`, off-board, the other side's move, castling through pieces, a missing promotion, a `k`/`p`/uppercase promotion, and any move in a mated position. `parseOrNull` returns null for each.
  - **Every legal move of the 7 perft FENs**, and every legal move at every ply of 20 random games, round-trips through `format`/`parse`.
- `SanTest` (9):
  - **A table of 38 cases**: `e4`, `e3`, `Nf3`, `Nc3`, `Nbd7`/`Nfd7`, `R1e2`/`R3e2`, `Rad1`/`Rhd1`, `N1f3`/`N5f3`, **three queens `Qh4xe1`/`Q1xe1`/`Qexe1`**, a pinned knight that adds no disambiguation (`Ne2`), `exd5`, `e5`, `Nxe5`, `Kxd2`, en passant `exd6` and `exd6+`, `e8=Q+`/`e8=R+`/`e8=B`/`e8=N`/`e8=N+`, `axb8=Q+`, `a8=Q`, **`bxa1=N#`**, `bxa1=Q`, `O-O` and `O-O-O` for both colours, `O-O-O+`, `Ra8+`, `Ra8#`, and a stalemating `Qb6` with no suffix. Each entry also parses back to its move.
  - **Two published games**: the Opera Game (Morphy, Paris 1858, 17 moves) and the Immortal Game (Anderssen v Kieseritzky, London 1851, 23 moves). Each is replayed from UCI, and `sanList` equals the published PGN movetext token for token. Both end in `Checkmate(WHITE)`. The Immortal Game's SAN also parses back into the same `Game`.
  - `sanList` equals `format` ply by ply, and is empty for a new game. `format` rejects an illegal move.
  - **SAN→Move→SAN round trip**: every legal move has a distinct SAN that parses back to it, in the 7 perft FENs, at every ply of 30 random games from the start, and at every ply of 21 random games from the perft FENs.
- `SanParseTest` (14): every tolerant form and every rejection listed above. Ambiguity is checked with two knights, two rooks on a file, and the three queens (`Qxe1`, `Qe1`, `Qhxe1`, `Q4xe1`). Also covered: the exception message; that `INTERNATIONAL` is K Q R B N; **Russian letters** (`Кf3`, `Фh4xe1`, `e8=Ф+`, `e8=К`, `Крd2`, `O-O`, a `sanList` from `e4` to `Сg4`, `Nf3` rejected, and a perft-position round trip); and that invalid letter sets are rejected.

### Commands run
- Setup: `local.properties` written with the double-backslash `sdk.dir` (checked with `od -c`). It is git-ignored.
- Baseline: `./gradlew :engine:test` → BUILD SUCCESSFUL, 198 tests.
- Red: `./gradlew :engine:compileTestKotlin` → FAILED, 81 errors, as expected.
- `./gradlew :engine:test` → BUILD SUCCESSFUL, 231 tests, 0 failures. The mutation runs FAILED as listed above and were reverted.
- GATE: `./gradlew :engine:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` → **BUILD SUCCESSFUL** in 2 min 41 s.
  - `:engine:test`: 231 tests, 0 failures, 0 skipped. `SanTest` took 0.6 s.
  - `:app:testDebugUnitTest`: 29 tests, 0 failures.
  - Lint: 0 errors, 87 warnings (86 `UnusedResources` and 1 `OldTargetApi`, all in `:app`, which this task does not touch).
  - `app-debug.apk` built. No Kotlin compiler warnings.
  - The T-6 replay timing test still passes with `Uci.parseOrNull`: 2,000 plies, best 5.8 ms in a plain `:engine:test` and 11.9 ms in the GATE run (limit 50 ms).

### Deviations from the plan
- **There is now a SAN parser**, which design §4.4 said v1 does not need. The task asked for one, for the tests and for later use (for example a PGN import or typed moves). The save format is still UCI (ADR-5).
- **`SanLetters` is in `San.kt`**, and piece letters are injectable, so T-29 can choose localized letters without changing the engine.
- **The plan's "known 20-move game"** is two published games instead, of 17 and 23 moves.
- **`Uci.format` and `Uci.parseOrNull`** are added next to `Uci.parse`. `Move.uci()` stays, and `Uci.format` delegates to it.

### Follow-ups
- T-22: show `Game.sanList()` in the move list. Build the SAN speech (`SanSpeech.kt`) from `Move` and `Position` data (piece, capture, check), not by reading the SAN string aloud.
- T-29: decide whether the move list uses localized letters. If it does, pass a `SanLetters` built from string resources to `Game.sanList` / `San.format`.

## 2026-10-14 — T-15: Vertical slice: play a legal game against a random-move computer (first demo)

**Stories:** US-3 (tap to move), US-4 (legality, castling, en passant; promotion auto-queens for now), US-6 (off the main thread, 400 ms minimum, board locked), US-5 (status only). Design 02 §4.3.3, §4.3.4 and the US-3/US-6 flows; tech design §6.3–§6.5 and §8.2.

### What changed and why
- **Computer seam** (`ai/AiPlayer.kt`, `ai/RandomMoveAiPlayer.kt`).
  - `AiPlayer.chooseMove(game, level): Move` is the interface `EngineAiPlayer` will implement in T-19.
  - `RandomMoveAiPlayer` is marked TEMPORARY (deleted in T-19). It picks a uniformly random legal move inside `withContext(dispatcher)`, so the work never runs on the caller's thread. It refuses a finished game with `IllegalStateException`.
- **`AppContainer`** gains `timeSource = TimeSource.Monotonic` and `newAiPlayer()`, which builds `RandomMoveAiPlayer(dispatchers.default, Random.Default)`. In T-19 only the body of `newAiPlayer()` changes.
- **UI state** (`ui/game/GameUiState.kt`, tech design §6.3): `GameUiState(level, player, board, status)`.
  - `StatusUi` is structured, not text: `YourMove`, `Check`, `Thinking(check)`, `NoLegalMoves` (the 2.5 s hint), `EngineError` and `GameOver(GameEnd)`.
  - `GameEnd` covers mate (win or loss) and the four draws.
  - The ViewModel holds no strings; the status row looks the words up.
- **`GameAction`**: `SquareTap(square)` and `RetryEngine`. Touch and TalkBack clicks use the same `SquareTap` path.
- **`GameUiMapper`** is pure and is the only engine → UI conversion.
  - `board(game, player, selected)`:
    - pieces from `Position.pieceAt`
    - the last move from the last ply
    - the checked king's square
    - `interactive` only on the player's turn in an ongoing game
  - `targets(moves)`, in this order:
    - castling → `Castling` on the king's destination (the engine encodes castling as the king's move)
    - en passant → `EnPassant` on the empty landing square (a ring)
    - promotion with or without a capture → `CapturePromotion` or `Promotion`; the four promotion moves collapse into one target
    - then `Capture` or `Move`
  - `status(game, player, hint, engineError)`:
    - a finished game wins over everything
    - the computer's turn is `Thinking(check)` or `EngineError`
    - the player's turn is the hint, `Check` or `YourMove`
- **`GameViewModel`** (tech design §6.5):
  - It holds the engine `Game` privately, with `selected`, `hint`, `engineError`, `generation`, `aiJob` and `hintJob`. Every change republishes `uiState` through the mapper.
  - **Tap rules** (design §4.3.3). A tap is ignored unless it is the player's turn in an ongoing game. Otherwise:
    - a target plays the move
    - the selected piece deselects
    - another own piece with legal moves takes the selection
    - an own piece with no legal moves clears the selection and shows the hint for 2.5 s; a new tap ends the hint and a repeat restarts it
    - anything else deselects silently
  - **Promotion:** `choosePromotion` always takes the queen. It is marked TEMPORARY for T-16's picker.
  - **AI loop:**
    - After each applied move, if it is the computer's turn, one `aiJob` on `viewModelScope` calls the AI.
    - It waits out `MIN_AI_DISPLAY` (400 ms), measured with the injected `TimeSource`.
    - It applies the move only if `generation` is unchanged. A `// T-19` comment marks where the move will be held behind dialogs.
    - Cancellation is rethrown. Any other exception goes to `onAiError` (which logs by default) and sets `EngineError`; **Try again** restarts the turn.
    - Clearing the ViewModel cancels the search.
  - `Factory` builds it from `ChessApplication.container`: White, level 2, `Game.start()`. This is the temporary direct start from Home's **New game**.
- **`StatusRow`** (design §4.3.4):
  - **Decorative indicator:**
    - the turn dot in the player's colour
    - the board's check badge
    - a 20 dp spinner
    - `info` and `error` icons
    - `flag` for a finished game
  - **Text:** `titleMedium`, and it wraps.
  - **Containers:** `errorContainer` for check and the engine error, `secondaryContainer` for game over.
  - **Try again:** a `TextButton`, shown only for the engine error.
  - **Live region:** the indicator and text are one merged node with `liveRegion = Polite` and `contentDescription` set to the status text (tech design §8.2). Its test tag is `status_row`.
- **`GameScreen`** is split into `GameRoute` and a stateless `GameScreen(state, onAction, onHome)`. `GameRoute` collects the ViewModel's state with `collectAsStateWithLifecycle`.
  - `GameScreen` has the top bar titled "Level 2 · Casual" (`level_label` + `level_name_n`), then the real `ChessBoard` wired to `SquareTap`, then the status row. The board is full width and square, and shrinks if the height is short.
  - `ChessNavHost` routes `Game` to `GameRoute`.
- **Strings:** none new. Everything shown comes from existing copy-deck keys (`status_*`, `result_*`, `level_*`, `action_try_again`).
  - Lint's `UnusedResources` count fell from 86 to 68.
  - T-26 (running in parallel) can merge `strings.xml` without a conflict.
- **Dependencies** (both already in the catalog, design §1.2):
  - `androidx.lifecycle:lifecycle-viewmodel-compose` (implementation), for `viewModel(factory)`
  - `kotlinx-coroutines-test` (testImplementation)

### Files touched
- New: `app/src/main/kotlin/chess/app/ai/{AiPlayer,RandomMoveAiPlayer}.kt`, `app/src/main/kotlin/chess/app/ui/game/{GameAction,GameUiMapper,GameViewModel,StatusRow}.kt`.
- Changed: `app/build.gradle.kts`, `app/src/main/kotlin/chess/app/AppContainer.kt`, `app/src/main/kotlin/chess/app/nav/ChessNavHost.kt`, `app/src/main/kotlin/chess/app/ui/game/{GameUiState,GameScreen}.kt`.
- Tests (new): `app/src/test/kotlin/chess/app/ai/{FakeAiPlayer,RandomMoveAiPlayerTest}.kt`, `app/src/test/kotlin/chess/app/testutil/MainDispatcherRule.kt`, `app/src/test/kotlin/chess/app/ui/game/{GameUiMapperTest,GameViewModelSelectionTest,GameViewModelAiTest,StatusRowTest,GameScreenTest}.kt`.
- `docs/product/chess-vs-computer/screens/t15-demo.png` (the emulator screenshot) and this log.

### Tests added (written first, seen failing)
**Red:** `:app:compileDebugUnitTestKotlin` failed with `Unresolved reference 'AiPlayer'`, `'RandomMoveAiPlayer'`, `'GameViewModel'`, `'GameRoute'`, `'StatusRowTestTag'`, `'newAiPlayer'` and others. All 70 new tests passed on the first green run.

**Mutation check (reverted; `grep -rn MUTATION app/src` finds nothing).** Six mutations together failed 12 tests across all five suites:
- removing the 400 ms wait
- removing the ViewModel's turn guard
- removing the hint-timer cancel
- mapping castling as `Move`
- mapping en passant as `Capture`
- making the live region `Assertive`

At first the mapper masked the turn-guard mutation, because the status shows "thinking" either way. `tapsDuringTheComputersTurnAreIgnored` now also checks that a tap while the board is locked leaves no hint behind for the player's turn. With only that mutation applied, it fails.

- `GameUiMapperTest` (JVM, 24):
  - the start position and every piece kind
  - Black at the bottom, with the board locked at the start
  - the last move, including castling as the king's move with the rook moved
  - the check square for both colours and on mate
  - dots for quiet moves and rings for captures
  - **en passant as a ring on the empty destination**
  - **castling targets the king's destination** for both colours; the rook's square is not a target
  - promotion vs capture-promotion
  - the status for every combination of turn, check, hint and error
  - every `GameEnd`, from FENs and a threefold knight shuffle
  - game over wins over the hint and the error
- `GameViewModelSelectionTest` (JVM, `runTest` + `MainDispatcherRule`, 18):
  - the new-game defaults
  - select, move, and reselect another own piece
  - tapping the selected piece deselects it
  - illegal empty and enemy squares deselect silently
  - taps with nothing selected do nothing
  - **an own piece with no legal moves selects nothing, and the hint lasts exactly 2,500 ms**
  - reselecting such a piece
  - the next tap ends the hint, and a repeat gets its own 2.5 s
  - **taps during the computer's turn are ignored** and leave no hint behind
  - taps after the game ends are ignored
  - castling, en passant and auto-queen promotion by taps
  - a checking move
  - the action path TalkBack uses
- `GameViewModelAiTest` (JVM, virtual time, 11):
  - the computer is asked only after the player's move, with the game and the level
  - **a 50 ms answer is applied exactly at 400 ms (not at 399); a 700 ms answer exactly at 700 ms**
  - the board stays locked while the computer thinks
  - as Black, the computer opens
  - the computer's mate and the player's mate both end the game, with no search after the end
  - an engine failure → `EngineError`, reported once; **Try again** → a move
  - retry does nothing when there is no error
  - **clearing the ViewModel cancels the search**, and no stale move is applied
  - a whole game against the real `RandomMoveAiPlayer` stays legal to the end (or 200 plies)
- `RandomMoveAiPlayerTest` (JVM, 7):
  - legal moves in 5 positions (castling, en passant, promotion, check)
  - the same seed gives the same moves
  - the moves vary
  - **it runs on the injected dispatcher**: a recording dispatcher sees the dispatch
  - **the container's AI runs on the container's `default` dispatcher**
  - cancellation
  - it refuses a finished game
- `StatusRowTest` (Robolectric Compose, 4):
  - every `StatusUi` shows its copy-deck text and speaks it
  - the live region is `LiveRegionMode.Polite`
  - **Try again** appears only for the engine error, and it calls back
- `GameScreenTest` (Robolectric Compose, 360 × 640 dp, 6). It runs through the real `GameRoute`, `ChessBoard` and `StatusRow`, with real touches on the board:
  - **tap e2 then e4 → "e4, white pawn, last move to"**, plus "e2, empty, last move from" and "Computer is thinking…"
  - three full moves against a scripted computer (e4 e5, Nf3 Nc6, Bc4 Nf6)
  - the reply is not shown at 300 ms of main-looper time and is shown at 450 ms
  - the no-legal-moves hint
  - the "Level 2 · Casual" heading and the Home arrow
  - Black sees h1 first, and the computer opens

### Commands run
- Baseline GATE before any change: **BUILD SUCCESSFUL**.
  - Engine 198 tests, app 100 tests.
  - Lint: 0 errors, 87 warnings.
- Red: `./gradlew :app:testDebugUnitTest` → compilation FAILED, as above.
- Mutation runs:
  - `./gradlew :app:testDebugUnitTest --tests 'chess.app.ui.game.*'` with all six mutations → 106 tests, 12 failed
  - the turn-guard mutation alone → 1 failed
  - both reverted
- GATE `./gradlew :engine:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` → **BUILD SUCCESSFUL**.
  - `:engine:test`: 198 tests, 0 failures. This branch has T-6; T-7 and T-8 are on other branches.
  - `:app:testDebugUnitTest`: **170 tests, 0 failures, 0 skipped** (20 suites; 70 new).
  - Lint: 0 errors, 69 warnings. 68 are `UnusedResources` (down from 86), all pre-existing copy-deck keys for later screens; the other is `OldTargetApi` (deliberate).
  - No Kotlin compiler warnings.
- **Emulator (`chess36`)**, following `scripts/emulator.md`:
  - **Boot:** a headless cold boot reached `sys.boot_completed=1` in 60 s. `wm size` 720x1280, density 320.
  - **Install and launch:** `./gradlew :app:installDebug` installed on `chess36(AVD) - 16`. `am start -W` reported a COLD launch of 6.5 s total (first boot, unoptimised debug build).
  - **System UI dialog:** right after boot, the emulator's System UI showed "System UI isn't responding" (not the app). "Wait" dismissed it.
  - **Play:** moves were made with `adb shell input tap` on the 90 px squares, and the board was read back with `uiautomator dump` (the squares' content descriptions).
    - The game went **1. e4 h5 2. Nf3 h4 3. Bc4 c6**.
    - Each random computer reply appeared after the player's move, with "Your move" back in the status row.
  - **Screenshot:** after selecting the c4 bishop (8 dots and a ring on f7), `adb exec-out screencap -p` saved `docs/product/chess-vs-computer/screens/t15-demo.png`.
  - **Logs:** `adb logcat -d --pid=<app>` had no StrictMode, exception or `GameViewModel` lines, and the crash buffer was empty.
  - **Shutdown:**
    - `adb -s emulator-5554 emu kill` → "OK: killing emulator, bye bye", and `adb devices` was empty.
    - The emulator's own `-kill … -sleep 20` watchdog exited by itself. After that, no `emulator` or `qemu` process remained.
    - `grainlab_test` was not touched.

### Deviations from the plan
- **`StatusUi` is structured.** It has `NoLegalMoves` instead of tech design §6.3's `Hint(text)`, so the ViewModel holds no strings (scope change: hy/ru).
- **`StatusUi.GameOver(GameEnd)`** shows the result title in the status row ("US-5 status only") until T-16's result panel. T-16 can grow `GameEnd` into its `GameResult`/`ResultUi` mapping.
- **The engine error and Try again are already in** (the plan puts them in T-19), so a failed search is never a dead end. T-19 keeps them and adds the lifecycle and held-move parts.
- **The live region speaks only the status sentence.**
  - The structured `Announcement`, the event sentences ("You: pawn e2 to e4.") and the U+200B repeat trick belong to T-24, as the plan lists them there.
  - Until then, TalkBack hears "Computer is thinking…" after the player's move. Design §5.4 instead folds that into the move sentence.
- **Some `GameUiState` fields are not here yet:** `phase`, `overlay`, `result`, `canUndo`, `sanMoves` and `announcement`. The `GameUiState` KDoc lists which task adds each one.
- **No lifecycle start/stop yet** (T-19). Leaving the screen cancels the search by clearing the ViewModel, but backgrounding the app does not pause it.
- **`GameRoute` plus a stateless `GameScreen`**, instead of one `GameScreen`. `ChessNavHostTest` is unchanged and passes.
- **Reduced motion:** the spinner is always animated. The static `hourglass_empty` icon comes in T-25.

### Follow-ups
- T-16: replace `GameViewModel.choosePromotion` (auto-queen) with `Overlay.Promotion(from, to)` and `GameAction.Promote`. The result panel replaces `StatusUi.GameOver` in the status row.
- T-17:
  - save after `applyMove`, where a comment marks the spot
  - load the game on init
  - when restoring with the computer to move, start the search on `ScreenStarted`
- T-18: the New game sheet replaces the direct start and passes `player` and `level` to the ViewModel.
- T-19:
  - `AppContainer.newAiPlayer()` returns `EngineAiPlayer`
  - delete `RandomMoveAiPlayer` and its test
  - hold the move where the `// T-19` comment is
  - add `ScreenStarted`/`ScreenStopped`
- T-23: `GameLayout` takes over the board sizing from the simple column.
- T-24: announcements go in the status row's live region (the `StatusRowTestTag` node), as event plus status sentences.
- The scratchpad directory is shared with parallel agents; another agent's script replaced one of my files there. Keep throwaway scripts under the worktree's ignored `build/` folder instead.

## 2026-10-14 — T-8: Evaluation and search (iterative deepening, alpha-beta, quiescence, transposition table, stop)

**Stories:** US-6.

### What changed and why
- **New package `chess.engine.search`** (`:engine` only, Kotlin stdlib only, no coroutines; ADR-1, ADR-6). `app/`, SAN/UCI and `Game.replay` are not touched (T-14 and T-7 run in parallel). No `Board`, `Position` or `Game` signature changed.
- **`Engine`** (public) is the API of design §4.6, as written: `Engine(ttEntriesLog2 = 18)`, `newGame()`, and `search(game, params, random, nanoTime = System::nanoTime, shouldStop = { false }): SearchResult`. `SearchResult(move, scoreCp, depth, nodes, elapsedMs, stopReason)` and `StopReason { DepthCap, NodeBudget, Deadline, Cancelled, OnlyMove }` are unchanged. Additions: `Engine.CHECK_INTERVAL = 1024`, `SearchResult.MATE_SCORE = 32000`, and `SearchResult.mateInPlies` (positive when the side to move mates, negative when it is mated, else null).
  - It throws `IllegalArgumentException` unless `game.status == Ongoing`.
  - `random` is accepted but not used yet. T-9's level randomisation will use it, and the search itself is deterministic.
  - With one legal move, it returns at once (`OnlyMove`, depth 0, 0 nodes).
- **`LevelParams`** (public): `depthCap` (1..64), `nodeBudget`, `timeCapMs: Long?` (the PRD cap, or null for no deadline), and `quiescence`. `deadlineMs` = cap − 100 ms (`DEADLINE_MARGIN_MS`, ADR-3). `withoutDeadline()` is for matches. **`Quiescence`** (public): `Off` (level 1), `CapturesOnly(maxPlies)` (level 2) and `Full` (levels 3 and 4). `Levels.params(level)` and randomisation are left for T-9.
- **`Eval`** (internal). It covers material (100/320/330/500/900) and the tapered middlegame/endgame PSTs in `Pst.kt`, with phase N 1, B 1, R 2, Q 4 out of 24. It adds a bishop pair (30) and tempo (10). Mop-up applies when one side has only its king and the other has at least a rook's worth of pieces: `10 × lone king's centre distance + 4 × (14 − king distance)`. A side with no pawns and at most one minor piece never scores above 0, so K+B v K, K+N v K and K+B v K+B evaluate as 0. The eval does not allocate. Table provenance is recorded in `docs/licences/eval-tables.md` (new; design §4.5 asks for it).
- **`Search`** (internal):
  - Negamax alpha-beta with iterative deepening, and quiescence (stand-pat, MVV-LVA, a 200 cp delta-pruning margin; captures, plus queen promotions in `Full`).
  - A +1 check extension, and move ordering (`Ordering.kt`): TT move, then MVV-LVA captures and promotions, then 2 killers per ply, then history.
  - It uses preallocated per-ply buffers and the mutable board from `Position.toBoard()`.
  - **Draws inside the search score 0.** That covers a position seen before since the last pawn move or capture, on the search path or in the game's history (`Board.key` / `Position.repetitionKey`; twofold counts). It also covers the 50-move rule (unless the side to move is mated, so a mate on the 100th halfmove wins), stalemate, and `Draws.isInsufficientMaterial(board)`.
  - **Stop.** The node budget, the deadline (`nanoTime() − start >= cap − 100 ms`) and `shouldStop` are checked every 1,024 nodes. The budget and deadline are also checked before each new iteration. `shouldStop` is honoured at once, even during depth 1 (→ `Cancelled`, still a legal move). The budget and deadline are ignored until depth 1 has completed, so depth 1 always completes.
  - **Partial iterations.** The previous best move is searched first. If the search stops mid-iteration after another move has beaten it, that move and its score are used. Otherwise the last completed iteration's move is kept. The reported depth is always the last completed one.
  - A proven mate (mate distance ≤ completed depth) ends the search early, reported as `DepthCap`.
- **`TranspositionTable`** (internal): 2^18 entries in two `LongArray`s (key, packed data), 4 MiB.
  - The data word packs move (19 bits), score (16), depth (8), bound (2) and search generation (6).
  - Buckets of two slots. The first is depth-preferred: it is replaced only by an entry at least as deep, by the same key, or when its entry is from an earlier search. The second is always replaced.
  - Mate scores are stored relative to the node (+ply) and loaded relative to the root (−ply).
  - `newGame()` clears it, along with killers and history. History is halved between searches.
- **`:engine:searchBench`** (new `Test` task, JUnit category `chess.engine.SearchBench`, excluded from `test` like `perftDeep`) measures nodes per second at level 4's 1.5 M-node budget over 8 positions.

### Files touched
`engine/src/main/kotlin/chess/engine/search/{Engine,Search,Eval,Pst,TranspositionTable,Ordering,LevelParams}.kt` (new), `engine/src/test/kotlin/chess/engine/search/{SearchTestSupport,EvalTest,TranspositionTableTest,SearchTest,EngineTest,SearchBenchTest}.kt` (new), `engine/src/test/kotlin/chess/engine/SearchBench.kt` (new, category marker), `engine/build.gradle.kts` (exclude `SearchBench` from `test`; `searchBench` task), `docs/licences/eval-tables.md` (new), `docs/product/chess-vs-computer/04-plan.md` (Results row), this log.

### Tests added (written first, seen failing)
Red: with the tests written and no `search` package, `:engine:test` failed to compile (276 unresolved references). On the first green run, 2 of 46 new tests failed because of my own test data: an invalid en passant FEN, and expecting R v B to be worth more than 300. Both were fixed in the tests; the code did not change. The default `:engine:test` now runs **249 tests** (T-6: 198; +51), plus `SearchBenchTest` in `searchBench` only.
- `EvalTest` (8):
  - **`eval(p) == −eval(mirror(p))` from White's view, and equal from the side to move's view, over 57 positions.** These are the 7 perft FENs, 8 hand-picked positions and 42 from seeded random play.
  - The start position is worth exactly the tempo bonus. Material dominates.
  - K+B v K, K+N v K and K+B v K+B score 0, but a pawn keeps the winning chances.
  - Mop-up values, including that it switches off when the defender has a pawn. The corner beats the centre, and close kings beat distant ones.
  - Endgame vs middlegame king tables.
- `TranspositionTableTest` (8): store/probe round trip; a miss on a different key; **mate scores ply-adjusted (stored at ply 3, loaded at ply 7 and ply 1; also mated scores)**; depth-preferred vs always-replace slots; aging by search generation; re-storing a key keeps its old move when the new one is `NONE`; `clear`; the default size is 2^18.
- `SearchTest` (23):
  - **Mates.** An independent brute-force prover (`hasForcedMate` in `SearchTestSupport`) first checks every mate position. **Mate in 1 (5 positions) and mate in 2 (3) are found at depth 4, and mate in 3 at depth 6.** The score is exactly `MATE − plies`, and the move is verified to force mate. The side being mated sees `−(MATE − 2)`. The check extension finds a checking mate in 2 at depth 2. A mate on the 100th halfmove beats the 50-move draw.
  - **Material.** It takes a hanging queen and a free rook. **It does not leave the queen en prise in 5 positions at 50 k nodes.** A queen is en prise if a cheaper piece can take it, or if it is taken while undefended.
  - **Quiescence.** With `Off`, depth 1 grabs a pawn defended by a pawn. With `Full`, it doesn't. `CapturesOnly(1)` stops a winning R×d5 sequence too early, and `CapturesOnly(64)` matches `Full`.
  - **Draws.** The winning side never stalemates (K+Q v K with two stalemating queen moves). Capturing into K+B v K scores 0. With the halfmove clock at 99 it plays a pawn move. **It avoids a threefold when 3 pawns ahead** (the natural Ng1-f3 would complete it), at depths 1–4. The losing side takes a repetition and scores 0.
  - **TT.** **With and without the TT, the move and score are identical at depths 1–4 in 10 positions, and the TT never costs nodes.**
  - **Iterative deepening.** Each iteration searches the previous best move first. **A search stopped by its node budget right after a later root move beat the previous best keeps that improvement.** The `RootMoveListener` test hook records an iteration, then the test reruns with a budget that lands in it.
  - **Determinism.** The same search gives the same result. `newGame()` resets to a fresh engine's result. The `Random` passed in does not change the result.
- `EngineTest` (12):
  - A finished game is rejected (mate, stalemate, threefold). An only move returns at once.
  - The depth cap. **The node budget is respected within 1,024** (3 positions). **`shouldStop` is honoured within 1,024**: flipped on its 5th poll, the search stops at 4,097–5,120 nodes. Cancelling during depth 1 still returns a legal move (7 positions).
  - **With a fake `nanoTime` whose deadline has already passed, it returns the legal depth-1 move** (7 positions). **Depth 1 completes even when its own stop checks see the budget or deadline passed:** a position whose depth 1 takes more than 2,048 nodes gives exactly the depth-1 result.
  - Deadline = cap − 100 ms: with a 300 ms cap and a fake clock advancing 1 ms per reading, it stops at 200–202 ms.
  - `LevelParams` validation.
  - **Every returned move is legal across 500 random positions** (seeded; 1,000-node budget; `newGame` every 50).
  - **It logs nodes per second** (about 1 M nodes; line `T-8 JVM search speed: …`).

**Mutation checks.** Each mutation was applied by a scratch script, then reverted, and `grep -rn MUTATION engine/` finds nothing. Every one fails at least one test:
- No repetition: 2 tests fail.
- No 50-move rule: 1.
- 50-move checked before mate: 1.
- Stalemate scored as mate: 1.
- Budget/deadline enforced during depth 1: 1.
- No insufficient-material draw (search and eval): 2.
- TT cut-off on entries 2 plies too shallow: 3.
- No check extension: 2.
- Partial-iteration improvement ignored: 1.
- Black PSTs not mirrored: 4.
- No mop-up: 1.

The first round left 4 survivors (depth 1, check extension, partial iteration, mop-up). The depth-1, check-extension, partial-iteration and mop-up-value tests above were added for them.

### Search speed
`./gradlew :engine:searchBench`, 8 positions × 1.5 M nodes (level 4's provisional budget), full quiescence, after warm-up:

| Position | Run 1 (loaded machine) | Run 2 | Depth | Move |
|---|---|---|---|---|
| start | 2.11 M/s | 2.25 M/s | 8 | e2e4 |
| Kiwipete | 2.13 M/s | 1.92 M/s | 6 | e2a6 |
| position 3 | 1.05 M/s | 1.76 M/s | 9 | b4f4 |
| position 4 | 0.80 M/s | 1.20 M/s | 8 | c4c5 |
| position 5 | 0.83 M/s | 1.67 M/s | 7 | d7c8q |
| position 6 | 1.00 M/s | 1.96 M/s | 6 | c3d5 |
| Italian | 1.69 M/s | 1.66 M/s | 6 | e1g1 |
| K+P endgame | 2.15 M/s | 1.83 M/s | 16 | c3c4 |
| **Total** | **1.25 M/s** (9.62 s) | **1.73 M/s** (6.94 s) | | |

Both runs chose the same moves and reached the same depths (deterministic). Run 1 shared the machine with other agents' builds. The plan's Results table records about 1.7 M nodes/s. For T-10: one level-4 move takes about 0.7–1.3 s on this JVM, so a 100-game L4 pairing of about 60 moves a side runs about 2–3 hours of CPU. Across 8 cores, that is roughly 15–25 minutes, in line with design §4.7.

### Commands run
- Setup: `local.properties` written with the Write tool and checked with `od -c`. It has double backslashes; a first `printf` attempt collapsed them, as previous agents found.
- Baseline: `./gradlew :engine:test` → BUILD SUCCESSFUL, 198 tests.
- Red: `./gradlew :engine:test` → compilation FAILED (276 unresolved references), as expected.
- `./gradlew :engine:test` → first green run, 244 tests, 2 failed (test data, above). After the fixes, BUILD SUCCESSFUL. After adding the mutation-driven tests: **249 tests, 0 failures**.
- Mutation runs: `./gradlew :engine:test --tests "chess.engine.search.*"`, once per mutation, from a scratch script → FAILED as listed above. Sources were restored after each run.
- `./gradlew :engine:searchBench` twice → BUILD SUCCESSFUL, 1 test (timings above).
- GATE (final tree): `./gradlew --rerun-tasks :engine:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` → **BUILD SUCCESSFUL** in 2 min 31 s, 61 tasks executed. An earlier GATE run on an almost-final tree also passed (1 min 47 s).
  - `:engine:test`: **249 tests, 0 failures, 0 skipped**. Search suites: SearchTest 6.6 s, EngineTest 1.4 s, EvalTest 0.3 s, TranspositionTableTest 0.1 s, run in parallel with the app's tasks. Run alone, `:engine:test` takes them about 4 s in total. The NPS line read 1.88 M nodes/s.
  - `:app:testDebugUnitTest`: 29 tests, 0 failures.
  - Lint: 0 errors, 87 warnings (86 `UnusedResources`, 1 `OldTargetApi`), all in `:app`, which T-8 does not touch.
  - `app-debug.apk` built. No Kotlin compiler warnings.
- The three slowest search tests were trimmed after the first GATE: the 500-position budget went from 1,500 to 1,000 nodes, the deadline test now uses a 300 ms cap, and the depth-1 position is fixed instead of chosen from 200 random games. What each test checks is unchanged. The depth-1 mutation was re-run against the trimmed test and still fails it.

### Deviations from the plan
- **Additions to the §4.6 API:** `Engine.CHECK_INTERVAL`, `SearchResult.MATE_SCORE` and `SearchResult.mateInPlies`. Nothing in §4.6 was changed or removed.
- **`random` is unused in T-8.** It is annotated `@Suppress("UNUSED_PARAMETER")` until T-9 uses it.
- **`LevelParams`** has no randomisation field yet. T-9 can add one with a default without breaking callers. `timeCapMs = null` means "no deadline" (matches, tests).
- **`shouldStop` is polled only by the 1,024-node check, not between iterations.** The budget and deadline are also checked between iterations. That makes `shouldStop` polls exactly countable in tests.
- **Proven mates end the search early** and report `DepthCap`, because §4.6's enum has no "mate found" reason.
- **The search's insufficient-material check** uses the PRD list (`Draws`). Separately, the eval gives no credit to a side with no pawns and at most one minor piece (so K+N v K+N, which the rules don't call drawn, evaluates as 0 too).
- **Quiescence** does not search check evasions (stand-pat even in check), and it only searches queen promotions. Both are common simplifications.
- **The TT is not cleared automatically on undo.** The design says to clear it on undo, so the caller must call `newGame()` after an undo (T-19, `EngineAiPlayer`). This is documented on `Engine`.
- **Extra files and tasks:** `SearchTestSupport.kt` (including an independent brute-force mate prover), `SearchBenchTest.kt` with its `SearchBench` category and `searchBench` task, `docs/licences/eval-tables.md`, and an internal `RootMoveListener` test hook on `Search` (null in production).
- **Mate in 3** and the extra draw, quiescence and iteration tests go beyond the plan's list (the caller asked for them).

### Follow-ups
- T-9: `Levels.params(level)` per ADR-3. Add randomisation (using the `random` argument) and exact root scores at the final depth via a lowered-alpha root window. The root loop in `Search.run` is the place, and `RootMoveListener` shows the per-move root scores.
- T-19: call `Engine.newGame()` on new game **and after undo** (design §4.6).
- T-10/T-11: the JVM speed is about 1.7 M nodes/s; the level-4 budget will be set from the device.
- Release review (T-26/T-27): decide whether the About screen should credit the Simplified Evaluation Function (`docs/licences/eval-tables.md`).
