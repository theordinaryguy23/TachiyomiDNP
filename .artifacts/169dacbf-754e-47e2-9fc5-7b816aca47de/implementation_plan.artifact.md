# Fix for GMS SecurityException: Unknown calling package name

The application is experiencing a `java.lang.SecurityException: Unknown calling package name 'com.google.android.gms'` which causes GMS services (like Google Drive backup) to fail. This is typically due to package visibility restrictions introduced in Android 11+ and occasionally missing GMS configuration in the manifest.

## Proposed Changes

### [Component: Manifest]

#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/RereNana/StudioProjects/TachiyomiDNP-2/app/src/main/AndroidManifest.xml)
- Add `<queries>` block to declare visibility for `com.google.android.gms`. This is required for apps targeting API 30+ to interact with Play Services.
- Add `com.google.android.gms.version` meta-data to the `<application>` tag. While usually added by the `google-services` plugin, manual addition ensures GMS components can verify the version they are running against.

### [Component: Dependencies]

#### [MODIFY] [build.gradle.kts](file:///C:/Users/RereNana/StudioProjects/TachiyomiDNP-2/app/build.gradle.kts)
- Remove `play-services-gcm:17.0.0`. GCM is deprecated and its presence might cause conflicts with newer GMS libraries. It is not used in the codebase.

## Verification Plan

### Manual Verification
- Deploy the app to a device or emulator with Google Play Services.
- Navigate to Settings > Backup and attempt to sign in to Google Drive or perform a backup.
- Verify that the `SecurityException` is no longer thrown and the sign-in/backup process works.
