// Top-level build file. Plugins are declared here with `apply false` so every module
// resolves the same versions from gradle/libs.versions.toml.
// Declaring the Kotlin JVM plugin here also aligns the Kotlin Gradle plugin version
// that AGP's built-in Kotlin support uses (design 03-tech-design.md §1.2).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
