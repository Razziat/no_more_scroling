# Anti Scroll for Android

[Français](README.md)

This first Android version blocks the **YouTube Shorts** and **Instagram
Reels** viewers while keeping the other parts of both applications available.

## MVP status

- Native Android application written in Kotlin and Jetpack Compose.
- Supports Android 8.0 and later.
- Independent YouTube and Instagram blocking switches.
- Local detection through an Android accessibility service.
- Automatic Back action when a Short or Reel is detected.
- Temporary “Stay focused!” message.
- Optional punitive mode: one attempt blocks the entire platform for 30
  minutes, with a countdown and manual unlock.
- French or English UI based on the Android language.
- No Internet permission and no transmitted data.

A new attempt during a penalty does not extend the 30 minutes. It only shows
the remaining time.

In punitive mode, Anti Scroll leaves the sanctioned application with Android’s
**Back** action. It does not trigger **Home**, so the launcher page or
application that was visible before opening it is preserved.

## Open and build the project

1. Install a recent stable version of Android Studio.
2. Open the `mobile/android` directory.
3. Let Android Studio install the requested Android SDK and sync Gradle.
4. Connect an Android phone with USB debugging enabled.
5. Run the `app` configuration.

The debug build can also be generated from a terminal:

```powershell
cd mobile/android
.\gradlew.bat testDebugUnitTest assembleDebug
```

The generated APK is located at
`mobile/android/app/build/outputs/apk/debug/app-debug.apk`.

## First activation

1. Open Anti Scroll.
2. Choose the platforms to protect.
3. Optionally enable **Punitive mode**.
4. Tap **Enable protection**.
5. Read and accept the disclosure.
6. In Android settings, select **Anti Scroll protection** and enable the
   service.

### Manually installed APK on Android 13 or later

Android may prevent the accessibility service from being enabled when Anti
Scroll was installed from an APK file instead of an app store. In that case:

1. Open **Settings > Apps > Anti Scroll**.
2. Open the **⋮** menu in the top-right corner.
3. Select **Allow restricted settings** and confirm your identity.
4. Return to **Settings > Accessibility > Installed apps**.
5. Select **Anti Scroll protection**, then enable the service.

Menu names can vary by device manufacturer. This is an Android security
measure for manually installed applications; the procedure is documented in
[the official Android Help](https://support.google.com/android/answer/12623953?hl=en).

Accessibility access is required to identify the visible screen and trigger
Android’s **Back** action. It is not used to read, store, or transmit the
user’s messages, searches, or videos.

## Architecture

```text
mobile/android/
├── app/src/main/java/com/antiscroll/mobile/
│   ├── MainActivity.kt
│   ├── accessibility/
│   │   ├── ShortFormBlockerService.kt
│   │   └── UiTreeReader.kt
│   ├── blocking/
│   │   ├── BlockCoordinator.kt
│   │   └── BlockOverlayController.kt
│   ├── data/
│   │   ├── SettingsRepository.kt
│   │   └── PunitiveLockManager.kt
│   ├── detection/
│   │   ├── YouTubeShortsDetector.kt
│   │   ├── InstagramReelsDetector.kt
│   │   └── ShortFormDetectionEngine.kt
│   └── ui/theme/
└── app/src/test/
```

The service only receives events from `com.google.android.youtube` and
`com.instagram.android`. It converts a limited copy of the accessibility tree
into an in-memory model and runs the platform-specific detectors. The copy is
discarded immediately after analysis and is never written to disk.

## Detection and false positives

The detectors prioritize:

- view identifiers specific to the Shorts or Reels viewer;
- an Android screen name when it is explicit enough;
- the selected state of the Shorts or Reels tab;
- explicit player descriptions.

The presence of an unselected “Shorts” or “Reels” navigation button alone is
not enough. This prevents the YouTube home page or Instagram feed from being
blocked.

YouTube and Instagram may change their interfaces without notice. Detection
rules are isolated and tested, but they can require updates when either
application changes. Final testing must be performed on a physical phone with
the versions that users actually have installed.

## Privacy

- no `INTERNET` permission;
- no telemetry;
- no account;
- no retained interface content;
- settings stored only in private Android preferences.

Before a potential Google Play release, use of the accessibility service must
be declared in Play Console and documented in the store listing.
