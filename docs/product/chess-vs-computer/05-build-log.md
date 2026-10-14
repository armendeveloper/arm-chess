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
