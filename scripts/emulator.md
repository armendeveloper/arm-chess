# Emulator harness (`chess36`)

Instrumented tests (`app/src/androidTest`) and every plan task marked **[emulator]** run on the
`chess36` AVD: Android 16 (API 36.1) with Google Play, x86_64, 720 × 1280 px at 320 dpi
(a **360 × 640 dp** screen, the design's tightest case), 2 GB RAM.

These are the exact commands verified on the dev machine (Windows 11, Git Bash, JDK 21,
emulator 37.2.12, WHPX) on 2026-10-14. Run them from the repo root in **Git Bash**.

```bash
SDK="$LOCALAPPDATA/Android/Sdk"   # C:\Users\armen\AppData\Local\Android\Sdk
```

> Never modify or delete other AVDs (for example `grainlab_test`). Everything below touches
> `chess36` only.

## 0. Prerequisites (one-off)

The system image is already installed; do not reinstall it. Check it is there:

```bash
ls "$SDK/system-images/android-36.1/google_apis_playstore/x86_64"
```

There is no plain `android-36` Play image; `android-36.1` is the one to use.

Check hardware acceleration:

```bash
"$SDK/emulator/emulator.exe" -accel-check
# accel:
# 0
# WHPX(10.0.26300) is installed and usable.
```

If this does not report a usable accelerator (WHPX or AEHD), stop. Turning on
"Windows Hypervisor Platform" is a Windows feature change for the machine owner to make.

## 1. Create the AVD (one-off)

```bash
echo no | "$SDK/cmdline-tools/latest/bin/avdmanager.bat" create avd \
  -n chess36 -k "system-images;android-36.1;google_apis_playstore;x86_64"
```

From Git Bash the `;`-separated package path works as-is when quoted: bash passes it to the
`.bat` as one double-quoted argument, so it is not split. (`sdkmanager.bat` is a different
story; see the plan's H-2 note and use `/` there.) If you call `avdmanager.bat` from `cmd.exe`
or PowerShell and it does split, `system-images/android-36.1/google_apis_playstore/x86_64`
is the fallback form.

Then set the screen and RAM in the AVD's own config (only `chess36.avd`):

```bash
sed -i \
  -e 's/^hw.lcd.width=.*/hw.lcd.width=720/' \
  -e 's/^hw.lcd.height=.*/hw.lcd.height=1280/' \
  -e 's/^hw.lcd.density=.*/hw.lcd.density=320/' \
  -e 's/^hw.ramSize=.*/hw.ramSize=2048/' \
  -e 's/^PlayStore.enabled=.*/PlayStore.enabled=yes/' \
  "$HOME/.android/avd/chess36.avd/config.ini"
"$SDK/emulator/emulator.exe" -list-avds    # chess36 (and any other AVDs, untouched)
```

`avdmanager` writes `PlayStore.enabled=no` even for a Play image; `yes` matches what Android
Studio sets for this image and keeps the Play Store usable (for installing TalkBack later).
A Play image does not allow `adb root`.

## 2. Boot

Headless (what CI-style runs and agents use):

```bash
"$SDK/emulator/emulator.exe" -avd chess36 -no-window -no-snapshot-save -no-audio -no-boot-anim &
```

With a window (to look at the app by hand):

```bash
"$SDK/emulator/emulator.exe" -avd chess36 -no-snapshot-save &
```

`-no-snapshot-save` keeps every run a clean cold boot (no `default_boot` snapshot is written).
The emulator prints a `-grpc` / jwt warning and "Failed to load snapshot 'default_boot'"; both
are expected.

## 3. Wait for boot

`adb wait-for-device` only waits for adbd. Wait for `sys.boot_completed` before installing:

```bash
ADB="$SDK/platform-tools/adb.exe"
"$ADB" wait-for-device
until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
"$ADB" devices                         # emulator-5554  device
"$ADB" shell wm size                   # Physical size: 720x1280
"$ADB" shell wm density                # Physical density: 320
```

A cold headless boot took **66 s** from launch to `sys.boot_completed=1` on this machine (WHPX).

## 4. Run the instrumented tests

```bash
./gradlew :app:connectedDebugAndroidTest
```

Gradle installs the debug APK (`io.github.armendeveloper.armchess.debug`) and the test APK,
runs every test on each connected device, and writes:

- `app/build/reports/androidTests/connected/debug/index.html` (HTML report)
- `app/build/outputs/androidTest-results/connected/debug/TEST-chess36(AVD) - 16.xml` (JUnit XML)

One class only:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=chess.app.LaunchSmokeTest
```

If more than one device is attached, set `ANDROID_SERIAL=emulator-5554` to pick one.

## 5. Shut down

```bash
"$ADB" -s emulator-5554 emu kill
"$ADB" devices                         # emulator-5554 is gone
```

Leave the adb server running; other tools may share it.

## PowerShell

The same commands work from PowerShell with `$SDK = "$env:LOCALAPPDATA\Android\Sdk"`, the call
operator (`& "$SDK\emulator\emulator.exe" ...`), `.\gradlew.bat`, and `Start-Process` instead of
`&` for a background boot. The `sed` and `until` steps are Git Bash only.

## Known issues

- **Espresso 3.5.0 fails on API 36.1** with `NoSuchMethodException:
  android.hardware.input.InputManager.getInstance` in `Espresso.onIdle`, which every Compose
  test calls. Compose `ui-test-junit4` brings in 3.5.0 transitively, so `app/build.gradle.kts`
  pins `androidTestImplementation(libs.androidx.test.espresso.core)` at 3.7.0. Keep that line
  when adding instrumented tests.
