plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose") version "1.11.1"
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(project(":shared"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

compose.desktop {
    application {
        mainClass = "eu.kanade.tachiyomi.desktop.MainKt"

        // Kotlin bytecode is compiled for Java 21. Explicitly use the same JDK
        // for jpackage/jlink so the bundled runtime can execute that bytecode.
        javaHome = System.getenv("JAVA_HOME")

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
            )
            // HttpClient is loaded by the MangaDex desktop source at runtime.
            // jpackage's dependency analysis can miss this module, so include it explicitly.
            modules("java.net.http")
            packageName = "TachiyomiDNP"
            packageVersion = "1.9.3"
            description = "TachiyomiDNP Windows Desktop Edition"
        }
    }
}
