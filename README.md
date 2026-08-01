# StreamDek Android Kotlin

Native Android StreamDek client built with Kotlin and Jetpack Compose.

This repository is the detached `StreamDekMobile-Kotlin` line. The old React Native / Expo app path has been removed from the repository and build flow.

## What is here

- Native app UI in `android/app/src/main/java/net/streamdek/mobile/nativeapp/`
- Android entrypoint in `android/app/src/main/java/net/streamdek/mobile/MainActivity.kt`
- Native MPV playback via `android/app/src/main/java/net/streamdek/mobile/mpv/MPVView.kt`
- Native torrent streaming service under `android/app/src/main/java/net/streamdek/mobile/torrent/`

## Requirements

- Android Studio or JDK 17+
- Android SDK Platform 36
- A backend URL in the root `.env`

Example:

```env
STREAMDEK_API_URL=http://192.168.0.2:3000
```

## Build

From `android/`:

```powershell
.\gradlew :app:compileDebugKotlin
.\gradlew :app:assembleDebug
.\gradlew :app:installDebug
.\gradlew :app:assembleRelease
```

## Device install

```powershell
adb devices
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

## Current scope

- Auth
- Profiles
- Add-ons
- Debrid
- Trakt device auth, watchlist sync, and playback scrobbling
- Native detail, stream selection, and MPV playback

## Notes

- The repository is Kotlin/Android-only.
- The original mixed mobile repo remains separate in `C:\Dev\StreamDekMobile`.
