pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
        maven { url=uri("https://jitpack.io") }
    }
}

rootProject.name = "tachiyomiJ2K"
include(":app")
