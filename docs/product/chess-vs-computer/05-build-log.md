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
