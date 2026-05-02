# streamdek-mobile

The primary StreamDek client - a React Native / Expo app for Android. Users can browse and search movies and shows, pick streams from installed addons, resolve torrents through debrid providers, and play media through a native MPV-based video player with full subtitle support.

---

## Tech Stack

- **Framework:** Expo 55 / React Native 0.83 / React 19
- **Language:** TypeScript
- **Navigation:** React Navigation (bottom tabs + native stack)
- **Video player:** Native MPV (custom Android module + `libmpv-release.aar`)
- **State:** React Context API
- **Auth:** Backend email/password sessions with JWT
- **Storage:** MMKV (`react-native-mmkv`) + AsyncStorage
- **Subtitles:** OpenSubtitles V3 via Stremio addon endpoint (no API key required)
- **Casting:** react-native-google-cast (Chromecast)
- **Gestures:** react-native-gesture-handler, react-native-reanimated
- **Platform:** Android-first; iOS supported

---

## Features

- **Browse & Search** - Trending, popular, and search-driven content via TMDB
- **Stream aggregation** - Fetches streams from all user-installed Stremio addons
- **Debrid resolution** - Resolves torrent hashes to direct links via Real-Debrid, AllDebrid, Premiumize, or Torbox
- **MPV player** - Hardware-accelerated native video playback with track selection, seek, speed, and volume controls
- **Subtitles** - Auto-loads from OpenSubtitles V3; manual selection, delay adjustment, and per-language preferences
- **Trakt sync** - Watch history, watchlist, and collection via device code auth
- **Watch progress** - Resumes from where you left off, per title
- **Addon management** - Install, enable/disable, and reorder Stremio addon sources
- **Chromecast** - Cast streams to any Google Cast device
- **i18n** - Multi-language UI support
- **Theming** - Dark / light mode

---

## Prerequisites

- Node.js 20+
- Android SDK (API 26+) and a connected Android device or emulator
- JDK 17 (required by Gradle)
- `streamdek-backend` running and reachable on your network

---

## Installation

```bash
cd streamdek-mobile
npm install
```

The `postinstall` script automatically runs `patch-package` to apply the `expo-video` Android patch.

---

## Configuration

### Backend URL

Do **not** edit `src/constants/api.ts` directly. The backend URL is driven by the root `.env` at the repo root, and the mobile app does not keep its own backend URL in `streamdek-mobile/.env`:

```env
# streamdek/.env
STREAMDEK_API_URL=http://192.168.0.2:3000
```

The `scripts/expo-with-env.js` startup script reads the root `.env` and exposes:
- `STREAMDEK_API_URL` -> `EXPO_PUBLIC_API_BASE_URL`

`src/constants/api.ts` first reads `EXPO_PUBLIC_API_BASE_URL` directly. If the app is launched outside the wrapper, `app.config.js` still reads the root `.env` and injects the same backend URL through Expo config `extra`.

When the full Docker stack is running, the mobile app should point directly at the backend host and port. There is no proxy layer in the current deployment.

### Auth and Reset Codes

The mobile app uses the backend JWT session flow. Forgot-password requests send a reset code through the backend, so the shared SMTP settings live in the repository root `.env`.

If you are running the full stack in Docker, update the root `.env` once and the web, mobile, and TV apps will all use the same backend session settings.

---

## Running

```bash
# Start the Expo dev server
npm run start:dev

# Build and install on a connected Android device (development build)
npm run android:dev

# Build a production APK
npm run android:prod
```

For a full native rebuild (required after changes to Kotlin or Gradle files):

```bash
npx expo run:android
```

---

## Sideloading to Firestick / Android TV

```bash
# Enable ADB on the device (Developer Options -> ADB Debugging)
adb connect <device-ip>

# Install the APK
adb install path/to/app.apk
```

---

## Screens

| Screen | Description |
|---|---|
| `HomeScreen` | Trending and featured content |
| `SearchScreen` | Search TMDB for movies and shows |
| `BrowseScreen` | Browse by genre / category |
| `MediaDetailScreen` | Full title info - cast, synopsis, stream selection |
| `EpisodeStreamsScreen` | Episode-specific stream picker for TV series |
| `PlayerScreen` | expo-video player with full playback controls |
| `MpvPlayerScreen` | Native MPV player with track picker and subtitle tools |
| `WatchlistScreen` | User's saved watchlist |
| `TraktCollectionScreen` | Trakt collection browser |
| `AddonsScreen` | Install and manage stream addons |
| `SettingsScreen` | App configuration - playback, subtitles, debrid, language |

---

## Android Native Module

The MPV player is a custom React Native view manager (`MpvPlayerViewManager`) backed by `libmpv-release.aar` in `android/app/libs/`. It exposes:

- Hardware-accelerated video rendering (GPU / OpenGL ES)
- Audio and subtitle track selection
- External subtitle loading (`sub-add`) and delay control (`sub-delay`)
- Seek, speed, volume
- HLS / HTTP / direct file playback

Native Kotlin source:

```text
android/app/src/main/java/com/anonymous/streamdekmobile/
  mpv/
    MPVView.kt              # Core MPV view + event handling
    MpvPlayerViewManager.kt # React Native bridge
    MpvPackage.kt           # Package registration
  torrent/
    ...                     # Local torrent HTTP streaming server
```

---

## Subtitle System

Subtitles are fetched from the OpenSubtitles V3 Stremio addon (`https://opensubtitles-v3.strem.io/`) - no API key required.

- Search results cached in MMKV (30 min TTL)
- Subtitle files cached on disk (up to 50 files, LRU eviction)
- Ranking by language preference -> forced -> HI -> release name similarity
- Auto-load per language configurable in Settings
- In-player delay control: +/-0.1s / +/-0.5s / reset

---

## Android Build Notes

| Setting | Value |
|---|---|
| Package name | `com.anonymous.streamdekmobile` |
| Min SDK | 26 (Android 8.0 Oreo) |
| Target SDK | 35 |
| New Architecture | Enabled (`newArchEnabled=true`) |
| MPV library | `android/app/libs/libmpv-release.aar` |

Cleartext HTTP is enabled in `AndroidManifest.xml` for local network development. Disable it before a public release.

---

## Key Dependencies

| Package | Purpose |
|---|---|
| `expo-video` | Primary video player (with Android patch) |
| `react-native-mmkv` | Fast synchronous key-value storage |
| `expo-file-system` | Subtitle file caching |
| `react-native-google-cast` | Chromecast support |
| `react-native-reanimated` | Gesture-driven animations |
| `patch-package` | Maintains the expo-video Android patch |

