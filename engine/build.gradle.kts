import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// No jvmToolchain(): only JDK 21 is installed and auto-provisioning would need a download.
// Bytecode targets 17; tests run on the Gradle JDK (design §1.4).
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
}

// The perft gate (design §4.2): `test` runs the gate depths; the release depths are tagged with the
// JUnit 4 category chess.engine.PerftDeep and run only by `perftDeep`.
tasks.test {
    useJUnit { excludeCategories("chess.engine.PerftDeep", "chess.engine.SearchBench", "chess.engine.LevelChecks") }
}

val perftDeep by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs the release-depth perft suite (design §4.2). Takes about a minute."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnit { includeCategories("chess.engine.PerftDeep") }
    shouldRunAfter(tasks.test)
    // A release check and a timing record: always run it, never take it from the build cache.
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
}

// Search speed at level 4's node budget (T-8); prints nodes per second for the plan's Results table.
val searchBench by tasks.registering(Test::class) {
    group = "verification"
    description = "Measures JVM search speed (nodes per second) at level 4's node budget. Takes about 10 seconds."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnit { includeCategories("chess.engine.SearchBench") }
    testLogging { showStandardStreams = true }
    shouldRunAfter(tasks.test)
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
}

// The longer US-6 level checks (T-9, design §4.7): K+Q v K conversion by levels 3 and 4, and variety,
// at the real level parameters with the deadline off.
val levelChecks by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs the K+Q v K and variety checks at the real level parameters (design §4.7)."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnit { includeCategories("chess.engine.LevelChecks") }
    testLogging { showStandardStreams = true }
    shouldRunAfter(tasks.test)
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
}
