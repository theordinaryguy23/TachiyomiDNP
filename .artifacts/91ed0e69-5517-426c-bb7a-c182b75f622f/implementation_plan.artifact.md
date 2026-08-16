# Upgrade Gradle to 9.3.1 and AGP to 9.1.0

This plan upgrades the project's build system to Gradle 9.3.1 and the Android Gradle Plugin (AGP) to 9.1.0. To ensure compatibility with the existing build logic (specifically the `applicationVariants` API and the explicit Kotlin plugin), we will temporarily opt out of the new AGP 9.0 features (`newDsl` and `builtInKotlin`).

## User Review Required

> [!IMPORTANT]
> AGP 9.0 is a major update. While this plan uses opt-out flags to maintain compatibility, you should eventually refactor the build scripts to use the new `androidComponents` API and built-in Kotlin support.

## Proposed Changes

### Build Configuration

#### [MODIFY] [gradle-wrapper.properties](file:///C:/Users/RereNana/StudioProjects/TachiyomiDNP-2/gradle/wrapper/gradle-wrapper.properties)
- Update `distributionUrl` to `https://services.gradle.org/distributions/gradle-9.3.1-bin.zip`.

#### [MODIFY] [build.gradle.kts](file:///C:/Users/RereNana/StudioProjects/TachiyomiDNP-2/build.gradle.kts)
- Update Android Gradle Plugin version from `8.13.2` to `9.1.0`.
- Update `google-services` plugin from `4.4.4` to `4.5.0`.
- Update `firebase-crashlytics-gradle` from `3.0.6` to `3.0.7`.

#### [MODIFY] [gradle.properties](file:///C:/Users/RereNana/StudioProjects/TachiyomiDNP-2/gradle.properties)
- Add `android.newDsl=false` to keep using the legacy `applicationVariants` API.
- Add `android.builtInKotlin=false` to keep using the explicit `org.jetbrains.kotlin.android` plugin.

## Verification Plan

### Automated Tests
- Run `./gradlew clean assembleDebug` to verify the build still succeeds and APKs are generated with the expected names.

### Manual Verification
- Verify in Android Studio that the Gradle sync completes successfully.
