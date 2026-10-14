plugins {
    // AGP 9 compiles Kotlin itself (built-in Kotlin); do not apply org.jetbrains.kotlin.android.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.licensee)
}

android {
    namespace = "chess.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.armendeveloper.armchess"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Lets a debug build sit next to a release install on the same device (design §15.1).
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // No jvmToolchain(): bytecode targets 17, unit tests run on the Gradle JDK 21,
    // which Robolectric's SDK-36 runtime needs (design §1.4).
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric 4.17 on JDK 21 reflects into jdk.internal.access.SharedSecrets
            // (FileDescriptorInterceptor); without this the sandbox fails with IllegalAccessException.
            it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
        }
    }
}

dependencies {
    implementation(project(":engine"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    // Instrumented tests on chess36 or a device (T-2, scripts/emulator.md).
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Licence allow-list (tech design §9.4). Any other licence, GPL and LGPL included, fails
// :app:licenseeAndroidDebug / :app:licenseeAndroidRelease. Those tasks also run inside every
// assemble and unit-test build, because the report they write is bundled into the APK's assets
// (assets/app/cash/licensee/artifacts.json), which feeds S7b Licences.
licensee {
    allow("Apache-2.0")
    allow("MIT")
    allow("BSD-3-Clause")
    // MIT and BSD-3-Clause are pre-approved but unused today; don't warn about them on every build.
    unusedAction(app.cash.licensee.UnusedAction.IGNORE)
    bundleAndroidAsset.set(true)
}
