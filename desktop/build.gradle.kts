plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose") version "1.11.1"
}

dependencies {
    implementation(compose.desktop.currentOs)
}

kotlin {
    jvmToolchain(21)
}

compose.desktop {
    application {
        mainClass = "eu.kanade.tachiyomi.desktop.MainKt"

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
            )
            packageName = "TachiyomiDNP"
            packageVersion = "1.0.0"
            description = "TachiyomiDNP Windows Desktop Edition"
        }
    }
}
