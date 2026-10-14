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
